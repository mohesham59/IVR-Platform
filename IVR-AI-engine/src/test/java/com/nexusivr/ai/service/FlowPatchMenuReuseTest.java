package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Flow Patch & Repair Menu Node Reuse Test")
class FlowPatchMenuReuseTest {

    @Test
    @DisplayName("Assert that graph repair reuses an existing MENU node rather than creating a duplicate MENU node")
    void testExistingMenuNodeIsReusedToFixBranchingHub() {
        FlowModel model = new FlowModel();

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode menuNode = new FlowNode("main_menu", FlowNodeType.MENU, "Menu");
        menuNode.setMenu(new FlowMenu());

        FlowNode promptWithParallelEdges = new FlowNode("prompt_hub", FlowNodeType.PROMPT, "Informational Prompt");
        FlowNode billingNode = new FlowNode("billing", FlowNodeType.PROMPT, "Billing");
        FlowNode supportNode = new FlowNode("support", FlowNodeType.PROMPT, "Support");
        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(menuNode);
        model.addNode(promptWithParallelEdges);
        model.addNode(billingNode);
        model.addNode(supportNode);
        model.addNode(endNode);

        // Connections:
        // start -> main_menu
        // main_menu -> prompt_hub
        // prompt_hub has 2 parallel outgoing connections (creating a DELETED_BRANCHING_HUB issue)
        model.addConnection(new FlowConnection("c1", "start", "out", "main_menu", "in"));
        model.addConnection(new FlowConnection("c2", "main_menu", "key1", "prompt_hub", "in"));

        // Parallel edges causing branching hub issue on prompt_hub:
        model.addConnection(new FlowConnection("c3", "prompt_hub", "out", "billing", "in"));
        model.addConnection(new FlowConnection("c4", "prompt_hub", "out", "support", "in"));

        // Leaf connections
        model.addConnection(new FlowConnection("c5", "billing", "out", "end", "in"));
        model.addConnection(new FlowConnection("c6", "support", "out", "end", "in"));

        long initialMenuCount = model.getNodes().stream().filter(n -> n.getType() == FlowNodeType.MENU).count();
        assertEquals(1, initialMenuCount, "Flow initially contains exactly 1 MENU node");

        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repaired = repairer.repair(model);

        long finalMenuCount = repaired.getNodes().stream().filter(n -> n.getType() == FlowNodeType.MENU).count();

        assertEquals(1, finalMenuCount, "Existing MENU node must be reused to fix branching hub issue (no duplicate menu nodes created)");
    }
}
