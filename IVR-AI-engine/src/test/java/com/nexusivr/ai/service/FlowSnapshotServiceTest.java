package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.FlowSnapshot;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.controller.ServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FlowSnapshotService Integration & Routing Tests")
public class FlowSnapshotServiceTest {

    private FlowSnapshotService snapshotService;
    private UnifiedAiEngine unifiedAiEngine;
    private ChatService chatService;
    private AiOperationRouter router;

    private ProviderManager providerManager;
    private PromptBuilder promptBuilder;
    private FlowContextService flowContextService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private final String v1FlowJson = "{"
            + "\"name\":\"V1 Flow\","
            + "\"nodes\":["
            + "  {\"id\":\"n1\",\"type\":\"start\",\"label\":\"Start Node\"}"
            + "],"
            + "\"edges\":[]"
            + "}";

    private final String v2FlowJson = "{"
            + "\"name\":\"V2 Improved Flow\","
            + "\"nodes\":["
            + "  {\"id\":\"n1\",\"type\":\"start\",\"label\":\"Start Node\"},"
            + "  {\"id\":\"n2\",\"type\":\"queue\",\"label\":\"Support Queue\"}"
            + "],"
            + "\"edges\":["
            + "  {\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}"
            + "]"
            + "}";

    private final String v1Vxml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
            + "  <form id=\"start\">"
            + "    <block>"
            + "      <prompt>Welcome to V1 Flow</prompt>"
            + "      <goto next=\"#n1\"/>"
            + "    </block>"
            + "  </form>"
            + "  <form id=\"n1\">"
            + "    <block>"
            + "      <prompt>Start Node</prompt>"
            + "      <disconnect/>"
            + "    </block>"
            + "  </form>"
            + "</vxml>";

    private final String v2Vxml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
            + "  <form id=\"start\">"
            + "    <block>"
            + "      <prompt>Welcome to V2 Improved Flow</prompt>"
            + "      <goto next=\"#support_queue\"/>"
            + "    </block>"
            + "  </form>"
            + "  <form id=\"support_queue\">"
            + "    <block>"
            + "      <prompt>Support Queue</prompt>"
            + "      <disconnect/>"
            + "    </block>"
            + "  </form>"
            + "</vxml>";

    private LlmClient llmClientMock;

    @BeforeEach
    void setUp() {
        snapshotService = ServiceRegistry.getFlowSnapshotService();
        snapshotService.clear();

        providerManager = new ProviderManager();
        providerManager.clearCache();

        promptBuilder = mock(PromptBuilder.class);
        flowContextService = mock(FlowContextService.class);

        llmClientMock = mock(LlmClient.class);
        providerManager.setOverrideClient(llmClientMock);

        // Mock generate structured response for V1 (now returns VXML)
        AiResponse v1LlmResponse = mock(AiResponse.class);
        when(v1LlmResponse.getContent()).thenReturn(v1Vxml);
        when(v1LlmResponse.getSelectedProvider()).thenReturn("groq");
        when(v1LlmResponse.getActualProviderUsed()).thenReturn("groq");
        when(llmClientMock.generateStructuredResponse(any(), any(), any())).thenReturn(v1LlmResponse);
        when(llmClientMock.generateResponse(any(), any(), any())).thenReturn(v1LlmResponse);

        unifiedAiEngine = new UnifiedAiEngine(providerManager, promptBuilder, flowContextService);
        chatService = mock(ChatService.class);
        router = new AiOperationRouter(unifiedAiEngine, chatService);
    }

    @AfterEach
    void tearDown() {
        if (providerManager != null) {
            providerManager.clearCache();
        }
    }

    @Test
    @DisplayName("Should create V1 snapshot on generate and V2 snapshot on improve without overwriting previous snapshots")
    void testVersioningAndImmutability() {
        // 1. Generate V1
        Flow v1Flow = unifiedAiEngine.generateFlow(tenantId, sessionId, "Create V1 Flow", null, null, -1, -1);
        assertNotNull(v1Flow);
        
        FlowSnapshot snap1 = snapshotService.getLatestSnapshot(sessionId);
        assertNotNull(snap1);
        assertEquals(1, snap1.getVersion());
        assertTrue(snap1.getFlowJson().contains("V1 Flow"));
        assertEquals(v1Flow.getId(), snap1.getFlowId());

        // Mock LLM for V2 improvement response (returns patch JSON per patch optimizer architecture)
        AiResponse v2LlmResponse = mock(AiResponse.class);
        String patchJson = "[{\"type\":\"ADD_NODE\",\"nodeType\":\"QUEUE\",\"nodeId\":\"n2\",\"title\":\"V2 Improved Flow\",\"subtitle\":\"V2 Improved Flow\",\"newPromptText\":\"V2 Improved Flow\"},{\"type\":\"ADD_EDGE\",\"sourceNodeId\":\"n1\",\"sourcePort\":\"out\",\"targetNodeId\":\"n2\",\"targetPort\":\"in\"}]";
        when(v2LlmResponse.getContent()).thenReturn(patchJson);
        when(v2LlmResponse.getSelectedProvider()).thenReturn("groq");
        when(v2LlmResponse.getActualProviderUsed()).thenReturn("groq");
        when(llmClientMock.generateResponse(any(), any(), any())).thenReturn(v2LlmResponse);
        when(llmClientMock.generateStructuredResponse(any(), any(), any())).thenReturn(v2LlmResponse);

        // 2. Improve V1 -> V2
        FlowImprovementResponse improveRes = unifiedAiEngine.improveFlow(sessionId, v1FlowJson, "Add a support queue", null, null, -1, -1);
        assertNotNull(improveRes);

        FlowSnapshot snap2 = snapshotService.getLatestSnapshot(sessionId);
        assertNotNull(snap2);
        assertEquals(2, snap2.getVersion());
        assertTrue(snap2.getFlowJson().contains("V2 Improved Flow"));
        assertEquals(snap1.getFlowId(), snap2.getFlowId()); // Flow ID remains constant for updates

        // 3. Verify V1 snapshot remains completely unchanged
        FlowSnapshot retrievedV1 = snapshotService.getSnapshot(snap1.getSnapshotId());
        assertNotNull(retrievedV1);
        assertEquals(1, retrievedV1.getVersion());
        assertTrue(retrievedV1.getFlowJson().contains("V1 Flow"));

        // 4. Verify Export JSON for V1 and V2 are correct
        ChatResponse exportV1 = (ChatResponse) router.route(AiOperation.EXPORT_JSON, sessionId, tenantId, "", null, snap1.getSnapshotId(), null, null, -1.0, -1);
        assertTrue(exportV1.getReplyMessage().contains("V1 Flow"));

        ChatResponse exportV2 = (ChatResponse) router.route(AiOperation.EXPORT_JSON, sessionId, tenantId, "", null, snap2.getSnapshotId(), null, null, -1.0, -1);
        assertTrue(exportV2.getReplyMessage().contains("V2 Improved Flow"));

        // 5. Verify flow questions (e.g. NODE_COUNT) retrieve correct counts from correct snapshots
        ChatResponse countV1 = (ChatResponse) router.route(AiOperation.NODE_COUNT, sessionId, tenantId, "", null, snap1.getSnapshotId(), null, null, -1.0, -1);
        assertTrue(countV1.getReplyMessage().contains("3 nodes"));

        ChatResponse countV2 = (ChatResponse) router.route(AiOperation.NODE_COUNT, sessionId, tenantId, "", null, snap2.getSnapshotId(), null, null, -1.0, -1);
        assertTrue(countV2.getReplyMessage().contains("3 nodes"));
    }
}
