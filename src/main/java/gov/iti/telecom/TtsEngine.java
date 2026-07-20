package gov.iti.telecom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;

public class TtsEngine {

    private static final Path SOUNDS_DIRECTORY = Paths.get("/var/lib/asterisk/sounds/custom");

    public static void sayText(AgiChannel channel, String text) throws IOException, AgiException {

        Path audioFile = null;

        try {

            // Generate temporary WAV file
            audioFile = generateSpeech(text);

            // Get filename without .wav
            String fileName = audioFile
                    .getFileName()
                    .toString()
                    .replaceFirst("\\.wav$", "");

            channel.streamFile(
                    "custom/" + fileName);

        } catch (Exception e) {

            System.err.println(
                    "[TtsEngine] Error: "
                            + e.getMessage());

            throw new RuntimeException(e);

        } finally {

            // Delete temporary audio file
            if (audioFile != null) {
                Files.deleteIfExists(audioFile);
            }
        }
    }

    private static Path generateSpeech(
            String text) throws IOException {

        Files.createDirectories(
                SOUNDS_DIRECTORY);

        Path audioFile = Files.createTempFile(
                SOUNDS_DIRECTORY,
                "tts-",
                ".wav");

        SynthesisInput input = SynthesisInput.newBuilder()
                .setText(text)
                .build();

        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("en-US")
                .build();

        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(
                        AudioEncoding.LINEAR16)
                .setSampleRateHertz(8000)
                .build();

        try (
                TextToSpeechClient client = TextToSpeechClient.create()) {

            SynthesizeSpeechResponse response = client.synthesizeSpeech(
                    input,
                    voice,
                    audioConfig);

            ByteString audioContent = response.getAudioContent();

            Files.write(
                    audioFile,
                    audioContent.toByteArray());
        }

        return audioFile;
    }
}