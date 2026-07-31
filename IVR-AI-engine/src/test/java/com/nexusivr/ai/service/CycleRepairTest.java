package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cycle Repair & Orchestration Convergence Tests")
class CycleRepairTest {

    @Test
    @DisplayName("Should break cycle in FlowModel and converge cleanly")
    void testBreakCycleInFlowModel() {
        FlowModel model = new FlowModel();
        model.setName("Cyclic Flow Test");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start Call");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Welcome Prompt");
        FlowNode menu = new FlowNode("n3", FlowNodeType.PROMPT, "Main Prompt");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(prompt);
        model.addNode(menu);
        model.addNode(end);

        // Connections: n1 -> n2 -> n3 -> n2 (cycle between n2 and n3)
        model.addConnection(new FlowConnection("c1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("c2", "n2", "out", "n3", "in"));
        model.addConnection(new FlowConnection("c3", "n3", "out", "n2", "in")); // cycle back-edge

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse initialVal = validator.validate(model);
        assertFalse(initialVal.isValid(), "Initial model with cycle must be invalid");
        assertTrue(initialVal.getIssues().stream().anyMatch(i -> "CYCLE_DETECTED".equals(i.getCode())),
                "Initial validation must detect CYCLE_DETECTED issue");

        FlowValidationOrchestrator orchestrator = new FlowValidationOrchestrator();
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(model);

        assertTrue(result.isConverged(), "Orchestrator should converge cleanly after breaking cycle");
        assertTrue(result.getIterations() <= 3, "Orchestrator should converge within 3 iterations, got " + result.getIterations());
        assertFalse(result.getFinalValidation().getIssues().stream().anyMatch(i -> "CYCLE_DETECTED".equals(i.getCode())),
                "Repaired model must have 0 CYCLE_DETECTED issues");
    }
}
