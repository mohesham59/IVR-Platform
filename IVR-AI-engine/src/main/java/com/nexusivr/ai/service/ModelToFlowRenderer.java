package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Renders an Internal Flow Model into the React Flow Builder JSON format.
 * <p>
 * This is the ONLY place that converts the model to frontend JSON.
 * The React Flow Canvas never receives AI-generated JSON directly;
 * it always receives a rendered view of the Internal Flow Model.
 */
public class ModelToFlowRenderer {

    private static final Logger logger = LoggerFactory.getLogger(ModelToFlowRenderer.class);
    private static final Gson GSON = new Gson();

    private static final Map<String, String> DTMF_KEY_MAP = Map.ofEntries(
            Map.entry("0", "key0"),
            Map.entry("1", "key1"),
            Map.entry("2", "key2"),
            Map.entry("3", "key3"),
            Map.entry("4", "key4"),
            Map.entry("5", "key5"),
            Map.entry("6", "key6"),
            Map.entry("7", "key7"),
            Map.entry("8", "key8"),
            Map.entry("9", "key9"),
            Map.entry("*", "keyStar"),
            Map.entry("#", "keyPound")
    );

    public String render(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) {
            return minimalFlowJson();
        }

        JsonArray nodes = new JsonArray();
        JsonArray edges = new JsonArray();

        Map<String, JsonObject> nodeMap = new LinkedHashMap<>();

        int index = 0;
        for (FlowNode flowNode : model.getNodes()) {
            JsonObject node = nodeToJson(flowNode, index++);
            nodes.add(node);
            nodeMap.put(flowNode.getId(), node);
        }

        for (FlowConnection conn : model.getConnections()) {
            JsonObject sourceNode = nodeMap.get(conn.getSourceNodeId());
            JsonObject targetNode = nodeMap.get(conn.getTargetNodeId());
            if (sourceNode == null || targetNode == null) {
                continue;
            }

            String sourceType = sourceNode.get("type").getAsString();
            if ("end".equalsIgnoreCase(sourceType) || "disconnect".equalsIgnoreCase(sourceType)) {
                continue;
            }

            edges.add(connectionToJson(conn, sourceNode, targetNode));
        }

        JsonObject root = new JsonObject();
        root.addProperty("name", model.getName() != null ? model.getName() : "Generated IVR Flow");
        root.addProperty("description", model.getDescription() != null ? model.getDescription() : "");
        root.add("nodes", nodes);
        root.add("edges", edges);

        return GSON.toJson(root);
    }

    private JsonObject nodeToJson(FlowNode node, int index) {
        JsonObject json = new JsonObject();
        json.addProperty("id", node.getId());
        json.addProperty("type", node.getType().getBuilderType());
        json.addProperty("title", node.getTitle() != null ? node.getTitle() : node.getId());
        json.addProperty("subtitle", node.getSubtitle() != null ? node.getSubtitle() : "");
        json.addProperty("x", 100 + (index % 5) * 200);
        json.addProperty("y", 100 + (index / 5) * 150);
        if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
            json.addProperty("prompt", node.getPrompt().getText());
        }

        JsonArray ports = new JsonArray();

        switch (node.getType()) {
            case START -> {
                ports.add(createPort("out", "Continue"));
            }
            case END -> {
                // no output ports
            }
            case MENU -> {
                if (node.getMenu() != null && node.getMenu().getChoices() != null) {
                    for (FlowChoice choice : node.getMenu().getChoices()) {
                        String key = choice.getKey();
                        String label = choice.getLabel();
                        if (label == null || label.isBlank()) {
                            label = "Option " + key;
                        }
                        ports.add(createPort(key, label));
                    }
                }
                ports.add(createPort("timeout", "Timeout"));
            }
            case INPUT -> {
                ports.add(createPort("success", "Got Input"));
                ports.add(createPort("timeout", "Timeout"));
            }
            case TRANSFER -> {
                ports.add(createPort("success", "Transferred"));
                ports.add(createPort("fail", "Failed"));
                if (node.getTransfer() != null && node.getTransfer().getDestination() != null) {
                    json.addProperty("transferDestination", node.getTransfer().getDestination());
                }
            }
            case QUEUE -> {
                ports.add(createPort("answered", "Answered"));
                ports.add(createPort("abandoned", "Abandoned"));
                ports.add(createPort("overflow", "Overflow"));
            }
            case CONDITION -> {
                ports.add(createPort("true", "True"));
                ports.add(createPort("false", "False"));
            }
            case BUSINESS_HOURS -> {
                ports.add(createPort("open", "Open"));
                ports.add(createPort("closed", "Closed"));
            }
            case HOLIDAY -> {
                ports.add(createPort("holiday", "Holiday"));
                ports.add(createPort("normal", "Normal Day"));
            }
            case API -> {
                ports.add(createPort("success", "Success"));
                ports.add(createPort("error", "Error"));
            }
            case DATABASE -> {
                ports.add(createPort("found", "Found"));
                ports.add(createPort("notfound", "Not Found"));
            }
            case AI -> {
                ports.add(createPort("resolved", "Resolved"));
                ports.add(createPort("escalate", "Escalate"));
            }
            case VOICEMAIL -> {
                ports.add(createPort("done", "Recorded"));
            }
            case WEBHOOK -> {
                ports.add(createPort("success", "Sent"));
                ports.add(createPort("error", "Failed"));
            }
            default -> {
                ports.add(createPort("out", "Continue"));
            }
        }

        json.add("ports", ports);
        return json;
    }

    private JsonObject createPort(String id, String label) {
        JsonObject port = new JsonObject();
        port.addProperty("id", id);
        port.addProperty("label", label);
        return port;
    }

    private JsonObject connectionToJson(FlowConnection conn, JsonObject sourceNode, JsonObject targetNode) {
        JsonObject edge = new JsonObject();
        edge.addProperty("id", conn.getId() != null ? conn.getId() : "e_" + conn.getSourceNodeId() + "_" + conn.getTargetNodeId());
        edge.addProperty("sourceId", conn.getSourceNodeId());
        edge.addProperty("sourcePort", conn.getSourcePort() != null ? conn.getSourcePort() : "out");
        edge.addProperty("targetId", conn.getTargetNodeId());
        edge.addProperty("targetPort", conn.getTargetPort() != null ? conn.getTargetPort() : "in");

        String label = conn.getLabel();
        if (label == null || label.isBlank()) {
            String targetTitle = targetNode.has("title") ? targetNode.get("title").getAsString() : conn.getTargetNodeId();
            label = "Go to " + targetTitle;
        }
        edge.addProperty("label", label);

        return edge;
    }

    private String minimalFlowJson() {
        JsonObject root = new JsonObject();
        root.addProperty("name", "Generated IVR Flow");
        root.addProperty("description", "Auto-generated flow");

        JsonArray nodes = new JsonArray();
        JsonObject start = new JsonObject();
        start.addProperty("id", "start");
        start.addProperty("type", "start");
        start.addProperty("title", "Start");
        start.addProperty("subtitle", "Entry Point");
        start.addProperty("x", 100);
        start.addProperty("y", 100);
        JsonArray startPorts = new JsonArray();
        startPorts.add(createPort("out", "Continue"));
        start.add("ports", startPorts);
        nodes.add(start);

        JsonObject end = new JsonObject();
        end.addProperty("id", "end");
        end.addProperty("type", "end");
        end.addProperty("title", "End Call");
        end.addProperty("subtitle", "Hang up");
        end.addProperty("x", 300);
        end.addProperty("y", 100);
        JsonArray endPorts = new JsonArray();
        end.add("ports", endPorts);
        nodes.add(end);

        JsonArray edges = new JsonArray();
        JsonObject edge = new JsonObject();
        edge.addProperty("id", "e_start_end");
        edge.addProperty("sourceId", "start");
        edge.addProperty("sourcePort", "out");
        edge.addProperty("targetId", "end");
        edge.addProperty("targetPort", "in");
        edge.addProperty("label", "Continue");
        edges.add(edge);

        root.add("nodes", nodes);
        root.add("edges", edges);
        return GSON.toJson(root);
    }
}
