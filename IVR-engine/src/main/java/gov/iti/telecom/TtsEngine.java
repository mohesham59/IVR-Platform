package gov.iti.telecom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;

/**
 * TtsEngine — Text-To-Speech engine for Asterisk IVR platform.
 * Synthesizes natural speech audio files in WAV, GSM, ULAW, and SLN formats for Asterisk playback.
 */
public class TtsEngine {

    private static final Path SOUNDS_DIRECTORY = Paths.get("/var/lib/asterisk/sounds/ivr-tts");
    private static final Path CUSTOM_AUDIO_DIRECTORY = Paths.get("/var/lib/asterisk/sounds/ivr-custom");

    public static void sayText(AgiChannel channel, String text, String lang) throws IOException, AgiException {
        String streamPath = getOrSynthesizeAudio(text, lang);
        if (streamPath != null) {
            System.out.println("[TtsEngine] Streaming synthesized audio to caller: " + streamPath);
            channel.streamFile(streamPath);
        }
    }

    public static String getOrSynthesizeAudio(String text, String lang) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            Files.createDirectories(SOUNDS_DIRECTORY);
            String langCode = (lang != null && !lang.trim().isEmpty()) ? lang : "en";
            String uniqueName = "tts-" + langCode + "-" + contentHash(text);
            Path targetWavFile = SOUNDS_DIRECTORY.resolve(uniqueName + ".wav");

            if (!Files.exists(targetWavFile)) {
                generateSpeech(text, langCode, targetWavFile);
            }

            return "ivr-tts/" + uniqueName;
        } catch (Exception e) {
            System.err.println("[TtsEngine] Error synthesizing speech for '" + text + "': " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves a custom audio file src to an Asterisk stream path.
     * Audio files should be placed in /var/lib/asterisk/sounds/ivr-custom/.
     *
     * <p>Usage in VXML:</p>
     * <pre>{@code
     * <audio src="welcome_ar.wav">Fallback TTS text if file not found</audio>
     * }</pre>
     *
     * @param src the audio file name from the VXML <audio> tag (e.g., "welcome_ar.wav")
     * @return Asterisk stream path (e.g., "ivr-custom/welcome_ar"), or null if file not found
     */
    public static String resolveAudioSrc(String src) {
        if (src == null || src.trim().isEmpty()) {
            return null;
        }

        try {
            Files.createDirectories(CUSTOM_AUDIO_DIRECTORY);
        } catch (IOException e) {
            System.err.println("[TtsEngine] Could not create custom audio directory: " + e.getMessage());
        }

        // Strip extension to get base name
        String baseName = src;
        int dotIndex = src.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = src.substring(0, dotIndex);
        }

        // Absolute path (e.g. a recorded voicemail under /dev/shm) - stream it directly.
        String[] extensions = {".wav", ".gsm", ".ulaw", ".sln", ".sln16", ".mp3", ".alaw"};
        if (baseName.startsWith("/")) {
            for (String ext : extensions) {
                Path audioFile = Paths.get(baseName + ext);
                if (Files.exists(audioFile)) {
                    System.out.println("[TtsEngine] Found absolute audio: " + audioFile);
                    return baseName;
                }
            }
            System.out.println("[TtsEngine] Absolute audio not found for: " + src);
            return null;
        }

        // Check if audio file exists in any common Asterisk format
        for (String ext : extensions) {
            Path audioFile = CUSTOM_AUDIO_DIRECTORY.resolve(baseName + ext);
            if (Files.exists(audioFile)) {
                System.out.println("[TtsEngine] Found custom audio: " + audioFile);
                return "ivr-custom/" + baseName;
            }
        }

        System.out.println("[TtsEngine] Custom audio not found for: " + src + " (looked in " + CUSTOM_AUDIO_DIRECTORY + ")");
        return null;
    }

    private static void generateSpeech(String text, String langCode, Path targetWavFile) throws IOException, InterruptedException {
        String baseName = targetWavFile.toString().replace(".wav", "");
        String mp3File = baseName + ".mp3";
        String wavFile = baseName + ".wav";
        String gsmFile = baseName + ".gsm";
        String ulawFile = baseName + ".ulaw";
        String slnFile = baseName + ".sln";

        // Text and language are passed as process arguments (no shell/string interpolation).
        ProcessBuilder pbTts = new ProcessBuilder(
            "python3", "-c",
            "import sys; from gtts import gTTS; tts=gTTS(sys.argv[1], lang=sys.argv[2]); tts.save(sys.argv[3])",
            text, langCode, mp3File
        );
        int exitTts = runAndWait(pbTts);

        if (exitTts == 0 && Files.exists(Paths.get(mp3File))) {
            runAndWait(new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "pcm_s16le", wavFile));
            runAndWait(new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "libgsm", gsmFile));
            runAndWait(new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "pcm_mulaw", ulawFile));
            runAndWait(new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-f", "s16le", slnFile));

            new java.io.File(wavFile).setReadable(true, false);
            new java.io.File(gsmFile).setReadable(true, false);
            new java.io.File(ulawFile).setReadable(true, false);
            new java.io.File(slnFile).setReadable(true, false);

            Files.deleteIfExists(Paths.get(mp3File));
        } else {
            boolean fileExists = Files.exists(Paths.get(mp3File));
            throw new IOException("Speech synthesis failed for text: " + text + ". exitTts=" + exitTts + ", fileExists=" + fileExists + ", mp3File=" + mp3File);
        }
    }

    /**
     * Runs a process, draining stdout/stderr concurrently (avoids pipe-buffer
     * deadlocks) and enforcing a timeout so a stuck process cannot hang a call.
     */
    private static int runAndWait(ProcessBuilder pb) throws IOException, InterruptedException {
        Process p = pb.start();
        Thread outReader = new Thread(() -> consume(p.getInputStream()), "tts-stdout");
        Thread errReader = new Thread(() -> consume(p.getErrorStream()), "tts-stderr");
        outReader.setDaemon(true);
        errReader.setDaemon(true);
        outReader.start();
        errReader.start();

        boolean finished = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            System.err.println("[TtsEngine] Process timed out: " + String.join(" ", pb.command()));
            p.destroyForcibly();
            throw new IOException("Process timed out: " + pb.command().get(0));
        }
        return p.exitValue();
    }

    private static void consume(java.io.InputStream stream) {
        try (java.util.Scanner s = new java.util.Scanner(stream).useDelimiter("\\A")) {
            if (s.hasNext()) {
                System.out.println("[TtsEngine Subprocess] " + s.next());
            }
        } catch (Exception ignored) {
        }
    }

    private static String contentHash(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}