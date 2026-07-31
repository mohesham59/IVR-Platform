package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowValidationResponseSemanticsTest {

    @Test
    void testCleanFlowHasValidStatusAndCleanFlag() {
        FlowValidationResponse response = new FlowValidationResponse(true, List.of(), 100);

        assertTrue(response.isValid());
        assertTrue(response.isClean());
        assertEquals("VALID", response.getStatus());
        assertEquals("Valid (Clean)", response.getStatusLabel());
        assertEquals(0, response.getErrorCount());
        assertEquals(0, response.getWarningCount());
    }

    @Test
    void testWarningsOnlyFlowHasValidWithWarningsStatusAndNotClean() {
        ValidationIssueDto warn1 = new ValidationIssueDto(ValidationSeverity.WARNING, "UNREACHABLE_NODE", "Node n2 is unreachable", "n2", null);
        ValidationIssueDto warn2 = new ValidationIssueDto(ValidationSeverity.WARNING, "PORT_MISMATCH", "Port mismatch on n3", "n3", null);

        FlowValidationResponse response = new FlowValidationResponse(true, List.of(warn1, warn2), 90);

        assertTrue(response.isValid(), "Flow with warnings only must be valid=true");
        assertFalse(response.isClean(), "Flow with warnings must NOT be clean");
        assertEquals("VALID_WITH_WARNINGS", response.getStatus());
        assertEquals("Valid (2 warnings)", response.getStatusLabel());
        assertEquals(0, response.getErrorCount());
        assertEquals(2, response.getWarningCount());

        // Invariant check: when status is VALID or VALID_WITH_WARNINGS, no ERROR issues exist
        assertTrue(response.getIssues().stream().noneMatch(i -> i.getSeverity() == ValidationSeverity.ERROR));
    }

    @Test
    void testErrorFlowHasInvalidStatus() {
        ValidationIssueDto error1 = new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_START", "Flow missing start node", null, null);
        ValidationIssueDto warn1 = new ValidationIssueDto(ValidationSeverity.WARNING, "EMPTY_MENU", "Menu is empty", "m1", null);

        FlowValidationResponse response = new FlowValidationResponse(false, List.of(error1, warn1), 60);

        assertFalse(response.isValid(), "Flow with errors must be valid=false");
        assertFalse(response.isClean());
        assertEquals("INVALID", response.getStatus());
        assertEquals("Invalid (1 error)", response.getStatusLabel());
        assertEquals(1, response.getErrorCount());
        assertEquals(1, response.getWarningCount());
    }

    @Test
    void testModelFlowValidatorProducesValidWithWarningsStatusForWarningOnlyFlow() {
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start"));
        model.addNode(new FlowNode("end", FlowNodeType.END, "End"));

        // Add unreachable node connected to end (so it is not an orphan ERROR, but an UNREACHABLE_NODE WARNING)
        model.addNode(new FlowNode("n_unreachable", FlowNodeType.PROMPT, "Unreachable"));
        model.addConnection(new FlowConnection("c1", "start", "out", "end", "in"));
        model.addConnection(new FlowConnection("c2", "n_unreachable", "out", "end", "in"));

        ModelFlowValidator validator = new ModelFlowValidator();
        FlowValidationResponse response = validator.validate(model);

        assertTrue(response.isValid());
        assertEquals("VALID_WITH_WARNINGS", response.getStatus());
        assertEquals(0, response.getErrorCount());
        assertTrue(response.getWarningCount() > 0);
        assertTrue(response.getIssues().stream().noneMatch(i -> i.getSeverity() == ValidationSeverity.ERROR));
    }
}
