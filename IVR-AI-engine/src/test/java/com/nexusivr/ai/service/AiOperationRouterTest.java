package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.common.FlowNodeDto;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AiOperationRouter Unit Tests")
public class AiOperationRouterTest {

    private UnifiedAiEngine unifiedAiEngine;
    private ChatService chatService;
    private FlowContextService flowContextService;
    private AiService aiService;
    private AiOperationRouter router;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final String sampleFlowJson = "{"
            + "\"name\":\"Sample Flow\","
            + "\"nodes\":["
            + "  {\"id\":\"n1\",\"type\":\"start\",\"label\":\"Start Node\"},"
            + "  {\"id\":\"n2\",\"type\":\"queue\",\"label\":\"Billing Queue\"}"
            + "],"
            + "\"edges\":["
            + "  {\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}"
            + "]"
            + "}";

    @BeforeEach
    void setUp() {
        unifiedAiEngine = mock(UnifiedAiEngine.class);
        chatService = mock(ChatService.class);
        flowContextService = mock(FlowContextService.class);
        when(unifiedAiEngine.getFlowContextService()).thenReturn(flowContextService);
        aiService = mock(AiService.class);
        when(unifiedAiEngine.validateFlow(anyString())).thenReturn(new FlowValidationResponse(true, List.of()));
        when(unifiedAiEngine.resolveStoredFlowToJson(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatService.getAiService()).thenReturn(aiService);

        router = new AiOperationRouter(unifiedAiEngine, chatService);

        // Pre-populate SessionMemoryStore with a FlowModel
        SessionMemoryStore.clear(sessionId);
        FlowModel sampleModel = buildSampleFlowModel();
        SessionMemoryStore.saveModel(sessionId, sampleModel);
        when(flowContextService.getActiveFlowModel(sessionId)).thenReturn(sampleModel);
    }

    @Test
    @DisplayName("Should classify prompts correctly into appropriate operations")
    void testClassification() {
        assertEquals(AiOperation.EXPORT_JSON, router.classify("give me the json code of this design"));
        assertEquals(AiOperation.EXPORT_JSON, router.classify("Is this the exact JSON?"));
        assertEquals(AiOperation.EXPORT_XML, router.classify("export xml of the canvas"));
        assertEquals(AiOperation.NODE_COUNT, router.classify("how many nodes?"));
        assertEquals(AiOperation.NODE_NAMES, router.classify("tell me their names"));
        assertEquals(AiOperation.FLOW_SUMMARY, router.classify("summarize this flow"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("generate flow for bank"));
        assertEquals(AiOperation.IMPROVE_FLOW, router.classify("optimize this flow"));
        assertEquals(AiOperation.CHAT, router.classify("hello there assistant"));
    }

    @Test
    @DisplayName("Should route EXPORT_JSON directly from FlowContextService without calling LLM")
    void testRouteExportJson() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);

        Object result = router.route(AiOperation.EXPORT_JSON, sessionId, tenantId, "", null);

        assertTrue(result instanceof ChatResponse);
        ChatResponse resp = (ChatResponse) result;
        assertTrue(resp.getReplyMessage().contains("canvas"));
        assertTrue(resp.getReplyMessage().contains("Billing Queue"));

        // Verify NO LLM calls
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Should route EXPORT_XML directly from FlowModel without calling LLM")
    void testRouteExportXml() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);

        Object result = router.route(AiOperation.EXPORT_XML, sessionId, tenantId, "", null);

        assertTrue(result instanceof ChatResponse);
        ChatResponse resp = (ChatResponse) result;
        assertTrue(resp.getReplyMessage().contains("```xml"), "Should contain XML code block");
        assertTrue(resp.getReplyMessage().contains("<vxml"), "Should contain VoiceXML root element");
        assertTrue(resp.getReplyMessage().contains("n1"), "Should contain node IDs in VoiceXML");

        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Should route NODE_COUNT directly from FlowContextService/SessionMemory without calling LLM")
    void testRouteNodeCount() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);

        Object result = router.route(AiOperation.NODE_COUNT, sessionId, tenantId, "", null);

        assertTrue(result instanceof ChatResponse);
        ChatResponse resp = (ChatResponse) result;
        assertTrue(resp.getReplyMessage().contains("2 nodes"));

        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Should route NODE_NAMES directly from FlowContextService/SessionMemory without calling LLM")
    void testRouteNodeNames() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);

        Object result = router.route(AiOperation.NODE_NAMES, sessionId, tenantId, "", null);

        assertTrue(result instanceof ChatResponse);
        ChatResponse resp = (ChatResponse) result;
        assertTrue(resp.getReplyMessage().contains("Billing Queue"));

        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Should route FLOW_SUMMARY using LLM to summarize without regenerating flow")
    void testRouteFlowSummary() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);
        when(aiService.generateResponse(anyString(), any())).thenReturn("Mock summary text");

        Object result = router.route(AiOperation.FLOW_SUMMARY, sessionId, tenantId, "", null);

        assertTrue(result instanceof ChatResponse);
        ChatResponse resp = (ChatResponse) result;
        assertEquals("Mock summary text", resp.getReplyMessage());

        verify(aiService, times(1)).generateResponse(anyString(), any());
    }

    @Test
    @DisplayName("Should route GENERATE_FLOW through UnifiedAiEngine")
    void testRouteGenerateFlow() {
        Flow mockFlow = new Flow();
        mockFlow.setName("Gen Flow");
        when(unifiedAiEngine.generateFlow(any(), any(), anyString(), any(), any(), anyDouble(), anyInt(), anyList(), anyBoolean()))
                .thenReturn(mockFlow);

        Object result = router.route(AiOperation.GENERATE_FLOW, sessionId, tenantId, "Build bank", null);

        assertSame(mockFlow, result);
        verify(unifiedAiEngine, times(1)).generateFlow(any(), any(), anyString(), any(), any(), anyDouble(), anyInt(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("Should pass explicit provider through to GENERATE_FLOW")
    void testRouteGenerateFlowWithExplicitProvider() {
        Flow mockFlow = new Flow();
        mockFlow.setName("Gen Flow");
        when(unifiedAiEngine.generateFlow(any(), any(), anyString(), eq("gemini"), eq("gemini-2.0-flash"), anyDouble(), anyInt(), anyList(), anyBoolean()))
                .thenReturn(mockFlow);

        Object result = router.route(AiOperation.GENERATE_FLOW, sessionId, tenantId, "Build bank", null,
                null, "gemini", "gemini-2.0-flash", 0.7, 30);

        assertSame(mockFlow, result);
        verify(unifiedAiEngine, times(1)).generateFlow(any(), any(), anyString(), eq("gemini"), eq("gemini-2.0-flash"), anyDouble(), anyInt(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("Should route IMPROVE_FLOW through UnifiedAiEngine")
    void testRouteImproveFlow() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);
        FlowImprovementResponse mockImprovement = new FlowImprovementResponse(new FlowDto(), List.of("Change 1"), "Rationale 1");
        when(unifiedAiEngine.improveFlow(any(), anyString(), anyString(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(mockImprovement);

        Object result = router.route(AiOperation.IMPROVE_FLOW, sessionId, tenantId, "add loyalty", null);

        assertSame(mockImprovement, result);
        verify(unifiedAiEngine, times(1)).improveFlow(any(), anyString(), anyString(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("Should pass explicit provider through to IMPROVE_FLOW")
    void testRouteImproveFlowWithExplicitProvider() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);
        FlowImprovementResponse mockImprovement = new FlowImprovementResponse(new FlowDto(), List.of("Change 1"), "Rationale 1");
        when(unifiedAiEngine.improveFlow(any(), anyString(), anyString(), eq("gemini"), eq("gemini-2.0-flash"), anyDouble(), anyInt()))
                .thenReturn(mockImprovement);

        Object result = router.route(AiOperation.IMPROVE_FLOW, sessionId, tenantId, "add loyalty", null,
                null, "gemini", "gemini-2.0-flash", 0.7, 30);

        assertSame(mockImprovement, result);
        verify(unifiedAiEngine, times(1)).improveFlow(any(), anyString(), anyString(), eq("gemini"), eq("gemini-2.0-flash"), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("Should route VALIDATE_FLOW through UnifiedAiEngine.validateFlow")
    void testRouteValidateFlow() {
        when(flowContextService.getActiveFlow(sessionId)).thenReturn(sampleFlowJson);
        FlowValidationResponse mockValidation = new FlowValidationResponse(true, List.of());
        when(unifiedAiEngine.validateFlow(anyString())).thenReturn(mockValidation);

        Object result = router.route(AiOperation.VALIDATE_FLOW, sessionId, tenantId, "", null);

        assertSame(mockValidation, result);
        verify(unifiedAiEngine, times(1)).validateFlow(sampleFlowJson);
    }

    private FlowModel buildSampleFlowModel() {
        FlowModel model = new FlowModel();
        model.setId("flow-1");
        model.setName("Sample Flow");
        model.setDescription("Test flow for router tests");

        FlowNode n1 = new FlowNode("n1", FlowNodeType.START, "Start Node");
        FlowNode n2 = new FlowNode("n2", FlowNodeType.QUEUE, "Billing Queue");

        model.addNode(n1);
        model.addNode(n2);
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        return model;
    }
}
