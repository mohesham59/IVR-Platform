package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.MockLlmClient;
import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.dto.response.AiSuggestionDto;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLM-Driven AI Suggestions Integration Test")
class AiSuggestionsLlmIntegrationTest {

    private UnifiedAiEngine engine;
    private ModelToFlowRenderer renderer;

    @BeforeEach
    void setUp() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());
        engine = new UnifiedAiEngine(pm, new PromptBuilder(), new FlowContextService());
        renderer = new ModelToFlowRenderer();
    }

    @Test
    @DisplayName("Assert that generateAiSuggestions calls LLM agent and returns structured suggestions referencing broken nodes by name")
    void testGenerateAiSuggestionsReturnsLlmAdviceReferencingBrokenElementByName() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Node");
        FlowNode menu = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu");
        FlowNode billing = new FlowNode("billing_queue", FlowNodeType.PROMPT, "Billing Queue");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(billing);
        model.addNode(end);

        // Intentionally broken structure: "billing_queue" is orphaned (unreachable)
        model.addConnection(new FlowConnection("c1", "start", "out", "main_menu", "in"));
        model.addConnection(new FlowConnection("c2", "main_menu", "key1", "end", "in"));

        String flowJson = renderer.render(model);
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        List<AiSuggestionDto> suggestions = engine.generateAiSuggestions(
                sessionId, tenantId, flowJson, null, "mock", "default", 0.7, 30
        );

        assertNotNull(suggestions, "AI Suggestions response must not be null");
        assertFalse(suggestions.isEmpty(), "AI Suggestions endpoint must return at least one suggestion for broken flow");

        boolean foundMatchingNodeName = suggestions.stream()
                .anyMatch(s -> s.getText().contains("Billing Queue") || s.getText().contains("main_menu") || s.getText().contains("Main Menu") || "billing_queue".equals(s.getNodeId()));

        assertTrue(foundMatchingNodeName, "At least one LLM suggestion must reference the broken flow element by name (Billing Queue / main_menu)");

        boolean hasLlmAdviceType = suggestions.stream()
                .anyMatch(s -> "llm_advice".equals(s.getSuggestionType()));

        assertTrue(hasLlmAdviceType, "Suggestions must be typed as 'llm_advice' to distinguish from local safety net repairs");
    }
}
