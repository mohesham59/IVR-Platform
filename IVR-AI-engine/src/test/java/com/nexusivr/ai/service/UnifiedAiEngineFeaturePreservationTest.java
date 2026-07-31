package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;

import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedAiEngineFeaturePreservationTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;

    private static final String VXML_WITH_BLOCK_CHOICE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to Telecom Support. Press 1 for Billing, 2 for Roaming.</prompt>
              <choice accept="digits 1" next="#billing"/>
              <choice accept="digits 2" next="#roaming"/>
            </block>
          </form>
          <form id="billing">
            <block><prompt>Billing department.</prompt><disconnect/></block>
          </form>
          <form id="roaming">
            <block><prompt>Roaming department.</prompt><disconnect/></block>
          </form>
        </vxml>
        """;

    private static final String VALID_TELECOM_VXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to Telecom Customer Support.</prompt>
              <goto next="#main_menu"/>
            </block>
          </form>
          <form id="main_menu">
            <menu>
              <prompt>Please select an option.</prompt>
              <choice accept="digits 1" next="#billing"/>
              <choice accept="digits 2" next="#roaming"/>
              <choice accept="digits 3" next="#sim_support"/>
              <choice accept="digits 4" next="#broadband"/>
              <choice accept="digits 5" next="#outage_reporting"/>
              <choice accept="digits 6" next="#plan_changes"/>
              <choice accept="digits 7" next="#call_recording"/>
            </menu>
          </form>
          <form id="billing"><block><prompt>Billing Services.</prompt><disconnect/></block></form>
          <form id="roaming"><block><prompt>Roaming Support.</prompt><disconnect/></block></form>
          <form id="sim_support"><block><prompt>SIM Activation and Support.</prompt><disconnect/></block></form>
          <form id="broadband"><block><prompt>Broadband Speed Test.</prompt><disconnect/></block></form>
          <form id="outage_reporting"><block><prompt>Report Network Outage.</prompt><disconnect/></block></form>
          <form id="plan_changes"><block><prompt>Plan and Package Changes.</prompt><disconnect/></block></form>
          <form id="call_recording"><block><prompt>Call Recording Notice.</prompt><disconnect/></block></form>
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
                .thenReturn("{\"business_domain\":\"telecom\",\"departments\":[\"Billing\",\"Roaming\",\"SIM Support\",\"Broadband\",\"Outage Reporting\",\"Plan Changes\",\"Call Recording Notice\"]}");
    }

    @Test
    void testBlockContainingChoiceTriggersAttempt2Retry() {
        String prompt = "Create a telecom customer support IVR with Billing, Roaming, SIM Support, Broadband, Outage Reporting, Plan Changes, and Call Recording Notice";

        AiResponse attempt1Response = mock(AiResponse.class);
        when(attempt1Response.isMock()).thenReturn(false);
        when(attempt1Response.getContent()).thenReturn(VXML_WITH_BLOCK_CHOICE);
        when(attempt1Response.getActualProviderUsed()).thenReturn("gemini");

        AiResponse attempt2Response = mock(AiResponse.class);
        when(attempt2Response.isMock()).thenReturn(false);
        when(attempt2Response.getContent()).thenReturn(VALID_TELECOM_VXML);
        when(attempt2Response.getActualProviderUsed()).thenReturn("gemini");

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(attempt1Response)
                .thenReturn(attempt2Response);

        Flow flow = unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30);

        assertNotNull(flow);

        // Verify that Attempt 2 was triggered with augmented instructions explaining the <menu> requirement
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(providerManager, times(2)).executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                captor.capture(),
                anyString(), anyString(), anyList(), anyString(), anyBoolean()
        );

        List<String> capturedInstructions = captor.getAllValues();
        assertEquals(2, capturedInstructions.size());
        assertTrue(capturedInstructions.get(1).contains("RETRY REMINDER: Your previous VoiceXML response was invalid because navigation <choice> tags were placed inside a <block>"));
    }

    @Test
    void testMultiFeatureTelecomPromptPreservesAllNamedFeaturesInFinalFlow() {
        String prompt = "Create a telecom customer support IVR. Include departments for Billing, Roaming, SIM Support, and Broadband. Features: outage reporting, plan changes, SIM activation, broadband speed test, and call recording notice.";

        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn(VALID_TELECOM_VXML);
        when(mockResponse.getActualProviderUsed()).thenReturn("gemini");

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        Flow flow = unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30);

        assertNotNull(flow);
        assertNotNull(flow.getFlowJson());

        FlowModel model = FlowContextService.convertJsonToModel(flow.getFlowJson());
        assertNotNull(model);

        // Verify all 7 requested features exist as nodes in the final flow
        List<String> expectedFeatures = List.of("Billing", "Roaming", "SIM Support", "Broadband", "Outage", "Plan", "Recording");
        for (String feature : expectedFeatures) {
            boolean exists = model.getNodes().stream().anyMatch(n ->
                    (n.getId() != null && n.getId().toLowerCase().contains(feature.toLowerCase())) ||
                    (n.getTitle() != null && n.getTitle().toLowerCase().contains(feature.toLowerCase()))
            );
            assertTrue(exists, "Expected feature node matching '" + feature + "' to exist in final FlowModel");
        }

        // Verify no requested features were dropped
        assertTrue(flow.getDroppedFeatures().isEmpty(), "No requested features should be dropped");
    }
}
