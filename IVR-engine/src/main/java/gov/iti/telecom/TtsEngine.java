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

    public static void sayText(AgiChannel channel, String text) throws IOException, AgiException {
        String streamPath = getOrSynthesizeAudio(text);
        if (streamPath != null) {
            System.out.println("[TtsEngine] Streaming synthesized audio to caller: " + streamPath);
            channel.streamFile(streamPath);
        }
    }

    public static String getOrSynthesizeAudio(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            Files.createDirectories(SOUNDS_DIRECTORY);
            String uniqueName = "tts-" + Math.abs(text.hashCode());
            Path targetWavFile = SOUNDS_DIRECTORY.resolve(uniqueName + ".wav");

            if (!Files.exists(targetWavFile)) {
                generateSpeech(text, targetWavFile);
            }

            return "ivr-tts/" + uniqueName;
        } catch (Exception e) {
            System.err.println("[TtsEngine] Error synthesizing speech for '" + text + "': " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void generateSpeech(String text, Path targetWavFile) throws IOException, InterruptedException {
        String baseName = targetWavFile.toString().replace(".wav", "");
        String mp3File = baseName + ".mp3";
        String wavFile = baseName + ".wav";
        String gsmFile = baseName + ".gsm";
        String ulawFile = baseName + ".ulaw";
        String slnFile = baseName + ".sln";

        ProcessBuilder pbTts = new ProcessBuilder(
            "python3", "-c",
            "from gtts import gTTS; tts=gTTS(" + quote(text) + "); tts.save(" + quote(mp3File) + ")"
        );
        Process pTts = pbTts.start();
        int exitTts = pTts.waitFor();

        if (exitTts == 0 && Files.exists(Paths.get(mp3File))) {
            new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "pcm_s16le", wavFile).start().waitFor();
            new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "libgsm", gsmFile).start().waitFor();
            new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-codec:a", "pcm_mulaw", ulawFile).start().waitFor();
            new ProcessBuilder("ffmpeg", "-y", "-i", mp3File, "-ar", "8000", "-ac", "1", "-f", "s16le", slnFile).start().waitFor();

            new java.io.File(wavFile).setReadable(true, false);
            new java.io.File(gsmFile).setReadable(true, false);
            new java.io.File(ulawFile).setReadable(true, false);
            new java.io.File(slnFile).setReadable(true, false);

            Files.deleteIfExists(Paths.get(mp3File));
        } else {
            throw new IOException("Speech synthesis failed for text: " + text);
        }
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}