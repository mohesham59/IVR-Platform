package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateMenuFixTest {

    private FlowModelAutoRepair autoRepair;

    @BeforeEach
    void setUp() {
        autoRepair = new FlowModelAutoRepair();
    }

    @Test
    void testBankingFlowRepairProducesExactlyOneMenuNode() {
        // Build a banking flow shape: Start -> multiple prompt/transfer nodes -> End (missing a menu hub)
        FlowModel model = new FlowModel();
        model.setName("Banking IVR");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode promptNode = new FlowNode("prompt_1", FlowNodeType.PROMPT, "Welcome Prompt");
        FlowNode acctNode = new FlowNode("n1", FlowNodeType.PROMPT, "Account Balance");
        FlowNode cardNode = new FlowNode("n2", FlowNodeType.TRANSFER, "Card Support");
        FlowNode loanNode = new FlowNode("n3", FlowNodeType.TRANSFER, "Loan Desk");
        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(promptNode);
        model.addNode(acctNode);
        model.addNode(cardNode);
        model.addNode(loanNode);
        model.addNode(endNode);

        // Multiple parallel unbranched edges from prompt_1 to acct, card, loan (collapsed branching hub)
        model.addConnection(new FlowConnection("c1", "start", "out", "prompt_1", "in"));
        model.addConnection(new FlowConnection("c2", "prompt_1", "out", "n1", "in"));
        model.addConnection(new FlowConnection("c3", "prompt_1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("c4", "prompt_1", "out", "n3", "in"));
        model.addConnection(new FlowConnection("c5", "n1", "out", "end", "in"));
        model.addConnection(new FlowConnection("c6", "n2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c7", "n3", "out", "end", "in"));

        // Run auto repair pass
        FlowModel repaired = autoRepair.repair(model);

        // Assert exactly ONE MENU node is present in the repaired model
        long menuCount = repaired.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.MENU)
                .count();

        assertEquals(1, menuCount, "Repaired flow graph must contain EXACTLY ONE Menu hub node, not duplicate menu nodes");
    }
}
