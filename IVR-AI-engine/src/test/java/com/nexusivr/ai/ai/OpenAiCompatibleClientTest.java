package com.nexusivr.ai.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.model.Message;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleClientTest {

    private HttpServer server;
    private int port;
    private JsonObject lastReceivedRequest;
    private String lastReceivedPath;
    private String lastAuthorizationHeader;
    private String lastRefererHeader;
    private String lastTitleHeader;
    private String responseToReturn;
    private int statusCodeToReturn;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                lastReceivedPath = exchange.getRequestURI().getPath();
                lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
                lastRefererHeader = exchange.getRequestHeaders().getFirst("HTTP-Referer");
                lastTitleHeader = exchange.getRequestHeaders().getFirst("X-Title");

                byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                String requestBodyStr = new String(requestBytes, StandardCharsets.UTF_8);
                if (!requestBodyStr.isBlank()) {
                    lastReceivedRequest = JsonParser.parseString(requestBodyStr).getAsJsonObject();
                }

                byte[] responseBytes = responseToReturn.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCodeToReturn, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        server.start();
        statusCodeToReturn = 200;
        responseToReturn = "{}";
        lastReceivedRequest = null;
        lastReceivedPath = null;
        lastAuthorizationHeader = null;
        lastRefererHeader = null;
        lastTitleHeader = null;
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testStandardOpenRouterRequest() {
        String baseUrl = "http://localhost:" + port + "/api/v1";
        responseToReturn = "{\n" +
                "  \"choices\": [{\n" +
                "    \"message\": {\n" +
                "      \"content\": \"Hello standard\"\n" +
                "    }\n" +
                "  }],\n" +
                "  \"usage\": {\n" +
                "    \"prompt_tokens\": 10,\n" +
                "    \"completion_tokens\": 20\n" +
                "  }\n" +
                "}";

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openrouter", "test-key", baseUrl, "llama-3.3-70b-versatile", 5, 0.7
        );

        AiResponse response = client.generateResponse("hi", new ArrayList<>());

        assertNotNull(response);
        assertFalse(response.isMock());
        assertEquals("Hello standard", response.getContent());
        assertEquals("meta-llama/llama-3.3-70b-instruct", response.getModel());

        assertEquals("/api/v1/chat/completions", lastReceivedPath);
        assertEquals("Bearer test-key", lastAuthorizationHeader);
        assertEquals("https://nexusivr.com", lastRefererHeader);
        assertEquals("NexusIVR", lastTitleHeader);

        assertNotNull(lastReceivedRequest);
        assertEquals("meta-llama/llama-3.3-70b-instruct", lastReceivedRequest.get("model").getAsString());
    }

    @Test
    void testStudentProxyRequest() {
        String baseUrl = "http://localhost:" + port + "/api/v1/student/chat";
        responseToReturn = "{\n" +
                "  \"output_text\": \"Hello student\",\n" +
                "  \"usage\": {\n" +
                "    \"input_tokens\": 15,\n" +
                "    \"output_tokens\": 25\n" +
                "  }\n" +
                "}";

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openrouter", "test-key", baseUrl, "llama-3.3-70b-versatile", 5, 0.7
        );

        AiResponse response = client.generateResponse("hi", new ArrayList<>());

        assertNotNull(response);
        assertFalse(response.isMock());
        assertEquals("Hello student", response.getContent());
        assertEquals(com.nexusivr.ai.config.LlmConfig.getOpenrouterModel(), response.getModel());

        assertEquals("/api/v1/student/chat", lastReceivedPath);
        assertEquals("Bearer test-key", lastAuthorizationHeader);
        assertNull(lastRefererHeader);
        assertNull(lastTitleHeader);

        assertNotNull(lastReceivedRequest);
        assertEquals(com.nexusivr.ai.config.LlmConfig.getOpenrouterModel(), lastReceivedRequest.get("model_id").getAsString());
        assertFalse(lastReceivedRequest.has("model"));
        assertFalse(lastReceivedRequest.has("response_format"));
    }

    @Test
    void testLiveRequest() {
        String apiKey = com.nexusivr.ai.config.LlmConfig.getOpenrouterApiKey();
        String baseUrl = com.nexusivr.ai.config.LlmConfig.getOpenrouterBaseUrl();
        String model = com.nexusivr.ai.config.LlmConfig.getOpenrouterModel();

        System.out.println("LIVE CONFIG: URL=" + baseUrl + ", Model=" + model + ", KeyPresent=" + (!apiKey.isBlank()));

        if (apiKey.isBlank()) {
            System.out.println("Skipping live test because OPENROUTER_API_KEY is not configured.");
            return;
        }

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openrouter", apiKey, baseUrl, model, 30, 0.7
        );

        String[] prompts = {
                "تصميم نظام حجز طبي بالعامية المصرية لعيادة أسنان",
                "تحويل مكالمات عملاء البنك للاستعلام عن الرصيد وحساباتهم",
                "استعلامات حجز الغرف الفندقية وخدمة الغرف"
        };

        for (int i = 0; i < prompts.length; i++) {
            System.out.println("--- LIVE REQUEST " + (i + 1) + " ---");
            AiResponse response = client.generateResponse(null, prompts[i], new ArrayList<>());
            System.out.println("Status      : " + response.getStatusCode());
            System.out.println("Mock/Error  : " + response.isMock());
            System.out.println("Content     : " + response.getContent());
            System.out.println("Prompt tokens: " + response.getPromptTokens());
            System.out.println("Completion tokens: " + response.getCompletionTokens());

            assertFalse(response.isMock());
        }
    }

    @Test
    void testHttpClientEnforcesHttp11() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openrouter", "key", "http://localhost", "model", 30, 0.7
        );
        java.net.http.HttpClient httpClient = client.getHttpClient();
        assertNotNull(httpClient);
        assertEquals(java.net.http.HttpClient.Version.HTTP_1_1, httpClient.version());
    }
}
