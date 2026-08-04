package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.IvrTemplate;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowPrompt;
import com.nexusivr.ai.model.flow.FlowConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VxmlRoundTripFidelityTest {

    private ModelToVxmlExporter exporter;
    private VxmlToModelConverter converter;
    private FlowModelValidator flowModelValidator;
    private ModelFlowValidator modelFlowValidator;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        converter = new VxmlToModelConverter();
        flowModelValidator = new FlowModelValidator();
        modelFlowValidator = new ModelFlowValidator();
    }

    private static final List<String> DOMAINS = List.of(
            "healthcare",
            "banking",
            "restaurant",
            "hospitality",
            "telecom",
            "government",
            "airline",
            "retail",
            "insurance",
            "education",
            "generic"
    );

    @Test
    @DisplayName("VXML Round-trip produces zero validation errors and preserves name for all templates")
    void testAllTemplateDomainsRoundTripFidelity() throws VxmlParseException {
        for (String domain : DOMAINS) {
            IvrTemplate template = IvrTemplateRegistry.getClosestTemplate(domain);
            assertNotNull(template, "Template must exist for domain: " + domain);

            FlowModel originalModel = FlowContextService.convertJsonToModel(template.getTemplateFlowJson());
            assertNotNull(originalModel, "Original FlowModel must convert from JSON for: " + domain);
            String expectedName = template.getDomainName() + " IVR Flow";
            originalModel.setName(expectedName);

            // Validate original model
            FlowValidationResponse origValidation = flowModelValidator.validate(originalModel);
            assertEquals(0, origValidation.getErrorCount(),
                    "Original template flow must have 0 validation errors for domain: " + domain +
                            ". Errors: " + origValidation.getIssues());

            // Step 1: Export to VXML
            String vxml = exporter.export(originalModel);
            assertNotNull(vxml, "Exported VXML must not be null for domain: " + domain);
            assertFalse(vxml.isBlank(), "Exported VXML must not be blank for domain: " + domain);
            assertTrue(vxml.contains(expectedName), "VXML must contain flow name metadata");

            // Step 2: Import back from VXML
            FlowModel reimportedModel = converter.convert(vxml);
            assertNotNull(reimportedModel, "Re-imported FlowModel must not be null for domain: " + domain);

            // Step 3: Verify Title Preservation
            assertEquals(expectedName, reimportedModel.getName(),
                    "Re-imported flow must preserve exact original name for domain: " + domain);

            // Step 4: Validate Re-imported Model — MUST HAVE ZERO ERRORS
            FlowValidationResponse reimportedValidation = flowModelValidator.validate(reimportedModel);
            assertEquals(0, reimportedValidation.getErrorCount(),
                    "Re-imported FlowModel must have 0 validation errors for domain: " + domain +
                            ". Errors: " + reimportedValidation.getIssues());

            FlowValidationResponse modelFlowVal = modelFlowValidator.validate(reimportedModel);
            assertEquals(0, modelFlowVal.getErrorCount(),
                    "ModelFlowValidator must report 0 errors for re-imported domain: " + domain +
                            ". Errors: " + modelFlowVal.getIssues());

            // Step 5: Verify Node Count Integrity
            assertFalse(reimportedModel.getNodes().isEmpty(), "Re-imported model must contain nodes");
            assertEquals(originalModel.getNodes().size(), reimportedModel.getNodes().size(),
                    "Re-imported model node count must match original for domain: " + domain);
        }
    }

    @Test
    @DisplayName("Custom valid flow with Transfer and Input nodes round-trips with 0 errors")
    void testCustomValidFlowRoundTrip() throws VxmlParseException {
        FlowModel model = new FlowModel();
        model.setName("Healthcare Custom Flow");

        FlowNode start = new FlowNode("n1", FlowNodeType.START, "Start Call");
        FlowNode input = new FlowNode("n2", FlowNodeType.INPUT, "Patient ID Input");
        FlowNode transfer = new FlowNode("n3", FlowNodeType.TRANSFER, "Nurse Line Transfer");
        FlowNode end = new FlowNode("n4", FlowNodeType.END, "End Call");

        start.setPrompt(new FlowPrompt("Welcome to City Clinic."));
        input.setPrompt(new FlowPrompt("Please enter your patient ID."));
        transfer.setPrompt(new FlowPrompt("Connecting you to an on-call nurse."));

        model.addNode(start);
        model.addNode(input);
        model.addNode(transfer);
        model.addNode(end);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "success", "n3", "in"));
        model.addConnection(new FlowConnection("e3", "n2", "timeout", "n4", "in"));
        model.addConnection(new FlowConnection("e4", "n3", "success", "n4", "in"));
        model.addConnection(new FlowConnection("e5", "n3", "fail", "n4", "in"));

        // Validate original
        FlowValidationResponse origVal = flowModelValidator.validate(model);
        assertEquals(0, origVal.getErrorCount(), "Original model must have 0 errors");

        // Export & Import
        String vxml = exporter.export(model);
        FlowModel imported = converter.convert(vxml);

        assertEquals("Healthcare Custom Flow", imported.getName());

        FlowValidationResponse importedVal = flowModelValidator.validate(imported);
        assertEquals(0, importedVal.getErrorCount(),
                "Re-imported custom flow must have 0 validation errors. Found: " + importedVal.getIssues());

        assertEquals(4, imported.getNodes().size());
        assertEquals(FlowNodeType.TRANSFER, imported.getNode("n3").getType());
        assertEquals(FlowNodeType.INPUT, imported.getNode("n2").getType());
    }
}
