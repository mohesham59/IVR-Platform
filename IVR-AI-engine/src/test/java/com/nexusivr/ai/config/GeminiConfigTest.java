package com.nexusivr.ai.config;

import com.nexusivr.ai.ai.GeminiClient;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiConfigTest {

    private static final Set<String> VALID_GEMINI_MODELS = Set.of(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash-lite"
    );

    @Test
    public void testGeminiModelDefaultValidity() {
        String model = LlmConfig.getGeminiModel();
        assertNotNull(model);
        assertFalse(model.isBlank());
        assertNotEquals("gemini-2.5-flash", model, "gemini-2.5-flash is an invalid/non-existent model name");
        assertTrue(model.startsWith("gemini-"), "Model should start with gemini- prefix");
        assertTrue(VALID_GEMINI_MODELS.contains(model), "Default Gemini model must be a valid catalog model, got: " + model);
    }

    @Test
    public void testGeminiClientInitializationWithValidModel() {
        GeminiClient client = new GeminiClient("test-key", LlmConfig.getGeminiBaseUrl(), LlmConfig.getGeminiModel(), 30, 0.7);
        assertEquals("gemini", client.getProviderName());
        assertEquals("gemini-2.0-flash", client.getModelName());
    }
}
