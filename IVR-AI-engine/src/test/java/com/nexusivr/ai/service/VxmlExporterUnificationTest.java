package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("VXML Exporter Unification Tests")
public class VxmlExporterUnificationTest {

    @Test
    @DisplayName("Should produce identical VXML from direct FlowModel and deserialized React Flow JSON (Unification validation)")
    void testVxmlUnificationIdenticalOutput() {
        // 1. Define React Flow JSON representing a flow with a start, greeting, menu, transfer, and end node
        String reactFlowJson = """
                {
                  "name": "Unified IVR Flow",
                  "nodes": [
                    {"id": "start", "type": "start", "title": "Start"},
                    {"id": "welcome", "type": "greeting", "title": "Welcome Greeting", "subtitle": "welcome.wav"},
                    {"id": "menu", "type": "dtmf_menu", "title": "Main Menu", "subtitle": "Please select an option.", "ports": [
                      {"id": "key1", "label": "Support"},
                      {"id": "key2", "label": "Sales"}
                    ]},
                    {"id": "support_xfer", "type": "transfer", "title": "Support Agent Transfer", "dest": "1001"},
                    {"id": "sales_xfer", "type": "transfer", "title": "Sales Agent Transfer", "transferDestination": "1002"},
                    {"id": "hangup", "type": "end", "title": "End Call", "subtitle": "Goodbye."}
                  ],
                  "edges": [
                    {"id": "e1", "source": "start", "target": "welcome"},
                    {"id": "e2", "source": "welcome", "target": "menu"},
                    {"id": "e3", "source": "menu", "sourcePort": "key1", "target": "support_xfer"},
                    {"id": "e4", "source": "menu", "sourcePort": "key2", "target": "sales_xfer"},
                    {"id": "e5", "source": "support_xfer", "target": "hangup"},
                    {"id": "e6", "source": "sales_xfer", "target": "hangup"}
                  ]
                }
                """;

        // 2. Convert React Flow JSON to FlowModel (this mimics the Export/Copy path)
        FlowModel jsonModel = FlowContextService.convertJsonToModel(reactFlowJson);
        assertNotNull(jsonModel);

        // Verify properties were parsed correctly
        assertEquals("Unified IVR Flow", jsonModel.getName());
        assertEquals(6, jsonModel.getNodes().size());
        assertEquals(6, jsonModel.getConnections().size());

        // Validate transfer destination mapping in convertJsonToModel
        FlowNode supportNode = jsonModel.getNodes().stream().filter(n -> "support_xfer".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(supportNode);
        assertNotNull(supportNode.getTransfer());
        assertEquals("1001", supportNode.getTransfer().getDestination());

        FlowNode salesNode = jsonModel.getNodes().stream().filter(n -> "sales_xfer".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(salesNode);
        assertNotNull(salesNode.getTransfer());
        assertEquals("1002", salesNode.getTransfer().getDestination());

        // 3. Render VXML from the deserialized FlowModel (Export / Copy VXML surface)
        ModelToVxmlExporter exporter = new ModelToVxmlExporter();
        String exportedVxml = exporter.export(jsonModel);

        // 4. Manually construct programmatically identical FlowModel (representing Publish path)
        FlowModel publishModel = new FlowModel();
        publishModel.setName("Unified IVR Flow");
        publishModel.setDescription("Imported from React Flow JSON"); // convertJsonToModel sets this description

        publishModel.addNode(new FlowNode("start", FlowNodeType.START, "Start"));

        FlowNode welcome = new FlowNode("welcome", FlowNodeType.PROMPT, "Welcome Greeting");
        welcome.setSubtitle("welcome.wav");
        FlowPrompt welcomePrompt = new FlowPrompt();
        welcomePrompt.setText("welcome.wav");
        welcome.setPrompt(welcomePrompt);
        publishModel.addNode(welcome);

        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Main Menu");
        menu.setSubtitle("Please select an option.");
        FlowPrompt menuPrompt = new FlowPrompt();
        menuPrompt.setText("Please select an option.");
        menu.setPrompt(menuPrompt);
        FlowMenu flowMenu = new FlowMenu();
        flowMenu.addChoice(new FlowChoice("key1", "Support", ""));
        flowMenu.addChoice(new FlowChoice("key2", "Sales", ""));
        menu.setMenu(flowMenu);
        publishModel.addNode(menu);

        FlowNode support = new FlowNode("support_xfer", FlowNodeType.TRANSFER, "Support Agent Transfer");
        support.setTransfer(new FlowTransfer("1001"));
        publishModel.addNode(support);

        FlowNode sales = new FlowNode("sales_xfer", FlowNodeType.TRANSFER, "Sales Agent Transfer");
        sales.setTransfer(new FlowTransfer("1002"));
        publishModel.addNode(sales);

        FlowNode hangup = new FlowNode("hangup", FlowNodeType.END, "End Call");
        hangup.setSubtitle("Goodbye.");
        FlowPrompt hangupPrompt = new FlowPrompt();
        hangupPrompt.setText("Goodbye.");
        hangup.setPrompt(hangupPrompt);
        publishModel.addNode(hangup);

        publishModel.addConnection(new FlowConnection("e1", "start", "out", "welcome", "in"));
        publishModel.addConnection(new FlowConnection("e2", "welcome", "out", "menu", "in"));
        publishModel.addConnection(new FlowConnection("e3", "menu", "key1", "support_xfer", "in"));
        publishModel.addConnection(new FlowConnection("e4", "menu", "key2", "sales_xfer", "in"));
        publishModel.addConnection(new FlowConnection("e5", "support_xfer", "out", "hangup", "in"));
        publishModel.addConnection(new FlowConnection("e6", "sales_xfer", "out", "hangup", "in"));

        // 5. Render VXML from programmatically identical Publish model
        String publishedVxml = exporter.export(publishModel);

        // 6. Assert both VXMLs are identical
        assertEquals(publishedVxml, exportedVxml);
    }
}
