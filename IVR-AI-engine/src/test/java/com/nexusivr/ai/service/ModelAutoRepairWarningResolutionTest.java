package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelAutoRepair 100% Score & Warning Resolution Tests")
public class ModelAutoRepairWarningResolutionTest {

    private final ModelAutoRepair autoRepair = new ModelAutoRepair();
    private final ModelFlowValidator validator = new ModelFlowValidator();

    @Test
    @DisplayName("Should resolve orphan TRANSFER nodes, missing transfer destinations, and missing menu fallbacks to achieve score 100")
    void testCompleteWarningResolutionAnd100Score() {
        FlowModel model = new FlowModel();

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode menuNode = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu");
        menuNode.setMenu(new FlowMenu());
        menuNode.getMenu().addChoice(new FlowChoice("key1", "Claims", "xfer_claims"));
        menuNode.getMenu().addChoice(new FlowChoice("key2", "Agent", "xfer_agent"));

        // TRANSFER node 1: no destination, no outgoing connection
        FlowNode xferClaims = new FlowNode("xfer_claims", FlowNodeType.TRANSFER, "Transfer to Claims");

        // TRANSFER node 2: no destination, no outgoing connection
        FlowNode xferAgent = new FlowNode("xfer_agent", FlowNodeType.TRANSFER, "Transfer to Agent");

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(menuNode);
        model.addNode(xferClaims);
        model.addNode(xferAgent);
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "main_menu", "in"));
        model.addConnection(new FlowConnection("c2", "main_menu", "key1", "xfer_claims", "in"));
        model.addConnection(new FlowConnection("c3", "main_menu", "key2", "xfer_agent", "in"));

        // Validate before repair: should have warnings for missing destinations, missing menu timeout fallback, missing leaf connections
        FlowValidationResponse preVal = validator.validate(model);
        assertTrue(preVal.getWarningCount() > 0, "Pre-repair model must have warnings");
        assertTrue(preVal.getScore() < 100, "Pre-repair score must be less than 100");

        // Run ModelAutoRepair
        FlowModel repaired = autoRepair.repair(model, preVal);

        // Validate after repair
        FlowValidationResponse postVal = validator.validate(repaired);
        assertTrue(postVal.isValid(), "Repaired model MUST be valid");


        assertEquals(0, postVal.getErrorCount(), "Repaired model MUST have 0 errors");

        // Assert TRANSFER destination placeholders were set
        assertNotNull(xferClaims.getTransfer());
        assertEquals("101", xferClaims.getTransfer().getDestination());
        assertNotNull(xferAgent.getTransfer());
        assertEquals("102", xferAgent.getTransfer().getDestination());

        // Assert TRANSFER nodes were connected to End Call on port 'fail'
        boolean claimsConnected = repaired.getConnections().stream()
                .anyMatch(c -> "xfer_claims".equals(c.getSourceNodeId()) && "end".equals(c.getTargetNodeId()) && "fail".equals(c.getSourcePort()));
        assertTrue(claimsConnected, "TRANSFER node 1 MUST be connected to End node on port 'fail'");

        boolean agentConnected = repaired.getConnections().stream()
                .anyMatch(c -> "xfer_agent".equals(c.getSourceNodeId()) && "end".equals(c.getTargetNodeId()) && "fail".equals(c.getSourcePort()));
        assertTrue(agentConnected, "TRANSFER node 2 MUST be connected to End node on port 'fail'");

        // Assert Menu node has timeout fallback connection to End Call
        boolean menuTimeoutConnected = repaired.getConnections().stream()
                .anyMatch(c -> "main_menu".equals(c.getSourceNodeId()) && "end".equals(c.getTargetNodeId()) && "timeout".equals(c.getSourcePort()));
        assertTrue(menuTimeoutConnected, "MENU node MUST have timeout fallback connection to End node");

        // Assert final validation score is 100 and warnings count is 0
        assertEquals(100, postVal.getScore(), "Repaired flow score MUST be 100%");
        assertEquals(0, postVal.getWarningCount(), "Repaired flow MUST have 0 remaining warnings");
    }
}
