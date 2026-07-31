package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.patch.*;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlowPatchApplierTest {

    @Test
    void testApplyAddNodePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "end", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new AddNodePatch("n1", "prompt", "New Prompt", "Hello", "end", "out")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals(3, result.getNodes().size());
        assertNotNull(result.getNode("n1"));
        assertEquals("New Prompt", result.getNode("n1").getTitle());
    }

    @Test
    void testApplyDeleteNodePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode prompt = new FlowNode("n1", FlowNodeType.PROMPT, "Prompt");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(prompt);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "n1", "in"));
        model.addConnection(new FlowConnection("e2", "n1", "out", "end", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new DeleteNodePatch("n1")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals(2, result.getNodes().size());
        assertNull(result.getNode("n1"));
        assertFalse(result.getConnections().stream().anyMatch(c -> "n1".equals(c.getSourceNodeId()) || "n1".equals(c.getTargetNodeId())));
    }

    @Test
    void testApplyAddEdgePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Menu");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "menu", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new AddEdgePatch("e2", "menu", "key1", "end", "in")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals(2, result.getConnections().size());
        assertTrue(result.getConnections().stream().anyMatch(c -> "e2".equals(c.getId())));
    }

    @Test
    void testApplyDeleteEdgePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "end", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new DeleteEdgePatch("e1", "start", "end")
        );
        FlowModel result = applier.apply(model, patches);

        assertTrue(result.getConnections().isEmpty());
    }

    @Test
    void testApplyRenameNodePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Old Name");
        model.addNode(start);

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new RenameNodePatch("start", "New Name")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals("New Name", result.getNode("start").getTitle());
    }

    @Test
    void testApplyUpdatePromptPatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        start.setPrompt(new FlowPrompt("Old prompt"));
        model.addNode(start);

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new UpdatePromptPatch("start", "New prompt text")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals("New prompt text", result.getNode("start").getPrompt().getText());
    }

    @Test
    void testApplyChangeMenuOptionPatchAdd() {
        FlowModel model = new FlowModel();
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Menu");
        menu.setMenu(new FlowMenu());
        model.addNode(menu);

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new ChangeMenuOptionPatch("menu", "ADD", "1", "Option 1", "end")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals(1, result.getNode("menu").getMenu().getChoices().size());
        assertEquals("1", result.getNode("menu").getMenu().getChoices().get(0).getDtmf());
    }

    @Test
    void testApplyChangeMenuOptionPatchRemove() {
        FlowModel model = new FlowModel();
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Menu");
        FlowMenu menuObj = new FlowMenu();
        FlowChoice c1 = new FlowChoice("key1", "Option 1", "end");
        c1.setDtmf("1");
        menuObj.addChoice(c1);
        menu.setMenu(menuObj);
        model.addNode(menu);

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new ChangeMenuOptionPatch("menu", "REMOVE", "1", null, null)
        );
        FlowModel result = applier.apply(model, patches);

        assertTrue(result.getNode("menu").getMenu().getChoices().isEmpty());
    }

    @Test
    void testApplyMoveSubtreePatch() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Menu");
        FlowNode prompt = new FlowNode("prompt", FlowNodeType.PROMPT, "Prompt");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(menu);
        model.addNode(prompt);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "menu", "in"));
        model.addConnection(new FlowConnection("e2", "menu", "key1", "prompt", "in"));
        model.addConnection(new FlowConnection("e3", "prompt", "out", "end", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new MoveSubtreePatch("prompt", "menu", "key2")
        );
        FlowModel result = applier.apply(model, patches);

        long incomingToPrompt = result.getConnections().stream().filter(c -> "prompt".equals(c.getTargetNodeId())).count();
        assertEquals(1, incomingToPrompt);
        assertTrue(result.getConnections().stream().anyMatch(c -> "menu".equals(c.getSourceNodeId()) && "prompt".equals(c.getTargetNodeId()) && "key2".equals(c.getSourcePort())));
    }

    @Test
    void testApplyMultiplePatches() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Menu");
        menu.setMenu(new FlowMenu());
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End");
        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);
        model.addConnection(new FlowConnection("e1", "start", "out", "menu", "in"));

        FlowPatchApplier applier = new FlowPatchApplier();
        List<FlowPatchOperation> patches = List.of(
                new UpdatePromptPatch("start", "Welcome to our service"),
                new ChangeMenuOptionPatch("menu", "ADD", "1", "Sales", "end"),
                new AddEdgePatch("e2", "menu", "key1", "end", "in")
        );
        FlowModel result = applier.apply(model, patches);

        assertEquals("Welcome to our service", result.getNode("start").getPrompt().getText());
        assertEquals(1, result.getNode("menu").getMenu().getChoices().size());
        assertEquals(2, result.getConnections().size());
    }
}
