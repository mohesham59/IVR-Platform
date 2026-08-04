package com.nexusivr.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.controller.AiFlowServlet;
import com.nexusivr.ai.model.Flow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class FlowGenerationSseTest {

    private UnifiedAiEngine unifiedAiEngine;
    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private FlowContextService flowContextService;
    private VxmlToModelConverter vxmlToModelConverter;
    private Gson gson;

    @BeforeEach
    void setUp() throws Exception {
        providerManager = Mockito.mock(ProviderManager.class);
        promptRefinerService = Mockito.mock(PromptRefinerService.class);
        flowContextService = Mockito.mock(FlowContextService.class);
        vxmlToModelConverter = new VxmlToModelConverter();
        gson = new Gson();

        unifiedAiEngine = new UnifiedAiEngine(
                providerManager,
                new com.nexusivr.ai.ai.PromptBuilder(),
                flowContextService,
                promptRefinerService
        );
        com.nexusivr.ai.controller.ServiceRegistry.setUnifiedAiEngine(unifiedAiEngine);


        // Mock Pass 1 PromptRefiner output
        when(promptRefinerService.refine(anyString(), anyString(), anyString(), anyDouble(), anyInt(), any(), anyString()))
                .thenReturn("{\"business_domain\":\"hospitality\",\"summary\":\"Hotel Concierge IVR\",\"requested_features\":[\"reservations\",\"dining\"]}");

        // Mock Pass 2 LLM output (Valid VoiceXML wrapped in JSON)
        String vxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <prompt>Welcome to Hotel Concierge Services. Press 1 for Reservations, 2 for Dining.</prompt>
                  <goto next="#menu"/>
                </block>
              </form>
              <form id="menu">
                <menu>
                  <prompt>Press 1 for Reservations, 2 for Dining.</prompt>
                  <choice accept="digits 1" next="#end"/>
                </menu>
              </form>
              <form id="end">
                <block><prompt>Thank you for calling.</prompt><disconnect/></block>
              </form>
            </vxml>
            """;
        JsonObject jsonResp = new JsonObject();
        jsonResp.addProperty("vxml", vxml);

        AiResponse aiResponse = new AiResponse(jsonResp.toString(), "groq", 100, 200, true);
        when(providerManager.executeWithRetryAndFallback(
                anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()
        )).thenReturn(aiResponse);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.nexusivr.ai.controller.ServiceRegistry.setUnifiedAiEngine(null);
    }


    @Test
    @DisplayName("UnifiedAiEngine emits all 8 pipeline milestones sequentially during flow generation")
    void testUnifiedAiEngineEmitsAll8PipelineStagesSequentially() {
        List<String> emittedStages = new ArrayList<>();
        List<String> emittedMessages = new ArrayList<>();

        ProgressListener listener = (stage, message) -> {
            emittedStages.add(stage);
            emittedMessages.add(message);
        };

        Flow flow = unifiedAiEngine.generateFlow(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Create a professional hotel concierge IVR flow for reservations and dining",
                "groq",
                "llama-3.3-70b-versatile",
                0.2,
                30,
                null,
                listener
        );

        assertNotNull(flow, "Flow must be generated");

        // Verify that multiple real progress stages were emitted sequentially
        assertTrue(emittedStages.size() >= 7, "Must emit at least 7 pipeline stage milestones (emitted: " + emittedStages.size() + ")");

        // Check key milestones in order
        assertTrue(emittedStages.contains("understanding"), "Must emit 'understanding' stage");
        assertTrue(emittedStages.contains("template"), "Must emit 'template' stage");
        assertTrue(emittedStages.contains("analysis"), "Must emit 'analysis' stage");
        assertTrue(emittedStages.contains("planning"), "Must emit 'planning' stage");
        assertTrue(emittedStages.contains("generating"), "Must emit 'generating' stage");
        assertTrue(emittedStages.contains("validating"), "Must emit 'validating' stage");
        assertTrue(emittedStages.contains("converting"), "Must emit 'converting' stage");
        assertTrue(emittedStages.contains("rendering"), "Must emit 'rendering' stage");

        // Assert strictly sequential order
        int idxUnderstanding = emittedStages.indexOf("understanding");
        int idxAnalysis = emittedStages.indexOf("analysis");
        int idxGenerating = emittedStages.indexOf("generating");
        int idxRendering = emittedStages.indexOf("rendering");

        assertTrue(idxUnderstanding < idxAnalysis, "'understanding' must occur before 'analysis'");
        assertTrue(idxAnalysis < idxGenerating, "'analysis' must occur before 'generating'");
        assertTrue(idxGenerating < idxRendering, "'generating' must occur before 'rendering'");
    }

    @Test
    @DisplayName("AiFlowServlet /api/v1/ai/flow/generate-stream sets SSE headers and streams progress events")
    void testAiFlowServletGenerateStreamReturnsSseContentTypeAndProgressEvents() throws Exception {
        AiFlowServlet servlet = new AiFlowServlet();

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

        when(request.getRequestURI()).thenReturn("/api/v1/ai/flow/generate-stream");
        when(request.getHeader("X-Tenant-ID")).thenReturn(UUID.randomUUID().toString());
        when(request.getHeader("X-AI-Provider")).thenReturn("groq");
        when(request.getHeader("X-Session-ID")).thenReturn(UUID.randomUUID().toString());

        when(request.getMethod()).thenReturn("POST");
        String jsonInput = "{\"description\":\"Create a hotel concierge IVR system\"}";
        BufferedReader reader = new BufferedReader(new StringReader(jsonInput));
        when(request.getReader()).thenReturn(reader);

        StringWriter out = new StringWriter();
        PrintWriter printWriter = new PrintWriter(out);
        when(response.getWriter()).thenReturn(printWriter);

        servlet.service(request, response);

        Mockito.verify(response).setContentType("text/event-stream");
        Mockito.verify(response).setHeader("Cache-Control", "no-cache");
        Mockito.verify(response).setHeader("Connection", "keep-alive");

        String sseOutput = out.toString();
        assertNotNull(sseOutput, "SSE output must not be null");
        assertTrue(sseOutput.contains("event: progress"), "SSE stream must contain progress events");
        assertTrue(sseOutput.contains("event: complete"), "SSE stream must contain complete event");
        assertTrue(sseOutput.contains("\"stage\":\"rendering\""), "SSE complete event must contain rendering stage");
    }
}
