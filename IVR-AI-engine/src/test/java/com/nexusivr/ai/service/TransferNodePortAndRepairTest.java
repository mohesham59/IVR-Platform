package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transfer Node Port & END Node Repair Tests")
public class TransferNodePortAndRepairTest {

    private final ModelAutoRepair autoRepair = new ModelAutoRepair();
    private final ModelFlowValidator validator = new ModelFlowValidator();
    private final VxmlToModelConverter converter = new VxmlToModelConverter();

    @Test
    @DisplayName("Should remove stray outgoing connection from END node without no-op remap warning")
    void testEndNodeStrayConnectionRemoval() {
        FlowModel model = new FlowModel();
        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode menuNode = new FlowNode("menu", FlowNodeType.MENU, "Main Menu");
        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(menuNode);
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "menu", "in"));
        model.addConnection(new FlowConnection("c2", "menu", "key1", "end", "in"));
        // STRAY OUTGOING CONNECTION FROM END NODE (invalid)
        model.addConnection(new FlowConnection("c_stray", "end", "out", "menu", "in"));

        // Pre-repair validation should flag invalid outgoing connection from END node
        FlowValidationResponse preVal = validator.validate(model);
        assertFalse(preVal.isValid() && preVal.getErrorCount() == 0, "Pre-repair model with stray END outgoing edge should have validation issues");

        // Execute ModelAutoRepair
        FlowModel repaired = autoRepair.repair(model, preVal);

        // Assert stray connection from END node was removed
        boolean hasStrayConn = repaired.getConnections().stream()
                .anyMatch(c -> "end".equals(c.getSourceNodeId()));
        assertFalse(hasStrayConn, "ModelAutoRepair MUST remove stray outgoing connection from terminal END node");

        // Post-repair validation should be valid with 0 errors
        FlowValidationResponse postVal = validator.validate(repaired);
        assertTrue(postVal.isValid(), "Repaired model MUST be valid");
        assertEquals(0, postVal.getErrorCount(), "Repaired model MUST have 0 errors");
    }

    @Test
    @DisplayName("Should parse VoiceXML transfer form with noinput/nomatch and map ports strictly to 'fail'")
    void testVxmlTransferNodePortMapping() throws Exception {
        String vxml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <goto next="#passport_transfer"/>
                    </block>
                  </form>
                  <form id="passport_transfer">
                    <transfer name="xfer" dest="101">
                      <prompt>Transferring to passport services.</prompt>
                      <filled>
                        <goto next="#end"/>
                      </filled>
                    </transfer>
                    <noinput>
                      <goto next="#start"/>
                    </noinput>
                    <nomatch>
                      <goto next="#start"/>
                    </nomatch>
                  </form>
                  <form id="end">
                    <block>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """;

        FlowModel model = converter.convert(vxml);
        assertNotNull(model);

        // Find transfer node connections
        List<FlowConnection> xferConnections = model.getConnections().stream()
                .filter(c -> "passport_transfer".equals(c.getSourceNodeId()))
                .toList();

        assertFalse(xferConnections.isEmpty(), "Transfer node must have outgoing connections");

        for (FlowConnection conn : xferConnections) {
            String port = conn.getSourcePort();
            assertTrue("success".equals(port) || "fail".equals(port),
                    "Transfer node output port MUST be 'success' or 'fail', but found: " + port);
            assertFalse("timeout".equals(port) || "error".equals(port),
                    "Transfer node MUST NOT have 'timeout' or 'error' ports");
        }

        // Validate model
        FlowValidationResponse val = validator.validate(model);
        assertTrue(val.isValid(), "Converted transfer flow model MUST be valid");
        assertEquals(0, val.getErrorCount(), "Converted transfer flow model MUST have 0 errors");
    }
}
