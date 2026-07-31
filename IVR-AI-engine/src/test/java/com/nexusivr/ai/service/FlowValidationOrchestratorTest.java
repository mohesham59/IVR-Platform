package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FlowValidationOrchestratorTest {

    @Test
    void testOrchestratorConvergesToValid() {
        FlowModel model = new FlowModel();
        model.setName("Converge");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowMenu menuObj = new FlowMenu();
        menuObj.addChoice(new FlowChoice("key1", "Option 1", "n4"));
        menu.setMenu(menuObj);
        FlowNode orphan = new FlowNode("n3", FlowNodeType.PROMPT, "Orphan");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(orphan);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n4", "in"));

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged(), "Should converge to valid. Status: " + result.getMessage());
        assertTrue(result.getFinalValidation().isValid(), "Final validation should pass");
        assertTrue(result.getIterations() > 0, "Should have performed at least one iteration");
    }

    @Test
    void testOrchestratorStopsOnMaxIterations() {
        FlowModel model = new FlowModel();
        model.setName("Max Iter");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowMenu menuObj = new FlowMenu();
        menuObj.addChoice(new FlowChoice("key1", "Option", null));
        menu.setMenu(menuObj);
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator(
                new FlowModelValidator(),
                new FlowModelAutoRepair(),
                2
        );
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.getIterations() <= 2, "Should not exceed max iterations");
    }

    @Test
    void testOrchestratorHandlesAlreadyValidModel() {
        FlowModel model = new FlowModel();
        model.setName("Already Valid");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Hello");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        start.setPrompt(new FlowPrompt("Welcome"));
        prompt.setPrompt(new FlowPrompt("Proceed"));

        model.addNode(start);
        model.addNode(prompt);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "out", "n3", "in"));

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged());
        assertEquals(0, result.getIterations(), "No iterations needed for valid model");
        assertTrue(result.getFinalValidation().isValid());
    }

    @Test
    void testOrchestratorRepairsMissingEnd() {
        FlowModel model = new FlowModel();
        model.setName("Missing End");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Hello");

        model.addNode(start);
        model.addNode(prompt);
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged());
        assertTrue(result.getFinalValidation().isValid());
        assertTrue(result.getFinalValidation().getIssues().stream()
                .noneMatch(i -> i.getCode().equals("MISSING_END")));
    }

    @Test
    void testOrchestratorRepairsDuplicateIds() {
        FlowModel model = new FlowModel();
        model.setName("Dup IDs");

        FlowNode n1 = new FlowNode("dup", FlowNodeType.START, "Start");
        FlowNode n2 = new FlowNode("dup", FlowNodeType.PROMPT, "Prompt");

        model.addNode(n1);
        model.addNode(n2);

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged());
        assertTrue(result.getFinalValidation().isValid());
        List<String> ids = result.getFinalValidation().getIssues().stream()
                .filter(i -> "DUPLICATE_NODE_ID".equals(i.getCode()))
                .map(ValidationIssueDto::getNodeId)
                .collect(Collectors.toList());
        assertTrue(ids.isEmpty(), "No duplicate ID issues should remain");
    }

    @Test
    void testOrchestratorRepairsDisconnectedNodes() {
        FlowModel model = new FlowModel();
        model.setName("Disconnected");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Prompt");
        FlowNode orphan = new FlowNode("n3", FlowNodeType.PROMPT, "Orphan");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(prompt);
        model.addNode(orphan);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "out", "n4", "in"));

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged());
        assertTrue(result.getFinalValidation().isValid());
        assertTrue(result.getFinalValidation().getIssues().stream()
                .noneMatch(i -> "DISCONNECTED_NODE".equals(i.getCode())));
    }
}
