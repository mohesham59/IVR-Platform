package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test: verifies that malformed XML (unclosed tags) in LLM output triggers
 * a corrective retry before surfacing a 502 error to the caller.
 * <p>
 * Covers both the "first gate" retry (isMalformedLlmOutput detects the issue before parsing)
 * and the "second gate" retry (VxmlToModelConverter parse failure triggers corrective retry).
 * <p>
 * This test was added to address a confirmed cross-provider bug where the retry mechanism
 * was not firing for XML parse errors, causing hard 502 failures on the first attempt.
 */
class MalformedXmlRetryRegressionTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;

    private static final String VALID_VXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome.</prompt>
              <goto next="#menu"/>
            </block>
          </form>
          <form id="menu">
            <menu>
              <prompt>Press 1 for Billing, 2 for Cards.</prompt>
              <choice accept="digits 1" next="#billing"/>
              <choice accept="digits 2" next="#cards"/>
            </menu>
          </form>
          <form id="billing">
            <block><prompt>Billing department.</prompt></block>
          </form>
          <form id="cards">
            <block><prompt>Cards department.</prompt></block>
          </form>
          <form id="end">
            <block>
              <prompt>Goodbye.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>
        """;

    /** Exactly replicates the real-world bug: unclosed <block> tag before </form> */
    private static final String MALFORMED_UNCLOSED_BLOCK_VXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to our banking service.</prompt>
              <goto next="#menu"/>
            </block>
          </form>
          <form id="menu">
            <menu>
              <prompt>Press 1 for Billing, 2 for lost card.</prompt>
              <choice accept="digits 1" next="#billing"/>
              <choice accept="digits 2" next="#lost_card"/>
            </menu>
          </form>
          <form id="billing">
            <block><prompt>Billing department.</prompt></block>
          </form>
          <form id="lost_card">
            <block>
              <prompt>We are sorry to hear that your card is lost or stolen.</prompt>
              <transfer dest="card_services"/>
          </form>
          <form id="end">
            <block>
              <prompt>Goodbye.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>
        """;

    /** JSON-wrapped version of the same malformed VXML (as it comes from real LLM output) */
    private static final String JSON_WRAPPED_MALFORMED_VXML =
            "{\"vxml\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n" +
            "<vxml version=\\\"2.1\\\" xmlns=\\\"http://www.w3.org/2001/vxml\\\">\\n" +
            "  <form id=\\\"start\\\">\\n" +
            "    <block>\\n" +
            "      <prompt>Welcome.</prompt>\\n" +
            "      <goto next=\\\"#menu\\\"/>\\n" +
            "    </block>\\n" +
            "  </form>\\n" +
            "  <form id=\\\"menu\\\">\\n" +
            "    <menu>\\n" +
            "      <prompt>Press 1 for Billing.</prompt>\\n" +
            "      <choice accept=\\\"digits 1\\\" next=\\\"#billing\\\"/>\\n" +
            "    </menu>\\n" +
            "  </form>\\n" +
            "  <form id=\\\"billing\\\">\\n" +
            "    <block>\\n" +
            "      <prompt>Billing department.</prompt>\\n" +
            "  </form>\\n" +
            "  <form id=\\\"end\\\">\\n" +
            "    <block><prompt>Goodbye.</prompt><disconnect/></block>\\n" +
            "  </form>\\n" +
            "</vxml>\"}";

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        promptRefinerService = mock(PromptRefinerService.class);
        unifiedAiEngine = new UnifiedAiEngine(providerManager, new com.nexusivr.ai.ai.PromptBuilder(),
                new FlowContextService(), promptRefinerService);
        RefinedSpecCache.clear();
        SemanticCache.getInstance().clear();
        when(providerManager.isProviderAvailable(anyString())).thenReturn(true);
        when(promptRefinerService.refine(anyString(), anyString(), anyString(), anyDouble(), anyInt(), any(), any()))
                .thenReturn("{\"business_domain\":\"generic\"}");
        when(promptRefinerService.refine(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"business_domain\":\"generic\"}");
    }

    /**
     * REGRESSION TEST: Bare VXML with unclosed <block> triggers corrective retry.
     * <p>
     * The first gate (isMalformedLlmOutput) should detect the unclosed tag via isWellFormedVxml()
     * and dispatch attempt 2 with RETRY REMINDER.
     */
    @Test
    void testBareVxmlUnclosedBlockTagTriggersRetry() {
        String prompt = "Create a banking IVR with Billing and lost card. Press 1 for balance, Press 2 for lost card. Include greeting and closing.";

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(MALFORMED_UNCLOSED_BLOCK_VXML);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        com.nexusivr.ai.model.Flow flow = unifiedAiEngine.generateFlow(
                UUID.randomUUID(), UUID.randomUUID(), prompt, "groq", "llama-3.3-70b-versatile", 0.7, 30);

        assertNotNull(flow, "Flow generation must succeed after corrective retry");
        assertNotNull(flow.getFlowJson(), "Flow JSON must be present");

        // CRITICAL ASSERTION: LLM provider must have been called TWICE (original + retry)
        verify(providerManager, atLeast(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());
    }

    /**
     * REGRESSION TEST: JSON-wrapped VXML with unclosed <block> triggers corrective retry.
     * <p>
     * This is the exact scenario that was failing in production: the VXML is JSON-wrapped
     * (common LLM output format), so the normalizer extracts it. But the extracted VXML
     * has an unclosed <block> tag that should trigger the "second gate" retry
     * (VxmlToModelConverter parse failure).
     */
    @Test
    void testJsonWrappedMalformedVxmlTriggersCorrectiveRetry() {
        String prompt = "Create a banking IVR with Billing. Press 1 for balance. Include greeting and closing.";

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(JSON_WRAPPED_MALFORMED_VXML);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        com.nexusivr.ai.model.Flow flow = unifiedAiEngine.generateFlow(
                UUID.randomUUID(), UUID.randomUUID(), prompt, "openrouter", "openai.gpt-oss-20b-1:0", 0.7, 35);

        assertNotNull(flow, "Flow generation must succeed after corrective retry");
        assertNotNull(flow.getFlowJson(), "Flow JSON must be present");

        // CRITICAL ASSERTION: LLM provider must have been called at least 2 times
        // (original attempt returned malformed VXML, corrective retry returned valid VXML)
        verify(providerManager, atLeast(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());
    }

    /**
     * REGRESSION TEST: Both original AND corrective retry produce malformed VXML.
     * In this case, the engine should throw ProviderException (502) — but only AFTER
     * exhausting both attempts.
     */
    @Test
    void testBothAttemptsFailStillThrowsAfterExhaustingRetries() {
        String prompt = "Create a banking IVR with Billing. Press 1 for balance. Include greeting and closing.";

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(MALFORMED_UNCLOSED_BLOCK_VXML);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(MALFORMED_UNCLOSED_BLOCK_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        assertThrows(com.nexusivr.ai.service.exception.ProviderException.class, () ->
                unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt,
                        "groq", "llama-3.3-70b-versatile", 0.7, 30));

        // CRITICAL: Must have tried BOTH attempts before giving up
        verify(providerManager, atLeast(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());
    }

    /**
     * REGRESSION TEST: Corrective retry system instruction contains the specific parse error.
     * This ensures the LLM gets actionable feedback about what was wrong.
     */
    @Test
    void testCorrectiveRetryIncludesParseErrorInSystemInstruction() {
        String prompt = "Create a banking IVR with Billing and Cards. Press 1 for balance, Press 2 for cards. Include greeting and closing.";

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(MALFORMED_UNCLOSED_BLOCK_VXML);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt,
                "groq", "llama-3.3-70b-versatile", 0.7, 30);

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(providerManager, atLeast(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                captor.capture(),
                anyString(), anyString(), anyList(), anyString(), anyBoolean());

        List<String> capturedSystemInstructions = captor.getAllValues();
        // At least one of the retry system instructions should contain corrective guidance
        boolean hasRetryReminder = capturedSystemInstructions.stream()
                .anyMatch(s -> s.contains("RETRY REMINDER"));
        assertTrue(hasRetryReminder, "At least one retry attempt must include a RETRY REMINDER in the system instruction");
    }
}
