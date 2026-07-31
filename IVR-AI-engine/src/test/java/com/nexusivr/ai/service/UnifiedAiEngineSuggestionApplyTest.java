package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedAiEngineSuggestionApplyTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        promptRefinerService = mock(PromptRefinerService.class);
        unifiedAiEngine = new UnifiedAiEngine(providerManager, new com.nexusivr.ai.ai.PromptBuilder(),
                new FlowContextService(), promptRefinerService);
        when(providerManager.isProviderAvailable(anyString())).thenReturn(true);
    }

    @Test
    void testApplySuggestionDelegatesToPatchImprovementPipeline() {
        // Given an initial flow model
        FlowModel model = new FlowModel();
        model.setName("Banking IVR");
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));
        
        FlowNode greeting = new FlowNode("n1", FlowNodeType.PROMPT, "Greeting");
        greeting.setPrompt(new FlowPrompt("Welcome to Banking Services"));
        model.addNode(greeting);

        FlowNode menuNode = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowMenu menu = new FlowMenu();
        menu.addChoice(new FlowChoice("key1", "Accounts", "n3"));
        menuNode.setMenu(menu);
        model.addNode(menuNode);

        model.addNode(new FlowNode("n3", FlowNodeType.PROMPT, "Account Balance"));
        model.addNode(new FlowNode("end", FlowNodeType.END, "End Call"));

        String inputJson = new com.google.gson.Gson().toJson(model);
        UUID sessionId = UUID.randomUUID();

        // Mock LLM returning valid patch operations (ADD_NODE & ADD_EDGE)
        String mockPatchJson = "```json\n" +
                "[\n" +
                "  {\"type\": \"ADD_NODE\", \"newNodeId\": \"n4\", \"nodeType\": \"PROMPT\", \"title\": \"Timeout Fallback\", \"promptText\": \"Please stay on the line\", \"targetNodeId\": \"n2\", \"sourcePort\": \"timeout\"},\n" +
                "  {\"type\": \"ADD_EDGE\", \"edgeId\": \"e5\", \"sourceNodeId\": \"n4\", \"sourcePort\": \"out\", \"targetNodeId\": \"end\", \"targetPort\": \"in\"}\n" +
                "]\n```";

        AiResponse aiResp = new AiResponse(mockPatchJson, "groq", 100, 10, false, "groq", "llama-3.3-70b-versatile");
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), eq("IMPROVE_FLOW_PATCH"), anyList(), anyString(), anyBoolean()))
                .thenReturn(aiResp);

        // When applying suggestion
        String suggestionText = "Add missing Timeout/Error fallback path for Main Menu";
        FlowImprovementResponse response = unifiedAiEngine.applySuggestion(
                sessionId, inputJson, suggestionText, "groq", "llama-3.3-70b-versatile", 0.7, 30
        );

        // Then verify response structure and validation status
        assertNotNull(response);
        assertNotNull(response.getImprovedFlow());
        assertNotNull(response.getFinalValidation());
        assertTrue(response.getFinalValidation().isValid());

        // Verify session domain was recorded
        String sessionDomain = SessionMemoryStore.getDomain(sessionId);
        assertNotNull(sessionDomain);
    }
}
