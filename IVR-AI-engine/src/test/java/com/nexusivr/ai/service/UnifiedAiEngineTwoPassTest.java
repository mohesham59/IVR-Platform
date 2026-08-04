package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedAiEngineTwoPassTest {

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
              <prompt>Press 1 for Billing, 2 for Cards, 3 for Loans.</prompt>
              <choice accept="digits 1" next="#billing"/>
              <choice accept="digits 2" next="#cards"/>
              <choice accept="digits 3" next="#loans"/>
            </menu>
          </form>
          <form id="billing">
            <block><prompt>Billing, account balance, and account details.</prompt></block>
          </form>
          <form id="cards">
            <block><prompt>Cards department.</prompt></block>
          </form>
          <form id="loans">
            <block><prompt>Loans department.</prompt></block>
          </form>
          <form id="orders">
            <block><prompt>Orders department.</prompt></block>
          </form>
          <form id="reservations">
            <block><prompt>Reservations department.</prompt></block>
          </form>
          <form id="pin_authentication">
            <block><prompt>PIN authentication prompt.</prompt></block>
          </form>
          <form id="appointments">
            <block><prompt>Appointments department.</prompt></block>
          </form>
          <form id="pharmacy">
            <block><prompt>Pharmacy department.</prompt></block>
          </form>
          <form id="triage">
            <block><prompt>Triage department.</prompt></block>
          </form>
          <form id="end">
            <block>
              <prompt>Goodbye.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>
        """;

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

    @Test
    void testPass1RefinementIsUnconditionalForFlowGenerations() {
        String prompt = "Create a banking IVR with Billing, Cards, and Loans. Press 1 for balance, Press 2 for cards, Press 3 for loans. Include greeting and closing.";
        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn(VALID_VXML);
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());

        verify(promptRefinerService, times(1)).refine(anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyList(), anyString());
        verify(providerManager, times(1)).executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    void testUnconditionalPass1ReusesSpecCacheOnRepeatedCalls() {
        String prompt = "Create a clinic IVR with Appointments and Billing";
        String refinedSpecJson = "{\"business_domain\":\"healthcare\",\"departments\":[\"Appointments\",\"Billing\"],\"menu_options\":[\"1-Appointments\",\"2-Billing\"]}";

        when(promptRefinerService.refine(eq(prompt), anyString(), anyString(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(refinedSpecJson);

        AiResponse mockResponse1 = mock(AiResponse.class);
        when(mockResponse1.isMock()).thenReturn(false);
        when(mockResponse1.getContent()).thenReturn(VALID_VXML);

        AiResponse mockResponse2 = mock(AiResponse.class);
        when(mockResponse2.isMock()).thenReturn(false);
        when(mockResponse2.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        // Call 1
        unifiedAiEngine.generateFlow(tenantId, UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());

        // Call 2 with identical prompt and tenantId
        unifiedAiEngine.generateFlow(tenantId, UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());

        // Verify Pass 1 refiner was executed once (cached for call 2)
        verify(promptRefinerService, times(1)).refine(eq(prompt), anyString(), anyString(), anyDouble(), anyInt(), any(), any());
    }

    @Test
    void testCacheKeyTenantIsolation() {
        String prompt = "design Clinic IVR";
        String refinedSpecJson = "{\"business_domain\":\"clinic\",\"departments\":[\"Appointments\",\"Billing\"],\"menu_options\":[\"1-Appointments\",\"2-Billing\"]}";

        when(promptRefinerService.refine(eq(prompt), anyString(), anyString(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(refinedSpecJson);

        AiResponse mockResponse1 = mock(AiResponse.class);
        when(mockResponse1.isMock()).thenReturn(false);
        when(mockResponse1.getContent()).thenReturn(VALID_VXML);

        AiResponse mockResponse2 = mock(AiResponse.class);
        when(mockResponse2.isMock()).thenReturn(false);
        when(mockResponse2.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        unifiedAiEngine.generateFlow(tenant1, UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());
        unifiedAiEngine.generateFlow(tenant2, UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());

        // Different tenants must both execute prompt refiner independently (isolated cache keys)
        verify(promptRefinerService, times(2)).refine(eq(prompt), anyString(), anyString(), anyDouble(), anyInt(), any(), any());
    }

    @Test
    void testNestedObjectVxmlTriggersRetryWithAugmentedSystemInstruction() {
        String wellSpecified = "Create a secure banking IVR system with PIN authentication, account balance, and multi-menu routing. Press 1 for balance, Press 2 for cards, Press 3 for loans. Include greeting and closing.";
        String nestedObjectResponse = "{\"vxml\": {\"version\": \"2.1\", \"forms\": {\"start\": {\"block\": {\"prompt\": \"Welcome\"}}}}}";

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(nestedObjectResponse);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), wellSpecified, "gemini", "gemini-2.0-flash", 0.7, 30);

        verify(providerManager, times(2)).executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(providerManager, org.mockito.Mockito.times(2)).executeWithRetryAndFallback(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt(),
                captor.capture(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
        List<String> captured = captor.getAllValues();
        assertEquals(2, captured.size());
        assertTrue(captured.get(1).contains("RETRY REMINDER: Your previous response had the wrong shape"),
                "Retry should include augmented system instruction");
    }

    @Test
    void testUnclosedBlockTagTriggersAttempt2WithAugmentedInstruction() {
        String wellSpecified = "Create a hospital IVR system with Appointments, Pharmacy, and Triage. Press 1 for appointments, Press 2 for pharmacy. Include greeting and closing.";
        String unclosedBlockVxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <prompt>Welcome to hospital.</prompt>
                  <goto next="#menu"/>
              </form>
              <form id="menu">
                <menu>
                  <prompt>Press 1 for appointments.</prompt>
                  <choice accept="digits 1" next="#end"/>
                </menu>
              </form>
              <form id="end">
                <block>
                  <prompt>Goodbye.</prompt>
                  <disconnect/>
                </block>
              </form>
            </vxml>
            """;

        AiResponse firstAttempt = mock(AiResponse.class);
        when(firstAttempt.isMock()).thenReturn(false);
        when(firstAttempt.getContent()).thenReturn(unclosedBlockVxml);

        AiResponse retryAttempt = mock(AiResponse.class);
        when(retryAttempt.isMock()).thenReturn(false);
        when(retryAttempt.getContent()).thenReturn(VALID_VXML);

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(firstAttempt)
                .thenReturn(retryAttempt);

        unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), wellSpecified, "groq", "llama-3.3-70b-versatile", 0.7, 30);

        verify(providerManager, times(2)).executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean());

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(providerManager, times(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                captor.capture(),
                anyString(), anyString(), anyList(), anyString(), anyBoolean()
        );
        List<String> captured = captor.getAllValues();
        assertEquals(2, captured.size());
        assertTrue(captured.get(1).contains("RETRY REMINDER: Your previous VoiceXML response contained malformed or unclosed XML tags"),
                "Retry instruction must remind LLM to fix malformed/unclosed XML tags");
    }
}
