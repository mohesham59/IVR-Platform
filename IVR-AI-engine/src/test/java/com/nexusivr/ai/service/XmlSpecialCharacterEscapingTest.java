package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XmlSpecialCharacterEscapingTest {

    @TempDir
    Path tempScenariosDir;

    private ModelToVxmlExporter exporter;
    private VxmlToModelConverter converter;
    private FlowPublishService publishService;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        converter = new VxmlToModelConverter();
        FlowModelValidator validator = new FlowModelValidator();
        publishService = new FlowPublishService(exporter, validator, tempScenariosDir.toString());
    }

    @Test
    @DisplayName("XML special characters (&, <, >, \", ') in prompts and choice labels are properly escaped and round-trip losslessly")
    void testXmlSpecialCharactersEscapedAndParsedLosslessly() throws Exception {
        FlowModel model = new FlowModel();
        model.setName("Special Characters & Entity Test");

        // Node with prompt containing &, <, >, ", '
        String specialPromptText = "Welcome to Q&A & Help. Please choose <Options> or \"Specials\" for 50's Diner.";
        FlowNode startNode = new FlowNode("n_start", FlowNodeType.START, "Start Node");
        startNode.setPrompt(new FlowPrompt(specialPromptText));
        model.addNode(startNode);

        // Menu node with choice labels containing &, <, >, ", '
        FlowNode menuNode = new FlowNode("n_menu", FlowNodeType.MENU, "Main Menu");
        menuNode.setPrompt(new FlowPrompt("Press 1 for Hours & Location, 2 for Terms & Conditions, 3 for 'Special Deals' & \"Offers\"."));
        FlowMenu menu = new FlowMenu();

        String choice1Label = "3-Hours & Location";
        String choice2Label = "Terms <Terms & Conditions>";
        String choice3Label = "\"Special Deals\" & 'Offers'";

        menu.addChoice(new FlowChoice("key1", choice1Label, "n_hours"));
        menu.addChoice(new FlowChoice("key2", choice2Label, "n_end"));
        menu.addChoice(new FlowChoice("key3", choice3Label, "n_end"));
        menuNode.setMenu(menu);
        model.addNode(menuNode);

        FlowNode hoursNode = new FlowNode("n_hours", FlowNodeType.PROMPT, "Hours & Location");
        hoursNode.setPrompt(new FlowPrompt("We are open 9am to 9pm & located at Main St."));
        model.addNode(hoursNode);

        FlowNode endNode = new FlowNode("n_end", FlowNodeType.END, "End Call");
        endNode.setPrompt(new FlowPrompt("Thank you & goodbye!"));
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "n_start", "out", "n_menu", "in"));
        model.addConnection(new FlowConnection("c2", "n_menu", "key1", "n_hours", "in"));
        model.addConnection(new FlowConnection("c3", "n_menu", "key2", "n_end", "in"));
        model.addConnection(new FlowConnection("c4", "n_menu", "key3", "n_end", "in"));

        // 1. Export model to VXML string
        String vxml = exporter.export(model);
        assertNotNull(vxml);
        assertTrue(vxml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(vxml.contains("<vxml version=\"2.1\""));

        // 2. Assert raw special characters do NOT exist unescaped inside XML text/attributes
        assertFalse(vxml.contains("3-Hours & Location"), "Raw '&' must not appear unescaped in VXML");
        assertTrue(vxml.contains("3-Hours &amp; Location"), "Bare '&' must be escaped as '&amp;'");

        assertTrue(vxml.contains("Terms &lt;Terms &amp; Conditions&gt;"), "Special chars < and > must be escaped as &lt; and &gt;");
        assertTrue(vxml.contains("&quot;Special Deals&quot; &amp; &apos;Offers&apos;"), "Quotes must be escaped in choice text");

        // 3. Parse VXML back to FlowModel via VxmlToModelConverter
        FlowModel parsedModel = converter.convert(vxml);
        assertNotNull(parsedModel, "VxmlToModelConverter must parse the XML successfully without SAXException");

        // 4. Assert recovered prompt text and choice labels match original unescaped strings
        FlowNode parsedStart = parsedModel.getNode("n_start");
        assertNotNull(parsedStart);
        assertEquals(specialPromptText, parsedStart.getPrompt().getText(),
                "Parsed prompt text must un-escape back to exact original string");

        FlowNode parsedMenu = parsedModel.getNode("n_menu");
        assertNotNull(parsedMenu);
        assertNotNull(parsedMenu.getMenu());
        assertEquals(3, parsedMenu.getMenu().getChoices().size());

        FlowChoice parsedChoice1 = parsedMenu.getMenu().getChoices().get(0);
        assertEquals(choice1Label, parsedChoice1.getLabel(),
                "Choice 1 label '3-Hours & Location' must be recovered exactly");

        FlowChoice parsedChoice2 = parsedMenu.getMenu().getChoices().get(1);
        assertEquals(choice2Label, parsedChoice2.getLabel(),
                "Choice 2 label must be recovered exactly with <, >, &");

        FlowChoice parsedChoice3 = parsedMenu.getMenu().getChoices().get(2);
        assertEquals(choice3Label, parsedChoice3.getLabel(),
                "Choice 3 label must be recovered exactly with quotes");
    }

    @Test
    @DisplayName("Publishing a Restaurant IVR flow containing '3-Hours & Location' choice succeeds cleanly")
    void testPublishRestaurantFlowWithAmpersandChoiceSucceeds() throws IOException {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "rest_flow_101";
        String flowName = "Restaurant IVR & Bistro";

        String reactFlowJson = """
            {
              "name": "Restaurant IVR & Bistro",
              "nodes": [
                { "id": "n_start", "type": "start", "title": "Start" },
                { "id": "n_greeting", "type": "greeting", "title": "Welcome Greeting", "subtitle": "Welcome to Bistro & Cafe" },
                {
                  "id": "n_menu",
                  "type": "dtmf_menu",
                  "title": "Main Menu & Options",
                  "ports": [
                    { "id": "key1", "label": "1-Reservations & Tables" },
                    { "id": "key2", "label": "2-Takeout & Delivery" },
                    { "id": "key3", "label": "3-Hours & Location" }
                  ]
                },
                { "id": "n_hours", "type": "prompt", "title": "Hours & Location Info" },
                { "id": "n_end", "type": "end", "title": "End Call" }
              ],
              "edges": [
                { "id": "e1", "source": "n_start", "target": "n_greeting" },
                { "id": "e2", "source": "n_greeting", "target": "n_menu" },
                { "id": "e3", "source": "n_menu", "target": "n_hours", "sourcePort": "key3" },
                { "id": "e4", "source": "n_hours", "target": "n_end" }
              ]
            }
            """;

        FlowPublishService.FlowPublishResult result = publishService.publishFlow(tenantId, flowId, "3000", flowName, reactFlowJson);
        assertTrue(result.isSuccess(), "Publish must succeed for flows containing '&' in menu option labels");

        Path pubPath = Path.of(result.getFilePath());
        assertTrue(Files.exists(pubPath), "Published scenario VXML file must exist on disk");

        String vxmlContent = Files.readString(pubPath);
        assertTrue(vxmlContent.contains("3-Hours &amp; Location"), "VXML file must contain properly escaped '3-Hours &amp; Location'");
        assertFalse(vxmlContent.contains("3-Hours & Location"), "VXML file must NOT contain raw unescaped '3-Hours & Location'");
    }
}
