package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FlowModelAutoRepairTest {

    @Test
    void testRepairMissingEndNode() {
        FlowModel model = new FlowModel();
        model.setName("No End");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n2", FlowNodeType.PROMPT, "Hello");

        model.addNode(start);
        model.addNode(prompt);
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        assertTrue(repaired.getNodes().stream().anyMatch(n -> n.getType() == FlowNodeType.END || n.getType() == FlowNodeType.DISCONNECT),
                "Repaired model should have an END node");
    }

    @Test
    void testRepairOrphanNode() {
        FlowModel model = new FlowModel();
        model.setName("Orphan");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode orphan = new FlowNode("n3", FlowNodeType.PROMPT, "Orphan");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(orphan);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "key1", "n4", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        boolean orphanConnected = repaired.getConnections().stream()
                .anyMatch(c -> c.getSourceNodeId().equals("n3"));
        assertTrue(orphanConnected, "Orphan node should be reconnected");
    }

    @Test
    void testRepairDuplicateIds() {
        FlowModel model = new FlowModel();
        model.setName("Dup IDs");

        FlowNode n1 = new FlowNode("same", FlowNodeType.START, "Start");
        FlowNode n2 = new FlowNode("same", FlowNodeType.PROMPT, "Prompt");

        model.addNode(n1);
        model.addNode(n2);

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        List<String> ids = repaired.getNodes().stream().map(FlowNode::getId).collect(Collectors.toList());
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size(), "All node IDs should be unique after repair");
    }

    @Test
    void testRepairInvalidMenuOptions() {
        FlowModel model = new FlowModel();
        model.setName("Invalid Menu");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        menu.setMenu(new FlowMenu());
        FlowNode end = new FlowNode("n3", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        FlowChoice c1 = new FlowChoice("key1", "Option 1", "n3");
        c1.setDtmf("a");
        menu.getMenu().addChoice(c1);
        FlowChoice c2 = new FlowChoice("key2", "Option 2", "n3");
        c2.setDtmf("1");
        c2.setTargetNodeId("nonexistent");
        menu.getMenu().addChoice(c2);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        FlowNode repairedMenu = repaired.getNode("n2");
        assertNotNull(repairedMenu);
        List<FlowChoice> choices = repairedMenu.getMenu().getChoices();
        assertTrue(choices.stream().allMatch(c -> c.getDtmf() != null && c.getDtmf().matches("\\d")),
                "All menu options should have valid DTMF digits after repair");
    }

    @Test
    void testRepairDeadEnds() {
        FlowModel model = new FlowModel();
        model.setName("Dead Ends");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode prompt1 = new FlowNode("n2", FlowNodeType.PROMPT, "A");
        FlowNode prompt2 = new FlowNode("n3", FlowNodeType.PROMPT, "B");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(prompt1);
        model.addNode(prompt2);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        boolean n2Connected = repaired.getConnections().stream().anyMatch(c -> "n2".equals(c.getSourceNodeId()));
        boolean n3Connected = repaired.getConnections().stream().anyMatch(c -> "n3".equals(c.getSourceNodeId()));
        assertTrue(n2Connected || n3Connected, "Dead-end nodes should be connected after repair");
    }

    @Test
    void testRepairMissingEdges() {
        FlowModel model = new FlowModel();
        model.setName("Missing Edges");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("n2", FlowNodeType.MENU, "Menu");
        FlowNode orphan = new FlowNode("n3", FlowNodeType.PROMPT, "Orphan");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(orphan);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        long edgeCount = repaired.getConnections().size();
        assertTrue(edgeCount >= 2, "Should have at least 2 connections after repair, got: " + edgeCount);
    }

    @Test
    void testRepairIdempotentOnSecondPass() {
        FlowModel model = new FlowModel();
        model.setName("Idempotent");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start");
        FlowNode end = new FlowNode("n2", FlowNodeType.END, "End");

        model.addNode(start);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired1 = repairer.repair(model);
        FlowModel repaired2 = repairer.repair(repaired1);

        assertEquals(repaired1.getNodes().size(), repaired2.getNodes().size());
        assertEquals(repaired1.getConnections().size(), repaired2.getConnections().size());
    }
}
