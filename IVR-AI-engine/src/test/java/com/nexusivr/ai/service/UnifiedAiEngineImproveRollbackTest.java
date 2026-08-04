package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.optimization.SemanticCache;
import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedAiEngineImproveRollbackTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;
    private ModelToFlowRenderer renderer;

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        promptRefinerService = mock(PromptRefinerService.class);
        unifiedAiEngine = new UnifiedAiEngine(providerManager, new com.nexusivr.ai.ai.PromptBuilder(),
                new FlowContextService(), promptRefinerService);
        renderer = new ModelToFlowRenderer();
        RefinedSpecCache.clear();
        SemanticCache.getInstance().clear();
        when(providerManager.isProviderAvailable(anyString())).thenReturn(true);
    }

    @Test
    void testFailedImprovePassRestoresPrePatchSnapshotAndPreservesOriginalTitle() {
        // Create pre-patch FlowModel (8 nodes, 11 connections)
        FlowModel prePatchModel = new FlowModel();
        prePatchModel.setName("Technical Support Line");

        prePatchModel.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));
        
        FlowNode n1 = new FlowNode("n1", FlowNodeType.PROMPT, "Greeting");
        n1.setPrompt(new FlowPrompt("Welcome to support"));
        prePatchModel.addNode(n1);

        FlowNode n2 = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowMenu menu = new FlowMenu();
        menu.addChoice(new FlowChoice("key1", "Tech Support", "n3"));
        menu.addChoice(new FlowChoice("key2", "Billing", "n4"));
        menu.addChoice(new FlowChoice("key3", "Product Info", "n5"));
        n2.setMenu(menu);
        prePatchModel.addNode(n2);

        prePatchModel.addNode(new FlowNode("n3", FlowNodeType.PROMPT, "Technical Support"));
        prePatchModel.addNode(new FlowNode("n4", FlowNodeType.PROMPT, "Billing Inquiries"));
        prePatchModel.addNode(new FlowNode("n5", FlowNodeType.PROMPT, "Product Info"));
        prePatchModel.addNode(new FlowNode("end", FlowNodeType.END, "End Call"));

        prePatchModel.addConnection(new FlowConnection("c_start_n1", "start", "out", "n1", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n1_n2", "n1", "out", "n2", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n2_n3", "n2", "key1", "n3", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n2_n4", "n2", "key2", "n4", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n2_n5", "n2", "key3", "n5", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n3_end", "n3", "out", "end", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n4_end", "n4", "out", "end", "in"));
        prePatchModel.addConnection(new FlowConnection("c_n5_end", "n5", "out", "end", "in"));

        int prePatchNodeCount = prePatchModel.getNodes().size();
        int prePatchConnCount = prePatchModel.getConnections().size();
        assertEquals(7, prePatchNodeCount);
        assertEquals(8, prePatchConnCount);

        String prePatchJson = renderer.render(prePatchModel);

        // LLM returns a destructive patch deleting all nodes in the flow
        String llmPatchJson = """
            [
              {"type": "DELETE_NODE", "nodeIdToDelete": "start"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "n1"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "n2"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "n3"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "n4"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "n5"},
              {"type": "DELETE_NODE", "nodeIdToDelete": "end"}
            ]
            """;

        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn(llmPatchJson);
        when(mockResponse.getActualProviderUsed()).thenReturn("gemini");

        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        FlowImprovementResponse response = unifiedAiEngine.improveFlow(
                UUID.randomUUID(), prePatchJson, "Add fallback paths", "gemini", "gemini-2.0-flash", 0.7, 30
        );

        assertNotNull(response);
        assertTrue(response.isRolledBack(), "Response must be marked rolledBack=true when patches introduce unresolvable branching hub issues");
        assertFalse(response.isImproved(), "Response must NOT be marked improved=true when rolled back");

        FlowDto resultFlow = response.getImprovedFlow();
        assertNotNull(resultFlow);

        // CRITICAL ASSERTION: The resulting FlowModel JSON must match the pre-patch snapshot node & edge counts (7 nodes, 8 connections), NOT 8/13!
        FlowModel returnedModel = FlowContextService.convertJsonToModel(response.getImprovedFlowJson());
        assertNotNull(returnedModel);
        assertEquals(prePatchNodeCount, returnedModel.getNodes().size(), "Node count after rollback must match pre-patch snapshot exactly (7 nodes)");
        assertEquals(prePatchConnCount, returnedModel.getConnections().size(), "Connection count after rollback must match pre-patch snapshot exactly (8 connections)");

        // CRITICAL ASSERTION: The title must retain the original base name and NOT append "(Optimized)" or "(Improved)"
        assertEquals("Technical Support Line", resultFlow.getName(), "Flow title must NOT append '(Optimized)' or '(Improved)' on rollback");
    }
}
