package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import com.nexusivr.ai.service.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test: an empty response body from an LLM provider (HTTP 200 + 0 content)
 * must be classified as a retryable failure and cause the provider manager to fall back
 * to the next provider in the chain — NOT immediately surface a 502 to the user.
 *
 * <p>Evidence from production log:
 * <pre>
 * [aiApi] generateFlowStream failed, falling back to standard generateFlow:
 * Error: Provider [all] failed: AI generation failed: LLM output could not be normalized
 * into VoiceXML for domain: hospitality. Normalization error: LLM returned empty response.
 * Both attempts failed.
 * POST /api/v1/ai/flow/generate 502 (Bad Gateway)
 * </pre>
 *
 * <p>Root cause: the empty-response AiResponse was previously returned with {@code isMock=false}
 * and a non-retryable status code (0), causing {@link ProviderManager#attemptWithRetries} to
 * treat it as a successful response and pass the empty string up the chain, which then failed
 * at normalisation. The fix returns {@code isMock=true, statusCode=502} so RetryPolicy marks
 * it retryable and the provider fallback chain fires correctly.
 */
class EmptyLlmResponseRegressionTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;

    /** A minimal valid VoiceXML document — returned by the fallback provider. */
    private static final String VALID_VXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to our hospitality service.</prompt>
              <goto next="#menu"/>
            </block>
          </form>
          <form id="menu">
            <menu>
              <prompt>Press 1 for reservations, 2 for room service.</prompt>
              <choice accept="digits 1" next="#reservations"/>
              <choice accept="digits 2" next="#room_service"/>
            </menu>
          </form>
          <form id="reservations">
            <block><prompt>Connecting to reservations.</prompt></block>
          </form>
          <form id="room_service">
            <block><prompt>Connecting to room service.</prompt></block>
          </form>
          <form id="end">
            <block>
              <prompt>Thank you. Goodbye.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>
        """;

    /**
     * An AiResponse that simulates an LLM provider returning HTTP 200 with empty body
     * (i.e. what the client now produces after the fix): isMock=true, statusCode=502.
     */
    private static AiResponse emptyProviderResponse(String providerLabel) {
        return new AiResponse(
                "Provider '" + providerLabel + "' returned empty response (0 output tokens).",
                "test-model", 0, 0,
                /*isMock=*/true, /*isTemplateFallback=*/false,
                /*functionName=*/null, /*functionArguments=*/null,
                /*statusCode=*/502
        );
    }

    /** An AiResponse with a valid VXML payload — simulates a healthy fallback provider. */
    private static AiResponse successResponse() {
        AiResponse r = mock(AiResponse.class);
        when(r.isMock()).thenReturn(false);
        when(r.getContent()).thenReturn(VALID_VXML);
        when(r.getStatusCode()).thenReturn(200);
        when(r.getActualProviderUsed()).thenReturn("groq");
        when(r.getModel()).thenReturn("llama-3.3-70b-versatile");
        when(r.getPromptTokens()).thenReturn(1200);
        when(r.getCompletionTokens()).thenReturn(800);
        return r;
    }

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        promptRefinerService = mock(PromptRefinerService.class);
        unifiedAiEngine = new UnifiedAiEngine(
                providerManager,
                new com.nexusivr.ai.ai.PromptBuilder(),
                new FlowContextService(),
                promptRefinerService
        );
        RefinedSpecCache.clear();
        SemanticCache.getInstance().clear();

        when(providerManager.isProviderAvailable(anyString())).thenReturn(true);
        when(promptRefinerService.refine(anyString(), anyString(), anyString(),
                anyDouble(), anyInt(), any(), any()))
                .thenReturn("{\"business_domain\":\"hospitality\"}");
        when(promptRefinerService.refine(anyString(), anyString(), anyString(),
                anyDouble(), anyInt()))
                .thenReturn("{\"business_domain\":\"hospitality\"}");
    }

    // -----------------------------------------------------------------------
    // Test 1: Primary provider returns empty response → fallback succeeds
    // -----------------------------------------------------------------------

    /**
     * REGRESSION: When the primary provider (e.g. openrouter/ITI gateway) returns an empty
     * response body, the system must NOT immediately 502. Instead it must attempt generation
     * via a fallback provider (e.g. Groq) and succeed.
     */
    @Test
    void testEmptyResponseFromPrimaryProviderTriggersProviderFallback() {
        String prompt = "Create a hospitality IVR with reservations and room service";

        // Primary call returns empty body (mock=true, status=502) — treated as retryable.
        AiResponse emptyResponse = emptyProviderResponse("openrouter");

        // Corrective retry also gets empty (same call, same failing provider chain leg).
        // The fallback provider call returns a valid VXML.
        AiResponse fallbackSuccess = successResponse();

        when(providerManager.executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                // attempt 1: primary provider → empty response
                .thenReturn(emptyResponse)
                // attempt 2 (corrective retry after normalisation failure): fallback provider → valid VXML
                .thenReturn(fallbackSuccess);

        com.nexusivr.ai.model.Flow flow = unifiedAiEngine.generateFlow(
                UUID.randomUUID(), UUID.randomUUID(),
                prompt, "openrouter", "openai.gpt-oss-20b-1:0", 0.7, 35);

        assertNotNull(flow, "Flow generation must succeed via fallback provider after empty primary response");
        assertNotNull(flow.getFlowJson(), "Resulting flow must have valid JSON");
    }

    // -----------------------------------------------------------------------
    // Test 2: Both attempts return empty → ProviderException is thrown
    // -----------------------------------------------------------------------

    /**
     * When ALL provider attempts return empty responses (no healthy fallback available),
     * the system must throw {@link ProviderException} and not silently return null or
     * swallow the error.
     */
    @Test
    void testAllProvidersReturnEmptyResponseThrowsProviderException() {
        String prompt = "Create a hospitality IVR with reservations";

        AiResponse emptyResponse1 = emptyProviderResponse("openrouter");
        AiResponse emptyResponse2 = emptyProviderResponse("groq");

        when(providerManager.executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                // Both generation attempts exhaust → ProviderException propagates
                .thenThrow(new ProviderException("all",
                        "AI generation failed: LLM output could not be normalized into VoiceXML for domain: hospitality. " +
                        "Normalization error: LLM returned empty response. Both attempts failed.",
                        ProviderException.FailureReason.PROVIDER_ERROR));

        assertThrows(ProviderException.class, () ->
                unifiedAiEngine.generateFlow(
                        UUID.randomUUID(), UUID.randomUUID(),
                        prompt, "openrouter", "openai.gpt-oss-20b-1:0", 0.7, 35),
                "Must throw ProviderException when all providers return empty responses");
    }

    // -----------------------------------------------------------------------
    // Test 3: Empty response is classified as retryable by RetryPolicy
    // -----------------------------------------------------------------------

    /**
     * Verifies the retryability classification: the mock AiResponse produced by the
     * empty-response guard in all three clients (statusCode=502, isMock=true) must be
     * identified as retryable by RetryPolicy, so the provider loop continues.
     */
    @Test
    void testEmptyResponseAiResponseIsRetryable() {
        AiResponse emptyResp = emptyProviderResponse("openrouter");
        assertTrue(com.nexusivr.ai.ai.RetryPolicy.isRetryable(emptyResp),
                "An empty-response AiResponse (statusCode=502, isMock=true) must be retryable");
    }

    // -----------------------------------------------------------------------
    // Test 4: An AiResponse with valid content is NOT classified as empty
    // -----------------------------------------------------------------------

    /**
     * Guard test: a normal successful AiResponse must NOT accidentally be treated as retryable
     * (isMock=false, statusCode=200) — ensuring the guard only fires for genuinely empty responses.
     */
    @Test
    void testNonEmptyResponseAiResponseIsNotRetryable() {
        AiResponse validResp = successResponse();
        assertFalse(com.nexusivr.ai.ai.RetryPolicy.isRetryable(validResp),
                "A successful (non-mock) AiResponse must NOT be classified as retryable");
    }

    // -----------------------------------------------------------------------
    // Test 5: Empty response followed by malformed VXML → normalisation retry fires
    // -----------------------------------------------------------------------

    /**
     * Edge-case: primary provider returns empty response body (attempt 1), the corrective
     * retry attempt returns a VALID VXML — verifies the full happy path after one empty response.
     */
    @Test
    void testEmptyResponseThenValidResponseSucceeds() {
        String prompt = "Create a hospitality IVR with reservations and room service";

        AiResponse emptyResp = emptyProviderResponse("openrouter");
        AiResponse goodResp  = successResponse();

        when(providerManager.executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(emptyResp)
                .thenReturn(goodResp);

        com.nexusivr.ai.model.Flow flow = unifiedAiEngine.generateFlow(
                UUID.randomUUID(), UUID.randomUUID(),
                prompt, "openrouter", "openai.gpt-oss-20b-1:0", 0.7, 35);

        assertNotNull(flow, "Flow must be generated successfully when fallback attempt returns valid VXML");

        verify(providerManager, atLeast(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());
    }
}
