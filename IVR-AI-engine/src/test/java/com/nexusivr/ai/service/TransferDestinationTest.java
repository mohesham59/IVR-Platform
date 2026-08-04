package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransferDestinationTest {

    private ModelFlowValidator validator;
    private ModelToVxmlExporter exporter;

    @BeforeEach
    void setUp() {
        validator = new ModelFlowValidator();
        exporter = new ModelToVxmlExporter();
    }

    @Test
    void testPromptBuilderContainsTransferDestRules() {
        String systemInstruction = PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION;
        assertNotNull(systemInstruction);
        assertTrue(systemInstruction.contains("<transfer dest=\"...\"> RULES:"), "System prompt must contain explicit transfer dest rules");
        assertTrue(systemInstruction.contains("NEVER use free-text human/role/department names"), "System prompt must forbid human role names");
        assertTrue(systemInstruction.contains("TRANSFER_TARGET_PLACEHOLDER"), "System prompt must document TRANSFER_TARGET_PLACEHOLDER");
    }

    @Test
    void testIsUnconfiguredOrRoleTransferDestinationClassification() {
        // Placeholders and human role strings (must return true -> unconfigured)
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination(null));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination(""));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("   "));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("TRANSFER_TARGET_PLACEHOLDER"));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("Patient Service Agent"));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("Pharmacy Staff"));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("Urgent Care"));
        assertTrue(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("Front Desk"));

        // Valid dialable extensions, SIP URIs, and system tokens (must return false -> valid/configured)
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("+1003"));
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("1001"));
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("*99"));
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("sip:agent@nexusivr.com"));
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("AGENT_QUEUE"));
        assertFalse(ModelFlowValidator.isUnconfiguredOrRoleTransferDestination("FRAUD_HOTLINE"));
    }

    @Test
    void testValidatorFlagsHumanRoleStringsAsUnconfiguredTransferDest() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode transferNode = new FlowNode("transfer_1", FlowNodeType.TRANSFER, "Transfer");
        transferNode.setTransfer(new FlowTransfer("Patient Service Agent"));
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(transferNode);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "transfer_1", "in"));
        model.addConnection(new FlowConnection("c2", "transfer_1", "success", "end", "in"));

        FlowValidationResponse response = validator.validate(model);

        List<ValidationIssueDto> unconfiguredIssues = response.getIssues().stream()
                .filter(i -> "UNCONFIGURED_TRANSFER_DEST".equals(i.getCode()))
                .toList();

        assertEquals(1, unconfiguredIssues.size(), "Validator must issue UNCONFIGURED_TRANSFER_DEST warning for role string 'Patient Service Agent'");
        assertTrue(unconfiguredIssues.get(0).getMessage().contains("Patient Service Agent"));
    }

    @Test
    void testValidatorPassesDialableExtensionCleanly() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode transferNode = new FlowNode("transfer_1", FlowNodeType.TRANSFER, "Transfer");
        transferNode.setTransfer(new FlowTransfer("+1003"));
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(transferNode);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "transfer_1", "in"));
        model.addConnection(new FlowConnection("c2", "transfer_1", "success", "end", "in"));

        FlowValidationResponse response = validator.validate(model);

        List<ValidationIssueDto> transferIssues = response.getIssues().stream()
                .filter(i -> "UNCONFIGURED_TRANSFER_DEST".equals(i.getCode()) || "MISSING_TRANSFER_DEST".equals(i.getCode()))
                .toList();

        assertTrue(transferIssues.isEmpty(), "Validator must NOT flag valid extension '+1003'");
        assertEquals(100, response.getScore());
    }

    @Test
    void testExporterRendersPlaceholderWhenDestinationIsMissing() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode transferNode = new FlowNode("transfer_1", FlowNodeType.TRANSFER, "Transfer");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(transferNode);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "transfer_1", "in"));
        model.addConnection(new FlowConnection("c2", "transfer_1", "success", "end", "in"));

        String xml = exporter.export(model);

        assertTrue(xml.contains("dest=\"TRANSFER_TARGET_PLACEHOLDER\""), "Exporter must render dest=\"TRANSFER_TARGET_PLACEHOLDER\" when destination is missing");
    }
}
