package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class VxmlFullRoundTripTest {

    private final ModelToVxmlExporter exporter = new ModelToVxmlExporter();
    private final VxmlToModelConverter converter = new VxmlToModelConverter();
    private final ModelFlowValidator validator = new ModelFlowValidator();

    @Test
    @DisplayName("Complex flow with AI routing, Transfer, Menu, and Conditions preserves node names, connection count, and yields 0 validation errors")
    void testComplexFlowRoundTrip() throws VxmlParseException {
        FlowModel model = new FlowModel();
        model.setName("Insurance Claims IVR");

        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode auth = new FlowNode("auth", FlowNodeType.INPUT, "User Authentication");
        FlowNode ai = new FlowNode("ai_routing", FlowNodeType.AI, "AI Assistant Routing");
        FlowNode menu = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu Fallback");
        FlowNode claim = new FlowNode("claims_form", FlowNodeType.TRANSFER, "Claims Form");
        FlowNode quote = new FlowNode("quote_form", FlowNodeType.TRANSFER, "Quote Form");
        FlowNode end = new FlowNode("end_call", FlowNodeType.END, "End Call");

        start.setPrompt(new FlowPrompt("Welcome to Insurance Hotline."));
        auth.setPrompt(new FlowPrompt("Enter your policy number."));

        FlowAi aiConfig = new FlowAi();
        aiConfig.setRole("insurance_agent");
        LinkedHashMap<String, String> aiOptions = new LinkedHashMap<>();
        aiOptions.put("claims", "claims_form");
        aiOptions.put("quote", "quote_form");
        aiConfig.setRoutingOptions(aiOptions);
        ai.setAi(aiConfig);
        ai.setPrompt(new FlowPrompt("How can I help you today?"));

        FlowMenu menuConfig = new FlowMenu();
        menuConfig.addChoice(new FlowChoice("1", "claims_form", "Claims"));
        menuConfig.addChoice(new FlowChoice("2", "quote_form", "Quote"));
        menu.setMenu(menuConfig);

        claim.setPrompt(new FlowPrompt("Transferring to claims representative."));
        claim.setTransfer(new FlowTransfer("101"));

        quote.setPrompt(new FlowPrompt("Transferring to sales representative."));
        quote.setTransfer(new FlowTransfer("102"));

        end.setPrompt(new FlowPrompt("Thank you for calling."));

        model.addNode(start);
        model.addNode(auth);
        model.addNode(ai);
        model.addNode(menu);
        model.addNode(claim);
        model.addNode(quote);
        model.addNode(end);

        // Add connections
        model.addConnection(new FlowConnection("c1", "start", "out", "auth", "in"));
        model.addConnection(new FlowConnection("c2", "auth", "success", "ai_routing", "in"));
        model.addConnection(new FlowConnection("c3", "auth", "timeout", "auth", "in"));
        model.addConnection(new FlowConnection("c4", "ai_routing", "claims", "claims_form", "in"));
        model.addConnection(new FlowConnection("c5", "ai_routing", "quote", "quote_form", "in"));
        model.addConnection(new FlowConnection("c6", "ai_routing", "nomatch", "main_menu", "in"));
        model.addConnection(new FlowConnection("c7", "main_menu", "1", "claims_form", "in"));
        model.addConnection(new FlowConnection("c8", "main_menu", "2", "quote_form", "in"));
        model.addConnection(new FlowConnection("c9", "main_menu", "timeout", "end_call", "in"));
        model.addConnection(new FlowConnection("c10", "claims_form", "success", "end_call", "in"));
        model.addConnection(new FlowConnection("c11", "claims_form", "fail", "main_menu", "in"));
        model.addConnection(new FlowConnection("c12", "quote_form", "success", "end_call", "in"));
        model.addConnection(new FlowConnection("c13", "quote_form", "fail", "main_menu", "in"));

        // Validate original
        FlowValidationResponse origVal = validator.validate(model);
        System.out.println("Original validation: score=" + origVal.getScore() + ", errors=" + origVal.getErrorCount() + ", issues=" + origVal.getIssues());
        assertEquals(0, origVal.getErrorCount(), "Original model must have 0 errors");

        // Export to VXML
        String vxml = exporter.export(model);
        System.out.println("Exported VXML:\n" + vxml);

        // Import back from VXML
        FlowModel imported = converter.convert(vxml);

        // Validate re-imported
        FlowValidationResponse importedVal = validator.validate(imported);
        System.out.println("Re-imported validation: score=" + importedVal.getScore() + ", errors=" + importedVal.getErrorCount() + ", issues=" + importedVal.getIssues());

        // Assert node IDs preserved
        assertEquals("start", imported.getNode("start").getId());
        assertEquals("auth", imported.getNode("auth").getId());
        assertEquals("claims_form", imported.getNode("claims_form").getId());
        assertEquals("quote_form", imported.getNode("quote_form").getId());
        assertEquals("end_call", imported.getNode("end_call").getId());

        // Assert node titles preserved (or derived cleanly)
        assertEquals("User Authentication", imported.getNode("auth").getTitle());
        assertEquals("Claims Form", imported.getNode("claims_form").getTitle());

        // Assert 0 validation errors
        assertEquals(0, importedVal.getErrorCount(), "Re-imported flow MUST have 0 validation errors. Issues: " + importedVal.getIssues());
    }

    @Test
    @DisplayName("Menu with 6 choices and duplicate DTMF input assigns unique DTMF digits (1, 2, 3, 4, 5, 0) and passes validation")
    void testMenuChoiceUniqueDtmfAllocation() {
        FlowModel model = new FlowModel();
        model.setName("Generic IVR");

        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode menu = new FlowNode("form_main_menu", FlowNodeType.MENU, "Main Menu");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        FlowMenu menuConfig = new FlowMenu();
        // 6 choices, 3 of which have duplicate dtmf="1" or blank DTMF
        FlowChoice c1 = new FlowChoice("key1", "Individual form", "form_individual_form");
        c1.setDtmf("1");
        FlowChoice c2 = new FlowChoice("key2", "Corporate form", "form_corporate_form");
        c2.setDtmf("2");
        FlowChoice c3 = new FlowChoice("key3", "Exemptions form", "form_exemptions_form");
        c3.setDtmf("3");
        FlowChoice c4 = new FlowChoice("key4", "Late form", "form_late_form");
        c4.setDtmf("1"); // Duplicate!
        FlowChoice c5 = new FlowChoice("key5", "Technical form", "form_technical_form");
        c5.setDtmf("1"); // Duplicate!
        FlowChoice c6 = new FlowChoice("key6", "Customer service form", "form_customer_service_form");
        c6.setDtmf("0");

        menuConfig.addChoice(c1);
        menuConfig.addChoice(c2);
        menuConfig.addChoice(c3);
        menuConfig.addChoice(c4);
        menuConfig.addChoice(c5);
        menuConfig.addChoice(c6);
        menu.setMenu(menuConfig);

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "form_main_menu", "in"));
        model.addConnection(new FlowConnection("c2", "form_main_menu", "key1", "end", "in"));

        String exportedVxml = exporter.export(model);
        System.out.println("Exported Menu VXML:\n" + exportedVxml);

        // Parse exported choices and assert DTMF uniqueness
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<choice\\s+dtmf=\"([^\"]+)\"").matcher(exportedVxml);
        java.util.List<String> assignedDtmfs = new java.util.ArrayList<>();
        while (m.find()) {
            assignedDtmfs.add(m.group(1));
        }

        assertEquals(6, assignedDtmfs.size(), "All 6 menu choices MUST be exported (none dropped)");
        java.util.Set<String> uniqueDtmfs = new java.util.HashSet<>(assignedDtmfs);
        assertEquals(assignedDtmfs.size(), uniqueDtmfs.size(), "Every exported menu choice MUST have a unique DTMF digit. Found: " + assignedDtmfs);
        assertTrue(uniqueDtmfs.contains("1") && uniqueDtmfs.contains("2") && uniqueDtmfs.contains("3") &&
                   uniqueDtmfs.contains("4") && uniqueDtmfs.contains("5") && uniqueDtmfs.contains("0"),
                   "DTMF digits assigned must cover 1, 2, 3, 4, 5, 0 uniquely. Assigned: " + assignedDtmfs);

        // Validate model and exported VXML
        FlowValidationResponse valResponse = validator.validate(model);
        assertEquals(0, valResponse.getErrorCount(), "Model with unique DTMF allocation MUST have 0 errors. Issues: " + valResponse.getIssues());
    }

    @Test
    @DisplayName("Exporting FlowModel to both VXML and JSON yields identical node/edge structures, preserved TRANSFER destination ('AGENT_QUEUE'), and matching validation warning counts")
    void testJsonAndVxmlExportImportEquivalence() throws VxmlParseException {
        FlowModel model = new FlowModel();
        model.setName("Hospitality IVR");

        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode input = new FlowNode("room_input", FlowNodeType.INPUT, "Room Number");
        FlowNode xfer = new FlowNode("xfer_front_desk", FlowNodeType.TRANSFER, "Transfer to Front Desk");
        FlowNode end = new FlowNode("end_call", FlowNodeType.END, "End Call");

        xfer.setTransfer(new FlowTransfer("AGENT_QUEUE"));

        model.addNode(start);
        model.addNode(input);
        model.addNode(xfer);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "room_input", "in"));
        model.addConnection(new FlowConnection("c2", "room_input", "success", "xfer_front_desk", "in"));
        model.addConnection(new FlowConnection("c3", "xfer_front_desk", "success", "end_call", "in"));
        model.addConnection(new FlowConnection("c4", "xfer_front_desk", "fail", "end_call", "in"));

        // Validate original model
        FlowValidationResponse origVal = validator.validate(model);

        // Export to VXML & JSON
        String exportedVxml = exporter.export(model);
        String exportedJson = new ModelToFlowRenderer().render(model);

        System.out.println("Exported VXML:\n" + exportedVxml);
        System.out.println("Exported JSON:\n" + exportedJson);

        // Assert VXML output contains real destination "AGENT_QUEUE" instead of generic hardcoded placeholder
        assertTrue(exportedVxml.contains("dest=\"AGENT_QUEUE\""), "VXML export MUST contain dest=\"AGENT_QUEUE\"");
        assertTrue(exportedJson.contains("\"transferDestination\":\"AGENT_QUEUE\""), "JSON export MUST contain transferDestination: AGENT_QUEUE");

        // Re-import VXML back into FlowModel
        FlowModel vxmlImported = converter.convert(exportedVxml);
        FlowValidationResponse vxmlVal = validator.validate(vxmlImported);

        assertEquals(model.getNodes().size(), vxmlImported.getNodes().size(), "Node count MUST match between original and VXML re-imported model");
        assertNotNull(vxmlImported.getNode("xfer_front_desk").getTransfer(), "TRANSFER node MUST have FlowTransfer object");
        assertEquals("AGENT_QUEUE", vxmlImported.getNode("xfer_front_desk").getTransfer().getDestination(), "Re-imported VXML model MUST preserve 'AGENT_QUEUE' destination");

        // Assert validation issue counts match between original and VXML imported flow
        assertEquals(origVal.getWarningCount(), vxmlVal.getWarningCount(), "Validation warning counts MUST be identical between original and VXML re-imported flow");
        assertEquals(origVal.getErrorCount(), vxmlVal.getErrorCount(), "Validation error counts MUST be identical between original and VXML re-imported flow");
    }
}


