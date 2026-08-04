package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VXML Import Layout & Renderer Tests")
public class VxmlImportLayoutTest {

    private final ModelToFlowRenderer renderer = new ModelToFlowRenderer();

    @Test
    @DisplayName("ModelToFlowRenderer MUST include explicit x and y position properties for rendered nodes")
    void testRendererOutputsPositionCoordinates() {
        FlowModel model = new FlowModel();
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode menu = new FlowNode("menu", FlowNodeType.MENU, "Main Menu");
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(start);
        model.addNode(menu);
        model.addNode(end);

        model.addConnection(new FlowConnection("c1", "start", "out", "menu", "in"));
        model.addConnection(new FlowConnection("c2", "menu", "timeout", "end", "in"));

        String renderedJson = renderer.render(model);
        assertNotNull(renderedJson, "Rendered JSON must not be null");

        JsonObject root = JsonParser.parseString(renderedJson).getAsJsonObject();
        assertTrue(root.has("nodes"), "Root JSON must contain 'nodes' array");
        assertTrue(root.has("edges"), "Root JSON must contain 'edges' array");

        JsonArray nodes = root.getAsJsonArray("nodes");
        assertEquals(3, nodes.size(), "Rendered JSON must contain 3 nodes");

        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = nodes.get(i).getAsJsonObject();
            assertTrue(node.has("x"), "Node object MUST contain 'x' position property");
            assertTrue(node.has("y"), "Node object MUST contain 'y' position property");
            assertNotNull(node.get("x"));
            assertNotNull(node.get("y"));
        }
    }
}
