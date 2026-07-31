package com.nexusivr.ai.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OllamaClient Unit Tests")
public class OllamaClientTest {

    @BeforeEach
    void setUp() {
        LlmProviderFactory.setOverrideClient(new MockLlmClient());
    }

    @AfterEach
    void tearDown() {
        LlmProviderFactory.clearCache();
    }

    @Test
    @DisplayName("Should initialize OllamaClient with default configuration")
    void testOllamaClientInitialization() {
        OllamaClient client = new OllamaClient();
        assertEquals("ollama", client.getProviderName());
        assertNotNull(client.getModelName());
        assertTrue(client.isAvailable());
    }

    @Test
    @DisplayName("Should return friendly fallback response when Ollama server is offline")
    void testOfflineOllamaHandling() {
        OllamaClient client = new OllamaClient("http://127.0.0.1:59999", "granite3.2:2b", 2);
        AiResponse response = client.generateResponse("Hello Ollama", List.of());

        assertNotNull(response);
        assertTrue(response.isMock());
        assertTrue(response.getContent().contains("unreachable") || response.getContent().contains("offline"));
    }

    @Test
    @DisplayName("Should return override client when set via LlmProviderFactory")
    void testProviderFactoryOverride() {
        LlmClient client = LlmProviderFactory.createProvider("ollama");
        assertEquals("mock", client.getProviderName());

        client = LlmProviderFactory.createProvider("groq");
        assertEquals("mock", client.getProviderName());
    }
}
