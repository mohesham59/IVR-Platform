package gov.iti.telecom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OllamaAgent {
    private static final String OLLAMA_URL = envOrDefault("OLLAMA_BASE_URL", "http://localhost:11434").replaceAll("/+$", "") + "/api/generate";
    private static final String MODEL_NAME = envOrDefault("OLLAMA_MODEL", "granite4.1:8b");

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static JsonObject chatJson(String systemPrompt, String conversationHistory) throws Exception {
        URL url = new URL(OLLAMA_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            String prompt = systemPrompt + "\n\nConversation History:\n" + conversationHistory + "\nAI:";

            JsonObject jsonRequest = new JsonObject();
            jsonRequest.addProperty("model", MODEL_NAME);
            jsonRequest.addProperty("prompt", prompt);
            jsonRequest.addProperty("stream", false);
            // Force JSON format if Ollama supports it (this works in newer versions)
            jsonRequest.addProperty("format", "json");

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonRequest.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }

            // Read response
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();
                String aiReplyString = jsonResponse.get("response").getAsString().trim();

                // Ensure the reply string is valid JSON since sometimes the LLM puts it in markdown code blocks
                if (aiReplyString.startsWith("```json")) {
                    aiReplyString = aiReplyString.substring(7);
                    if (aiReplyString.endsWith("```")) {
                        aiReplyString = aiReplyString.substring(0, aiReplyString.length() - 3);
                    }
                }

                return JsonParser.parseString(aiReplyString).getAsJsonObject();
            }
        } finally {
            conn.disconnect();
        }
    }
}
