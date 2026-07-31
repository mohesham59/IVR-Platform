package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.LlmProviderFactory;
import com.nexusivr.ai.ai.MockLlmClient;
import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowMenu;
import com.nexusivr.ai.model.flow.FlowChoice;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelFlowValidatorTest {

    @Test
    void testMenuNodeWithConnectionsButNoChoicesDoesNotTriggerEmptyMenu() {
        FlowModel model = new FlowModel();
        model.setName("Test Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode dept1 = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode dept2 = new FlowNode("n4", FlowNodeType.PROMPT, "Dept B");
        FlowNode end = new FlowNode("n5", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept1);
        model.addNode(dept2);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n2", "key2", "n4", "in"));
        model.addConnection(new FlowConnection("e4", "n3", "out", "n5", "in"));
        model.addConnection(new FlowConnection("e5", "n4", "out", "n5", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.isValid());
        assertFalse(response.getIssues().stream()
                .anyMatch(i -> "EMPTY_MENU".equals(i.getCode())),
                "Menu node with connections should not trigger EMPTY_MENU");
    }

    @Test
    void testMenuNodeWithNoConnectionsAndNoChoicesTriggersEmptyMenu() {
        FlowModel model = new FlowModel();
        model.setName("Test Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "menu", "out", "n3", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream()
                .anyMatch(i -> "EMPTY_MENU".equals(i.getCode())),
                "Menu node with no connections and no choices should trigger EMPTY_MENU");
    }

    @Test
    void testConsistentScoreBetweenGenerationAndValidation() {
        FlowModel model = new FlowModel();
        model.setName("Consistent Score Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode dept = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n3", "out", "n4", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse validationResponse = validator.validate(model);

        assertEquals(100, validationResponse.getScore(),
                "Valid flow with start, menu with choices, and end should score 100");
    }

    @Test
    void testScoreDeductedForOrphanNodes() {
        FlowModel model = new FlowModel();
        model.setName("Orphan Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode dept = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");
        FlowNode orphan = new FlowNode("n5", FlowNodeType.PROMPT, "Orphan");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept);
        model.addNode(end);
        model.addNode(orphan);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n3", "out", "n4", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response = validator.validate(model);

        assertEquals(85, response.getScore(),
                "Flow with one orphan node should score 85 (100 - 10 orphan - 5 unreachable)");
    }

    @Test
    void testScoreDeductedForEmptyMenu() {
        FlowModel model = new FlowModel();
        model.setName("Empty Menu Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode dept = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream()
                .anyMatch(i -> "EMPTY_MENU".equals(i.getCode())),
                "Flow with empty menu should trigger EMPTY_MENU warning");
        assertEquals(65, response.getScore(),
                "Flow with empty menu and disconnected dept/end should score 65 (100 - 5 empty menu - 10 orphan dept - 10 orphan end - 5 unreachable dept - 5 unreachable end)");
    }

    @Test
    void testValidatorScoreIsStableForValidFlow() {
        FlowModel model = new FlowModel();
        model.setName("Stable Score Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode dept = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n3", "out", "n4", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response1 = validator.validate(model);
        FlowValidationResponse response2 = validator.validate(model);

        assertEquals(response1.getScore(), response2.getScore(),
                "Same flow should produce identical scores on repeated validation");
    }
}
