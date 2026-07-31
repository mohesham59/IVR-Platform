package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FlowModelValidatorTest {

    @Test
    void testValidFlowScores100() {
        FlowModel model = new FlowModel();
        model.setName("Valid Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowNode prompt = new FlowNode("n3", FlowNodeType.PROMPT, "Dept A");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        start.setPrompt(new FlowPrompt("Welcome"));
        menu.setPrompt(new FlowPrompt("Choose an option"));
        prompt.setPrompt(new FlowPrompt("You chose dept A"));

        model.addNode(start);
        model.addNode(menu);
        model.addNode(prompt);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n3", "out", "n4", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.isValid(), "Valid flow should pass validation: " + response.getIssues());
        assertEquals(100, response.getScore());
    }

    @Test
    void testMultipleStartsDetected() {
        FlowModel model = new FlowModel();
        model.setName("Multi Start");

        FlowNode start1 = new FlowNode("n1", FlowNodeType.START, "Start 1");
        FlowNode start2 = new FlowNode("n2", FlowNodeType.START, "Start 2");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start1);
        model.addNode(start2);
        model.addNode(end);

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "MULTIPLE_STARTS".equals(i.getCode())));
    }

    @Test
    void testMissingStartDetected() {
        FlowModel model = new FlowModel();
        model.setName("No Start");

        FlowNode prompt = new FlowNode("n1", FlowNodeType.PROMPT, "Hello");
        FlowNode end = new FlowNode("n2", FlowNodeType.END, "End");

        model.addNode(prompt);
        model.addNode(end);

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "MISSING_START".equals(i.getCode())));
    }

    @Test
    void testMissingEndDetected() {
        FlowModel model = new FlowModel();
        model.setName("No End");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Hello");

        model.addNode(start);
        model.addNode(prompt);
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "MISSING_END".equals(i.getCode())));
    }

    @Test
    void testDuplicateIdsDetected() {
        FlowModel model = new FlowModel();
        model.setName("Dup IDs");

        FlowNode n1 = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode n2 = new FlowNode("n1", FlowNodeType.PROMPT, "Prompt");

        model.addNode(n1);
        model.addNode(n2);

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "DUPLICATE_NODE_ID".equals(i.getCode())));
    }

    @Test
    void testDisconnectedNodeDetected() {
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

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "DISCONNECTED_NODE".equals(i.getCode()) && "n3".equals(i.getNodeId())));
    }

    @Test
    void testCycleDetected() {
        FlowModel model = new FlowModel();
        model.setName("Cycle");

        FlowNode n1 = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode n2 = new FlowNode("n2", FlowNodeType.PROMPT, "A");
        FlowNode n3 = new FlowNode("n3", FlowNodeType.PROMPT, "B");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(n1);
        model.addNode(n2);
        model.addNode(n3);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "out", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n3", "out", "n2", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "CYCLE_DETECTED".equals(i.getCode())));
    }

    @Test
    void testDeadEndDetected() {
        FlowModel model = new FlowModel();
        model.setName("Dead End");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Dead");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(prompt);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "DEAD_END".equals(i.getCode()) && "n2".equals(i.getNodeId())));
    }

    @Test
    void testMissingPromptDetected() {
        FlowModel model = new FlowModel();
        model.setName("Missing Prompt");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(prompt);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "out", "n3", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream().anyMatch(i -> "MISSING_PROMPT".equals(i.getCode())));
    }

    @Test
    void testInvalidMenuDetected() {
        FlowModel model = new FlowModel();
        model.setName("Invalid Menu");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "INVALID_MENU".equals(i.getCode())));
    }

    @Test
    void testPortMismatchDetected() {
        FlowModel model = new FlowModel();
        model.setName("Port Mismatch");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "invalid_port", "n3", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode())));
    }

    @Test
    void testBrokenEdgeDetected() {
        FlowModel model = new FlowModel();
        model.setName("Broken Edge");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode end = new FlowNode("n2", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "nonexistent", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertFalse(response.isValid());
        assertTrue(response.getIssues().stream().anyMatch(i -> "BROKEN_EDGE".equals(i.getCode())));
    }

    @Test
    void testDeterministicScoring() {
        FlowModel model = new FlowModel();
        model.setName("Score Stable");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode dept = new FlowNode("n3", FlowNodeType.PROMPT, "Dept");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(dept);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse r1 = validator.validate(model);
        FlowValidationResponse r2 = validator.validate(model);

        assertEquals(r1.getScore(), r2.getScore(), "Score must be deterministic");
    }

    @Test
    void testConvergingPathsWarning() {
        FlowModel model = new FlowModel();
        model.setName("Converging Paths");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End Call");

        start.setPrompt(new FlowPrompt("Start"));
        menu.setPrompt(new FlowPrompt("Menu"));

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n3", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n2", "key2", "n3", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream().anyMatch(i -> "CONVERGING_PATHS".equals(i.getCode())));
    }

    @Test
    void testSmartDeleteMenuNodeTriggersConvergingPathsWarning() {
        FlowModel model = new FlowModel();
        model.setName("Deleted Hub Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start Call");
        FlowNode greeting = new FlowNode("n2", FlowNodeType.PROMPT, "Welcome Greeting");
        FlowNode dept1 = new FlowNode("n3", FlowNodeType.PROMPT, "Billing Support");
        FlowNode dept2 = new FlowNode("n4", FlowNodeType.PROMPT, "Tech Support");
        FlowNode dept3 = new FlowNode("n5", FlowNodeType.PROMPT, "Plan Change");
        FlowNode dept4 = new FlowNode("n6", FlowNodeType.PROMPT, "Appointments");
        FlowNode dept5 = new FlowNode("n7", FlowNodeType.PROMPT, "Pharmacy");
        FlowNode dept6 = new FlowNode("n8", FlowNodeType.PROMPT, "Outage");
        FlowNode end = new FlowNode("n9", FlowNodeType.END, "End Call");

        start.setPrompt(new FlowPrompt("Start"));
        greeting.setPrompt(new FlowPrompt("Welcome to support"));
        dept1.setPrompt(new FlowPrompt("Billing"));
        dept2.setPrompt(new FlowPrompt("Tech"));
        dept3.setPrompt(new FlowPrompt("Plan"));
        dept4.setPrompt(new FlowPrompt("Appointments"));
        dept5.setPrompt(new FlowPrompt("Pharmacy"));
        dept6.setPrompt(new FlowPrompt("Outage"));

        model.addNode(start);
        model.addNode(greeting);
        model.addNode(dept1);
        model.addNode(dept2);
        model.addNode(dept3);
        model.addNode(dept4);
        model.addNode(dept5);
        model.addNode(dept6);
        model.addNode(end);

        // Smart-delete simulation: Menu was deleted, auto-reconnecting greeting to all 6 department nodes
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "out", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n2", "out", "n4", "in"));
        model.addConnection(new FlowConnection("e4", "n2", "out", "n5", "in"));
        model.addConnection(new FlowConnection("e5", "n2", "out", "n6", "in"));
        model.addConnection(new FlowConnection("e6", "n2", "out", "n7", "in"));
        model.addConnection(new FlowConnection("e7", "n2", "out", "n8", "in"));

        model.addConnection(new FlowConnection("e8", "n3", "out", "n9", "in"));
        model.addConnection(new FlowConnection("e9", "n4", "out", "n9", "in"));
        model.addConnection(new FlowConnection("e10", "n5", "out", "n9", "in"));
        model.addConnection(new FlowConnection("e11", "n6", "out", "n9", "in"));
        model.addConnection(new FlowConnection("e12", "n7", "out", "n9", "in"));
        model.addConnection(new FlowConnection("e13", "n8", "out", "n9", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.getIssues().stream().anyMatch(i -> "DELETED_BRANCHING_HUB".equals(i.getCode())),
                "Validator must issue DELETED_BRANCHING_HUB warning when Menu node is deleted and 6 paths auto-connect to a prompt node");
        assertTrue(response.getIssues().stream().anyMatch(i -> "CONVERGING_PATHS".equals(i.getCode())),
                "Validator must issue CONVERGING_PATHS warning when 6 department paths converge into End Call");
    }
}
