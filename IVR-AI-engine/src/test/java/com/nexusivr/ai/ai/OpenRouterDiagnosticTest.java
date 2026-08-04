package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.LlmConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Manual diagnostic test — requires live ITI Gateway network access and valid API key")
public class OpenRouterDiagnosticTest {

    @Test
    void testOpenRouterHttp11Diagnostic() {
        String apiKey = LlmConfig.getOpenRouterApiKey();
        String baseUrl = LlmConfig.getOpenRouterBaseUrl();
        String model = LlmConfig.getOpenRouterModel();
        int timeout = LlmConfig.getOpenRouterTimeout();

        System.out.println("--- OpenRouter HTTP/1.1 Diagnostic Test ---");
        System.out.println("API Key present: " + (!apiKey.isBlank()));
        System.out.println("Base URL: " + baseUrl);
        System.out.println("Model: " + model);
        System.out.println("Timeout: " + timeout + "s");

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openrouter", apiKey, baseUrl, model, timeout, 0.7
        );

        // Test 1: Standard text response
        AiResponse response1 = client.generateResponse("Hello, please reply with a 1-word confirmation.", null);
        System.out.println("[Test 1 Freeform] Is Mock/Failure: " + response1.isMock());
        System.out.println("[Test 1 Freeform] Status Code: " + response1.getStatusCode());
        System.out.println("[Test 1 Freeform] Response Content: " + response1.getContent());
        assertFalse(response1.isMock(), "Response 1 should not be mock/failure");
        assertNotNull(response1.getContent());

        // Test 2: Structured JSON response
        AiResponse response2 = client.generateStructuredResponse(
                "You are an IVR system assistant. Respond in JSON with key 'greeting'.",
                "Generate a short welcome message.",
                null
        );
        System.out.println("[Test 2 Structured] Is Mock/Failure: " + response2.isMock());
        System.out.println("[Test 2 Structured] Status Code: " + response2.getStatusCode());
        System.out.println("[Test 2 Structured] Response Content: " + response2.getContent());
        assertFalse(response2.isMock(), "Response 2 should not be mock/failure");
        assertNotNull(response2.getContent());

        System.out.println("--------------------------------------------");
    }
}
