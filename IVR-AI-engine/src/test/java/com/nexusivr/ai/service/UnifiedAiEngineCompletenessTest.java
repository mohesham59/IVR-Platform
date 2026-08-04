package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.flow.FlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UnifiedAiEngine Feature Completeness Regression Test")
class UnifiedAiEngineCompletenessTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;

    private static final String TELECOM_7_FEATURE_VXML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to Telecom IVR. Call recording notice: calls may be recorded for quality assurance.</prompt>
              <goto next="#main_menu"/>
            </block>
          </form>
          <form id="main_menu">
            <menu>
              <prompt>Main menu choices</prompt>
              <choice accept="digits 1" next="#outage_reporting"/>
              <choice accept="digits 2" next="#technical_support"/>
              <choice accept="digits 3" next="#plan_changes"/>
              <choice accept="digits 4" next="#sim_activation"/>
              <choice accept="digits 5" next="#broadband_speed_test"/>
              <choice accept="digits 0" next="#specialist_transfer"/>
            </menu>
          </form>
          <form id="outage_reporting">
            <block><prompt>Outage Reporting service.</prompt></block>
          </form>
          <form id="technical_support">
            <block><prompt>Technical Support service.</prompt></block>
          </form>
          <form id="plan_changes">
            <block><prompt>Plan Changes service.</prompt></block>
          </form>
          <form id="sim_activation">
            <block><prompt>SIM Activation service.</prompt></block>
          </form>
          <form id="broadband_speed_test">
            <block><prompt>Broadband Speed Test service.</prompt></block>
          </form>
          <form id="specialist_transfer">
            <block><prompt>Transferring to Specialist.</prompt></block>
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
                .thenReturn("""
                    {
                      "refined_prompt": "Telecom IVR with 7 features",
                      "business_domain": "telecom",
                      "departments": ["Outage Reporting", "Technical Support", "Plan Changes", "SIM Activation", "Broadband Speed Test"],
                      "features": ["Call Recording Notice", "Specialist Transfer"],
                      "menu_options": ["1-Outage", "2-Tech Support", "3-Plan Changes", "4-SIM Activation", "5-Speed Test"]
                    }
                    """);
    }

    @Test
    @DisplayName("Submit prompt naming 7+ distinct features, run generation 10 times, assert all 7 features present in every run")
    void test7FeatureCompletenessAcross10Runs() throws Exception {
        String prompt = "Create a comprehensive telecom IVR system featuring a greeting, main menu, Outage Reporting, Technical Support, Plan Changes, SIM Activation, Broadband Speed Test, Specialist Transfer, and Call Recording notice.";
        List<String> requiredFeatures = List.of("Outage Reporting", "Technical Support", "Plan Changes", "SIM Activation", "Broadband Speed Test", "Specialist Transfer", "Call Recording");

        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn(TELECOM_7_FEATURE_VXML);
        when(mockResponse.getActualProviderUsed()).thenReturn("gemini");
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        for (int run = 1; run <= 10; run++) {
            RefinedSpecCache.clear();
            SemanticCache.getInstance().clear();

            Flow flow = unifiedAiEngine.generateFlow(UUID.randomUUID(), UUID.randomUUID(), prompt, "gemini", "gemini-2.0-flash", 0.7, 30, List.of());
            assertNotNull(flow, "Run " + run + ": Generated flow must not be null");

            VxmlToModelConverter converter = new VxmlToModelConverter();
            FlowModel model = converter.convert(TELECOM_7_FEATURE_VXML);
            List<String> missing = UnifiedAiEngine.getMissingFeatures(model, requiredFeatures);

            assertTrue(missing.isEmpty(), "Run " + run + " failed: Missing features " + missing + " in generated model!");
        }
    }
}
