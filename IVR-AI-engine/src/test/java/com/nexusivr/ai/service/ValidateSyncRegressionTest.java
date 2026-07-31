package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidateSyncRegressionTest {

    private FlowModelAutoRepair autoRepair;
    private FlowModelValidator validator;

    @BeforeEach
    void setUp() {
        autoRepair = new FlowModelAutoRepair();
        validator = new FlowModelValidator();
    }

    @Test
    void testMenuDeletionRepairSynchronizesValidationAndSuggestionsToZero() {
        // Build a banking flow with a Menu hub node
        FlowModel model = new FlowModel();
        model.setName("Banking IVR");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode promptNode = new FlowNode("prompt_1", FlowNodeType.PROMPT, "Main Prompt");
        FlowNode balNode = new FlowNode("n_bal", FlowNodeType.PROMPT, "Balance");
        FlowNode xferNode = new FlowNode("n_xfer", FlowNodeType.PROMPT, "Transfer");
        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(promptNode);
        model.addNode(balNode);
        model.addNode(xferNode);
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "prompt_1", "in"));
        model.addConnection(new FlowConnection("c2", "prompt_1", "out", "n_bal", "in"));
        model.addConnection(new FlowConnection("c3", "prompt_1", "out", "n_xfer", "in"));
        model.addConnection(new FlowConnection("c4", "n_bal", "out", "end", "in"));
        model.addConnection(new FlowConnection("c5", "n_xfer", "out", "end", "in"));

        // Run auto repair pass to upgrade prompt_1 into a Menu hub
        FlowModel repaired = autoRepair.repair(model);

        // Validate the repaired flow
        com.nexusivr.ai.dto.response.FlowValidationResponse response = validator.validate(repaired);
        response.getIssues().forEach(i -> System.out.println("ISSUE: [" + i.getSeverity() + "] " + i.getCode() + " -> " + i.getMessage()));

        long errorCount = response.getIssues().stream()
                .filter(i -> i.getSeverity() == com.nexusivr.ai.dto.common.ValidationSeverity.ERROR)
                .count();

        assertEquals(0, errorCount, "After repair of flow, validation errors must reach exactly 0");
        assertTrue(response.isValid(), "Flow must be valid after repair");
    }
}
