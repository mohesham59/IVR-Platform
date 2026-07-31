package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.AiSuggestionDto;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlreadyConnectedMenuSuggestionTest {

    private UnifiedAiEngine unifiedAiEngine;

    @BeforeEach
    void setUp() {
        unifiedAiEngine = new UnifiedAiEngine(null, new com.nexusivr.ai.ai.PromptBuilder(), new FlowContextService(), null);
    }

    @Test
    void testAlreadyConnectedTargetNodeProducesNoRedundantMenuSuggestion() {
        FlowModel model = new FlowModel();
        model.setName("University IVR");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode menuNode = new FlowNode("menu_1", FlowNodeType.MENU, "Main Menu");
        FlowNode admNode = new FlowNode("n_adm", FlowNodeType.PROMPT, "Admissions");

        model.addNode(startNode);
        model.addNode(menuNode);
        model.addNode(admNode);

        // Admissions is ALREADY connected to Main Menu
        model.addConnection(new FlowConnection("c1", "start", "out", "menu_1", "in"));
        model.addConnection(new FlowConnection("c2", "menu_1", "key1", "n_adm", "in"));

        ValidationIssueDto issue = new ValidationIssueDto(
                ValidationSeverity.WARNING,
                "CONVERGING_PATHS",
                "Multiple incoming paths converge into node 'n_adm'",
                "n_adm",
                "Unbranched routing"
        );

        String flowJson = new ModelToFlowRenderer().render(model);

        List<AiSuggestionDto> suggestions = unifiedAiEngine.generateAiSuggestions(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), flowJson, List.of(issue), "groq", "llama-3.3-70b-versatile", 0.7, 30
        );

        // Verify NO suggestion is generated for n_adm because it's already connected to Menu
        boolean hasAdmMenuSuggestion = suggestions.stream()
                .anyMatch(s -> s.getText().contains("Admissions") && s.getText().contains("Menu"));

        assertFalse(hasAdmMenuSuggestion, "Must NOT generate menu reconnect suggestion for node 'Admissions' because it is already connected to Main Menu");
    }

    @Test
    void testEdgeCountDeltaMatchesActualAdditions() {
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));
        model.addNode(new FlowNode("n1", FlowNodeType.PROMPT, "Greeting"));
        model.addNode(new FlowNode("end", FlowNodeType.END, "End Call"));

        model.addConnection(new FlowConnection("c1", "start", "out", "n1", "in"));

        int initialEdgeCount = model.getConnections().size();

        // Add 1 new edge
        model.addConnection(new FlowConnection("c2", "n1", "out", "end", "in"));

        int finalEdgeCount = model.getConnections().size();
        int addedEdges = finalEdgeCount - initialEdgeCount;

        assertEquals(1, addedEdges, "Reported connections added must exactly match the before/after edge count delta");
    }
}
