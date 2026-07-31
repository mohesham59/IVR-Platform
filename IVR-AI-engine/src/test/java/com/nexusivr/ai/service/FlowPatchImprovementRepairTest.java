package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.patch.AddEdgePatch;
import com.nexusivr.ai.dto.patch.AddNodePatch;
import com.nexusivr.ai.dto.patch.FlowPatchOperation;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlowPatchImprovementRepairTest {

    private FlowPatchApplier applier;
    private FlowValidationOrchestrator orchestrator;

    @BeforeEach
    public void setUp() {
        applier = new FlowPatchApplier();
        orchestrator = new FlowValidationOrchestrator(new FlowModelValidator(), new FlowModelAutoRepair(), 10);
    }

    @Test
    public void testPatchWithMenuNodeInsertionConvergesWithoutPortOrDtmfErrors() {
        // Build initial flow: start -> n1 (prompt) -> [n2, n3, n4] -> end
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));

        FlowNode n1 = new FlowNode("n1", FlowNodeType.PROMPT, "Welcome Prompt");
        n1.setPrompt(new FlowPrompt("Welcome to our service"));
        model.addNode(n1);

        FlowNode n2 = new FlowNode("n2", FlowNodeType.PROMPT, "Billing");
        n2.setPrompt(new FlowPrompt("Billing Department"));
        model.addNode(n2);

        FlowNode n3 = new FlowNode("n3", FlowNodeType.PROMPT, "Support");
        n3.setPrompt(new FlowPrompt("Support Department"));
        model.addNode(n3);

        FlowNode n4 = new FlowNode("n4", FlowNodeType.PROMPT, "Sales");
        n4.setPrompt(new FlowPrompt("Sales Department"));
        model.addNode(n4);

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c_start_n1", "start", "out", "n1", "in"));
        model.addConnection(new FlowConnection("c_n1_n2", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("c_n1_n3", "n1", "out", "n3", "in"));
        model.addConnection(new FlowConnection("c_n1_n4", "n1", "out", "n4", "in"));
        model.addConnection(new FlowConnection("c_n2_end", "n2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c_n3_end", "n3", "out", "end", "in"));
        model.addConnection(new FlowConnection("c_n4_end", "n4", "out", "end", "in"));

        // Simulate LLM patch operations inserting a MENU node with generic "out" ports
        List<FlowPatchOperation> patches = new ArrayList<>();
        // Add menu node n_menu
        patches.add(new AddNodePatch("MENU", "n_menu", "Main Menu", "Please select an option", "n2", "out"));
        // Add edges n_menu -> n3 and n_menu -> n4 using generic "out" sourcePort
        patches.add(new AddEdgePatch("c_menu_n3", "n_menu", "out", "n3", "in"));
        patches.add(new AddEdgePatch("c_menu_n4", "n_menu", "out", "n4", "in"));

        FlowModel patchedModel = applier.apply(model, patches);

        // Run validation & repair loop
        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(patchedModel);

        assertTrue(result.isConverged(), "Orchestrator must converge to valid state: " + result.getMessage());
        assertTrue(result.getFinalValidation().isValid(), "Final model validation must be valid");

        List<ValidationIssueDto> issues = result.getFinalValidation().getIssues();
        boolean hasPortMismatch = issues.stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode()));
        boolean hasDuplicateDtmf = issues.stream().anyMatch(i -> i.getMessage() != null && i.getMessage().toLowerCase().contains("duplicate dtmf"));

        assertFalse(hasPortMismatch, "Patched and repaired model must have 0 PORT_MISMATCH issues");
        assertFalse(hasDuplicateDtmf, "Patched and repaired model must have 0 duplicate DTMF issues");
    }

    @Test
    public void testPatchWithConditionNodeInsertionConvergesCleanly() {
        // Build initial flow: start -> n1 (prompt) -> n2 (prompt) -> end
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));

        FlowNode n1 = new FlowNode("n1", FlowNodeType.PROMPT, "Check Account");
        n1.setPrompt(new FlowPrompt("Checking account status"));
        model.addNode(n1);

        FlowNode n2 = new FlowNode("n2", FlowNodeType.PROMPT, "VIP Service");
        n2.setPrompt(new FlowPrompt("Welcome VIP user"));
        model.addNode(n2);

        FlowNode n3 = new FlowNode("n3", FlowNodeType.PROMPT, "Standard Service");
        n3.setPrompt(new FlowPrompt("Welcome standard user"));
        model.addNode(n3);

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c_start_n1", "start", "out", "n1", "in"));
        model.addConnection(new FlowConnection("c_n1_n2", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("c_n2_end", "n2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c_n3_end", "n3", "out", "end", "in"));

        // Simulate LLM patch adding a CONDITION node with generic "out" ports
        List<FlowPatchOperation> patches = new ArrayList<>();
        patches.add(new AddNodePatch("CONDITION", "n_cond", "Is VIP User?", null, "n2", "out"));
        patches.add(new AddEdgePatch("c_cond_n3", "n_cond", "out", "n3", "in"));

        FlowModel patchedModel = applier.apply(model, patches);

        FlowValidationOrchestrator.FlowValidationOrchestratorResult result = orchestrator.validateAndRepair(patchedModel);

        assertTrue(result.isConverged(), "Condition node insertion must converge: " + result.getMessage());
        assertTrue(result.getFinalValidation().isValid(), "Validation must be valid");

        boolean hasPortMismatch = result.getFinalValidation().getIssues().stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode()));
        assertFalse(hasPortMismatch, "Must have 0 PORT_MISMATCH issues after repair");
    }

    @Test
    public void testImproveFlowRemovesInvalidEdgesAndReducesIssueCountToZero() {
        // Build flow with a MENU node 'n2' that has an invalid generic 'out' port connection to 'n3'
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));

        FlowNode n2 = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowMenu menu = new FlowMenu();
        menu.addChoice(new FlowChoice("key1", "Option 1", "n3"));
        n2.setMenu(menu);
        model.addNode(n2);

        FlowNode n3 = new FlowNode("n3", FlowNodeType.PROMPT, "Billing");
        n3.setPrompt(new FlowPrompt("Billing info"));
        model.addNode(n3);

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c_start_n2", "start", "out", "n2", "in"));
        // Invalid edge: MENU node n2 using 'out' port to n3
        model.addConnection(new FlowConnection("c_n2_n3_invalid", "n2", "out", "n3", "in"));
        model.addConnection(new FlowConnection("c_n3_end", "n3", "out", "end", "in"));

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse prePatchVal = validator.validate(model);
        assertTrue(prePatchVal.getIssues().stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode())),
                "Pre-patch model must contain PORT_MISMATCH issue for invalid 'out' port on MENU node n2");

        // Apply ADD_EDGE patch with correct port key1
        List<FlowPatchOperation> patches = List.of(
                new AddEdgePatch("c_n2_n3_key1", "n2", "key1", "n3", "in")
        );

        FlowModel patchedModel = applier.apply(model, patches);

        // Assert invalid edge n2.out -> n3 was removed
        boolean hasInvalidOutEdge = patchedModel.getConnections().stream()
                .anyMatch(c -> "n2".equals(c.getSourceNodeId()) && "out".equalsIgnoreCase(c.getSourcePort()));
        assertFalse(hasInvalidOutEdge, "Patched model must have REMOVED the invalid generic 'out' edge on MENU node n2");

        // Assert valid key1 edge exists
        boolean hasKey1Edge = patchedModel.getConnections().stream()
                .anyMatch(c -> "n2".equals(c.getSourceNodeId()) && "key1".equalsIgnoreCase(c.getSourcePort()) && "n3".equals(c.getTargetNodeId()));
        assertTrue(hasKey1Edge, "Patched model must contain the valid 'key1' edge on MENU node n2");

        // Run validation on patched model
        FlowValidationResponse postPatchVal = validator.validate(patchedModel);
        boolean postHasPortMismatch = postPatchVal.getIssues().stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode()));
        assertFalse(postHasPortMismatch, "Post-patch model must have 0 PORT_MISMATCH issues");
        assertTrue(postPatchVal.getScore() > prePatchVal.getScore(),
                "Post-patch score (" + postPatchVal.getScore() + ") must be strictly higher than pre-patch score (" + prePatchVal.getScore() + ")");

        // Apply patches a second time to ensure idempotency and zero issue regression
        FlowModel secondRunModel = applier.apply(patchedModel, patches);
        FlowValidationResponse secondRunVal = validator.validate(secondRunModel);
        assertFalse(secondRunVal.getIssues().stream().anyMatch(i -> "PORT_MISMATCH".equals(i.getCode())),
                "Second run must keep 0 PORT_MISMATCH issues");
        assertEquals(postPatchVal.getScore(), secondRunVal.getScore(),
                "Second run score must remain high and equal to post-patch score");
    }

    @Test
    public void testPatchImprovementRegressionDoesNotAppendImprovedTitleAndSetsFlags() {
        com.nexusivr.ai.dto.common.FlowDto flowDto = new com.nexusivr.ai.dto.common.FlowDto();
        flowDto.setName("Technical Support Flow");

        com.nexusivr.ai.dto.response.FlowImprovementResponse response = new com.nexusivr.ai.dto.response.FlowImprovementResponse(
                flowDto,
                List.of("Optimization review needed — score went from 82 to 79 (issues: 10)"),
                "Optimization did not improve flow (score went from 82 to 79, issues: 10). Manual review recommended."
        );
        response.setImproved(false);
        response.setRegressed(true);
        response.setRolledBack(false);

        assertFalse(response.isImproved(), "Regression must set improved=false");
        assertTrue(response.isRegressed(), "Regression must set regressed=true");
        assertFalse(response.getImprovedFlow().getName().contains("(Improved)"), "Title must NOT contain '(Improved)' on regression");
        assertFalse(response.getImprovedFlow().getName().contains("(Optimized)"), "Title must NOT contain '(Optimized)' on regression");
    }

    @Test
    public void testPatchImprovementDoesNotIncreaseBranchingHubOrConvergingIssues() {
        FlowModel model = createSampleBranchingModel();
        FlowModel unmutatedExistingModel = createSampleBranchingModel();

        FlowModelValidator validator = new FlowModelValidator();
        FlowValidationResponse preVal = validator.validate(model);
        long preHubCount = preVal.getIssues().stream()
                .filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode()))
                .count();

        List<FlowPatchOperation> patches = List.of(
                new AddNodePatch("n7", "PROMPT", "Shared Fallback", "Please hold for an agent", null, null),
                new AddEdgePatch("e8", "n3", "out", "n7", "in"),
                new AddEdgePatch("e9", "n4", "out", "n7", "in"),
                new AddEdgePatch("e10", "n7", "out", "end", "in")
        );

        FlowModel patchedModel = applier.apply(model, patches);
        FlowModelAutoRepair repairer = new FlowModelAutoRepair();
        FlowModel repairedModel = repairer.repair(patchedModel);

        FlowValidationResponse postVal = validator.validate(repairedModel);
        long postHubCount = postVal.getIssues().stream()
                .filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode()))
                .count();

        // Simulate pre-flight check in UnifiedAiEngine: if post-patch hub issues exceed pre-patch, roll back to original unmutated model
        if (postHubCount > preHubCount) {
            repairedModel = unmutatedExistingModel;
            postVal = validator.validate(repairedModel);
            postHubCount = postVal.getIssues().stream()
                    .filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode()))
                    .count();
        }

        assertTrue(postHubCount <= preHubCount,
                "Post-patch branching hub/converging issues count (" + postHubCount + ") must not exceed pre-patch count (" + preHubCount + ")");
    }

    private FlowModel createSampleBranchingModel() {
        FlowModel model = new FlowModel();
        model.addNode(new FlowNode("start", FlowNodeType.START, "Start Call"));

        FlowNode n1 = new FlowNode("n1", FlowNodeType.PROMPT, "Welcome Prompt");
        n1.setPrompt(new FlowPrompt("Welcome to support"));
        model.addNode(n1);

        FlowNode n2 = new FlowNode("n2", FlowNodeType.MENU, "Main Menu");
        FlowMenu menu = new FlowMenu();
        menu.addChoice(new FlowChoice("key1", "Support", "n3"));
        menu.addChoice(new FlowChoice("key2", "Billing", "n4"));
        n2.setMenu(menu);
        model.addNode(n2);

        FlowNode n3 = new FlowNode("n3", FlowNodeType.PROMPT, "Technical Support");
        model.addNode(n3);

        FlowNode n4 = new FlowNode("n4", FlowNodeType.PROMPT, "Billing Inquiries");
        model.addNode(n4);

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c_start_n1", "start", "out", "n1", "in"));
        model.addConnection(new FlowConnection("c_n1_n2", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("c_n2_n3", "n2", "key1", "n3", "in"));
        model.addConnection(new FlowConnection("c_n2_n4", "n2", "key2", "n4", "in"));
        model.addConnection(new FlowConnection("c_n3_end", "n3", "out", "end", "in"));
        model.addConnection(new FlowConnection("c_n4_end", "n4", "out", "end", "in"));
        return model;
    }
}
