package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.patch.AddEdgePatch;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Duplicate Edge Prevention Test")
class FlowDuplicateEdgePreventionTest {

    @Test
    @DisplayName("FlowModel.addConnection ignores duplicate edges with matching source, port, and target")
    void testFlowModelPreventsDuplicateEdges() {
        FlowModel model = new FlowModel();
        FlowNode n1 = new FlowNode("n1", FlowNodeType.PROMPT, "Menu");
        FlowNode n2 = new FlowNode("n2", FlowNodeType.END, "End Call");
        model.addNode(n1);
        model.addNode(n2);

        FlowConnection conn1 = new FlowConnection("c1", "n1", "error", "n2", "in");
        FlowConnection conn2 = new FlowConnection("c2", "n1", "error", "n2", "in");

        model.addConnection(conn1);
        model.addConnection(conn2);

        assertEquals(1, model.getConnections().size(), "FlowModel must prevent duplicate connections between the same nodes/ports");
    }

    @Test
    @DisplayName("FlowPatchApplier skips ADD_EDGE patch if equivalent edge already exists")
    void testPatchApplierSkipsDuplicateAddEdge() {
        FlowModel model = new FlowModel();
        FlowNode n1 = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu");
        FlowNode n2 = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(n1);
        model.addNode(n2);

        // Pre-existing fallback edge
        model.addConnection(new FlowConnection("c_fallback", "main_menu", "error", "end", "in"));
        assertEquals(1, model.getConnections().size());

        FlowPatchApplier applier = new FlowPatchApplier();
        AddEdgePatch patch = new AddEdgePatch("c_new_fallback", "main_menu", "error", "end", "in");

        FlowModel result = applier.apply(model, List.of(patch));

        assertEquals(1, result.getConnections().size(), "Patch applier must skip ADD_EDGE when equivalent edge already exists");
    }
}
