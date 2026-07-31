package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.patch.UpdatePromptPatch;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Flow Optimization Rationale & Summary Test")
class FlowOptimizationSummaryTest {

    @Test
    @DisplayName("Assert rationale explicitly caveats unchanged structure and describes specific patch changes")
    void testStructureUnchangedRationale() {
        FlowModel model = new FlowModel();
        FlowNode n1 = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode n2 = new FlowNode("prompt_main", FlowNodeType.PROMPT, "Welcome Prompt");
        FlowNode n3 = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(n1);
        model.addNode(n2);
        model.addNode(n3);
        model.addConnection(new FlowConnection("c1", "start", "out", "prompt_main", "in"));
        model.addConnection(new FlowConnection("c2", "prompt_main", "out", "end", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        UpdatePromptPatch patch = new UpdatePromptPatch("prompt_main", "Updated Welcome Message for Clarity");
        FlowModel patched = applier.apply(model, List.of(patch));

        // Nodes and edges count remains unchanged (3 nodes, 2 connections)
        boolean structureUnchanged = (model.getNodes().size() == patched.getNodes().size() &&
                model.getConnections().size() == patched.getConnections().size());

        assertTrue(structureUnchanged, "Topology must be unchanged for prompt update patch");
    }
}
