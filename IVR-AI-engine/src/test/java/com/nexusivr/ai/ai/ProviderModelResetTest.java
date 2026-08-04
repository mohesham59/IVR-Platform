package com.nexusivr.ai.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderModelResetTest {

    private ProviderManager providerManager;

    @BeforeEach
    void setUp() {
        providerManager = new ProviderManager();
        providerManager.clearCache();
    }

    @Test
    void testGroqModelIsInvalidForOpenRouter() {
        // When user switches from Groq to OpenRouter, Groq's model 'llama-3.3-70b-versatile'
        // must NOT be accepted as valid for 'openrouter'.
        assertFalse(ProviderManager.isModelSupportedByProvider("openrouter", "llama-3.3-70b-versatile"),
                "'llama-3.3-70b-versatile' must be invalid for provider 'openrouter'");
    }

    @Test
    void testOpenRouterDefaultModelIsValid() {
        // openai/gpt-oss-20b must be in openrouter's supported model list
        assertTrue(ProviderManager.isModelSupportedByProvider("openrouter", "openai/gpt-oss-20b"),
                "'openai/gpt-oss-20b' must be valid for provider 'openrouter'");
    }

    @Test
    void testSwitchGroqToOpenRouterLlmClientResetsModel() {
        // When caller requests openrouter with Groq's model, getLlmClient must resolve to OpenRouter's default.
        LlmClient client = providerManager.getLlmClient("openrouter", "llama-3.3-70b-versatile", 0.7, 20);
        assertNotNull(client, "LlmClient must not be null for openrouter");
        // The client should resolve to the openrouter default model, not llama-3.3-70b-versatile
        assertNotEquals("llama-3.3-70b-versatile", client.getModelName(),
                "OpenRouter client must not use Groq model 'llama-3.3-70b-versatile'");
    }

    @Test
    void testCircuitBreakerAuthFailureClassification() {
        CircuitBreaker cb = new CircuitBreaker("openrouter");
        cb.recordFailure("HTTP 401: Invalid API Key", 401);
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertTrue(cb.isAuthFailure(), "401 failure must be classified as auth failure");
        assertEquals(CircuitBreaker.ReasonType.AUTH_FAILURE, cb.getOpenReasonType());
        assertTrue(cb.getCooldownRemainingMs() <= 120_000L,
                "OpenRouter circuit breaker cooldown must be capped at <= 120s");
    }
}
