public class TestTTS {
    public static void main(String[] args) throws Exception {
        String text = "أَهْلًا بِكَ فِي الْقَائِمَةِ الرَّئِيسِيَّةِ.";
        String mp3File = "test_java.mp3";
        String langCode = "ar";
        String script = "from gtts import gTTS; tts=gTTS(\"" + text + "\", lang='" + langCode + "'); tts.save(\"" + mp3File + "\")";
        System.out.println("Script: " + script);
        ProcessBuilder pb = new ProcessBuilder("python3", "-c", script);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] output = p.getInputStream().readAllBytes();
        System.out.println("Output: " + new String(output));
        System.out.println("Exit code: " + p.waitFor());
    }
}
