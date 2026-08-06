package com.nexusivr.ai.ai;

import com.nexusivr.ai.service.exception.ProviderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderManagerTest {

    @AfterEach
    void tearDown() {
        ProviderManager pm = new ProviderManager();
        pm.clearCache();
    }

    @Test
    void testPriorityOrder() {
        ProviderManager pm = new ProviderManager();
        assertEquals("groq", pm.getAvailableProviders().get(0));
        assertEquals("openrouter", pm.getAvailableProviders().get(1));
        assertEquals("gemini", pm.getAvailableProviders().get(2));
    }

    @Test
    void testRateLimitSkipsProvider() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        assertFalse(pm.isProviderAvailable("groq"));
        assertTrue(pm.isProviderAvailable("gemini"));
    }

    @Test
    void testSaturatedProviderSkipped() {
        ProviderManager pm = new ProviderManager();
        for (int i = 0; i < 3; i++) {
            pm.markFailed("groq", "some error");
        }
        assertFalse(pm.isProviderAvailable("groq"));
    }

    @Test
    void testCircuitBreakerTripsAfter3FailuresAndSkipsProviderImmediately() {
        ProviderManager pm = new ProviderManager();
        String targetProvider = "ollama";

        assertTrue(pm.isProviderAvailable(targetProvider), "Ollama must be available initially");

        // Simulate 3 consecutive failures for Ollama
        pm.markFailed(targetProvider, "Connection refused");
        pm.markFailed(targetProvider, "Connection refused");
        pm.markFailed(targetProvider, "Connection refused");

        // After 3 failures, circuit breaker MUST be OPEN and provider unavailable
        assertFalse(pm.isProviderAvailable(targetProvider),
                "Ollama must be marked unavailable after 3 consecutive failures");
        assertEquals(CircuitBreaker.State.OPEN, pm.getCircuitState(targetProvider),
                "Circuit breaker state for Ollama must be OPEN after 3 failures");

        // Verify request 4 skips Ollama immediately
        List<String> available = pm.getAvailableProviders();
        assertFalse(available.contains(targetProvider),
                "Available providers list must skip Ollama immediately when circuit is OPEN");
    }

    @Test
    void testSuccessResetsHealth() {
        ProviderManager pm = new ProviderManager();
        pm.markFailed("groq", "error");
        pm.markFailed("groq", "error");
        pm.markSuccess("groq");
        assertTrue(pm.isProviderAvailable("groq"));
    }

    @Test
    void testNextProviderReturnsFirstAvailable() {
        ProviderManager pm = new ProviderManager();
        assertEquals("openrouter", pm.getNextProvider("groq"));
        assertEquals("groq", pm.getNextProvider("openrouter"));
    }

    @Test
    void testTemplateGeneratorFallback() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("gemini");

        LlmClient client = pm.getLlmClient("template-generator", null, 0.7, 30);
        assertTrue(client instanceof TemplateGenerator);
        assertTrue(client.isAvailable());
    }

    @Test
    void testTemplateFallbackResponseIsFlagged() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("gemini");

        LlmClient client = pm.getLlmClient("template-generator", null, 0.7, 30);
        assertTrue(client instanceof TemplateGenerator);

        TemplateGenerator templateGen = (TemplateGenerator) client;
        com.nexusivr.ai.ai.AiResponse response = templateGen.generateStructuredResponse(
                "system", "user prompt", List.of(), "restaurant"
        );
        assertTrue(response.isTemplateFallback(),
                "TemplateGenerator fallback response must be flagged as templateFallback=true");
        assertFalse(response.isMock(),
                "TemplateGenerator fallback response must NOT be flagged as mock=true — it is a real fallback, not a test mock");
        String content = response.getContent().toLowerCase();
        assertTrue(content.contains("order") || content.contains("reservation") || content.contains("hostess"),
                "Template fallback content should reflect requested domain 'restaurant'. Content: " + response.getContent());
    }

    @Test
    void testAllProvidersExhaustedReturnsTemplateFallback() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("openrouter");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        AiResponse response = pm.executeWithRetryAndFallback(
                "gemini", "gemini-2.0-flash", 0.7, 30,
                "system", "Create a banking IVR", "GENERATE_FLOW", List.of(), "banking"
        );

        assertNotNull(response);
        assertTrue(response.isTemplateFallback());
        assertEquals("template-generator", response.getActualProviderUsed());
    }

    @Test
    void testTemplateFallbackAttributesRequestedProvider() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("openrouter");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        AiResponse response = pm.executeWithRetryAndFallback(
                "gemini", "gemini-2.0-flash", 0.7, 30,
                "system", "Create a banking IVR", "GENERATE_FLOW", List.of(), "banking"
        );

        assertNotNull(response);
        assertEquals("gemini", response.getSelectedProvider());
        assertEquals("template-generator", response.getActualProviderUsed());
    }

    @Test
    void testMultiProviderAttemptsTrackingWhenAllProvidersFail() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("openrouter");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        AiResponse response = pm.executeWithRetryAndFallback(
                "groq", "llama-3.3-70b-versatile", 0.7, 30,
                "system", "Create a banking IVR", "GENERATE_FLOW", List.of(), "banking"
        );

        assertNotNull(response);
        assertEquals("template-generator", response.getActualProviderUsed());
        assertNotNull(response.getProviderAttempts());
        assertFalse(response.getProviderAttempts().isEmpty());

        assertEquals(4, response.getProviderAttempts().size());

        com.nexusivr.ai.dto.common.ProviderAttemptDto groqAttempt = response.getProviderAttempts().get(0);
        assertEquals("groq", groqAttempt.getProvider());
        assertEquals(429, groqAttempt.getStatus());
        assertTrue(groqAttempt.getReason().contains("Quota exceeded"));
        assertTrue(groqAttempt.getCooldownSeconds() > 0);
    }

    @Test
    void testQuotaExceededSetsLongCooldownAndMaximumCooldownSeconds() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        int maxCooldownSecs = pm.getMaximumCooldownSeconds();
        assertTrue(maxCooldownSecs >= 290 && maxCooldownSecs <= 300,
                "Maximum remaining cooldown for fresh 429 quota failures must be ~300s, but was " + maxCooldownSecs);

        int groqCooldownSecs = pm.getCooldownSeconds("groq");
        assertTrue(groqCooldownSecs >= 290 && groqCooldownSecs <= 300,
                "Groq remaining cooldown for 429 quota failure must be ~300s, but was " + groqCooldownSecs);
    }

    @Test
    void testDifferentErrorTypesYieldDifferentCooldowns() {
        ProviderManager pm = new ProviderManager();
        // 3 consecutive server failures (500) trip circuit breaker to OPEN with ~30s cooldown
        pm.markFailed("ollama", "HTTP 500 Internal Server Error");
        pm.markFailed("ollama", "HTTP 500 Internal Server Error");
        pm.markFailed("ollama", "HTTP 500 Internal Server Error");

        // Quota failure (429) sets ~300s cooldown
        pm.markRateLimited("groq");

        int ollamaCooldown = pm.getCooldownSeconds("ollama");
        int groqCooldown = pm.getCooldownSeconds("groq");
        int maxCooldown = pm.getMaximumCooldownSeconds();

        assertTrue(ollamaCooldown >= 1 && ollamaCooldown <= 60,
                "Server failure cooldown should be short (~30s), but was " + ollamaCooldown);
        assertTrue(groqCooldown >= 290 && groqCooldown <= 300,
                "Quota failure cooldown should be ~300s, but was " + groqCooldown);
        assertEquals(groqCooldown, maxCooldown,
                "Maximum remaining cooldown should match the longest active cooldown (groq ~300s)");
    }

    @Test
    void testExhaustedQuotaProvidersThrowProviderExceptionWithQuotaReason() {
        ProviderManager pm = new ProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("openrouter");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        // Vague description "design IVR" causes TemplateGenerator fallback to fail, triggering ProviderException
        ProviderException ex = assertThrows(ProviderException.class, () ->
                pm.executeWithRetryAndFallback(
                        "gemini", "gemini-2.0-flash", 0.7, 30,
                        "system instruction", "design IVR", "GENERATE_FLOW", List.of(), "generic",
                        true
                )
        );

        assertEquals(ProviderException.FailureReason.QUOTA_EXCEEDED, ex.getReason(),
                "FailureReason must be QUOTA_EXCEEDED when all providers failed due to 429 quota exhaustion");
    }
}
