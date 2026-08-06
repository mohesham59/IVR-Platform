package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing the active IVR flow context on a per-session basis.
 *
 * <p>Provides operations for saving, retrieving, updating, and clearing flow contexts,
 * acting as the single source of truth for flow-related operations.
 *
 * <p>All AI features and chat assistants use this service to access or modify flow data.
 * Flow data is stored as {@link FlowModel} (Internal Flow Model) in {@link SessionMemoryStore}.
 * React Flow JSON is rendered on demand from the model.
 */
public class FlowContextService {

    private static final Logger logger = LoggerFactory.getLogger(FlowContextService.class);

    /**
     * Saves the active flow for a session as an Internal Flow Model.
     * <p>
     * If the input is a String, it will be auto-detected and converted:
     * <ul>
     *   <li>VoiceXML → converted via {@link VxmlToModelConverter}</li>
     *   <li>React Flow JSON → converted to a minimal FlowModel</li>
     * </ul>
     * Clears any outdated flow structure for this session first.
     *
     * @param sessionId the active chat session UUID
     * @param flowModel the Internal Flow Model to store
     */
    public void saveActiveFlow(UUID sessionId, FlowModel flowModel) {
        if (sessionId == null || flowModel == null) {
            return;
        }
        clearOutdatedFlows(sessionId);
        SessionMemoryStore.saveModel(sessionId, flowModel);
        logFlowDetails("SaveActiveFlow", sessionId, flowModel);
    }

    /**
     * Saves the active flow for a session from a raw string (VoiceXML or React Flow JSON).
     * <p>
     * The string is converted to a FlowModel before storage.
     *
     * @param sessionId the active chat session UUID
     * @param flowData  VoiceXML or React Flow JSON string
     */
    public void saveActiveFlow(UUID sessionId, String flowData) {
        if (sessionId == null || flowData == null || flowData.isBlank()) {
            return;
        }
        String trimmed = flowData.trim();
        FlowModel model = null;
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<vxml")) {
            model = convertVxmlToModel(flowData);
        } else if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            model = convertJsonToModel(flowData);
        } else {
            logger.warn("[FlowContextService] Unrecognized flow format for session {}. Expected VXML or JSON.", sessionId);
        }
        if (model != null) {
            saveActiveFlow(sessionId, model);
        } else {
            logger.warn("[FlowContextService] Could not convert flow data to FlowModel for session {}. Flow not stored.", sessionId);
        }
    }

    /**
     * Retrieves the active flow as React Flow JSON for a session.
     * <p>
     * The JSON is rendered on demand from the stored Internal Flow Model.
     *
     * @param sessionId the active chat session UUID
     * @return React Flow JSON string, or null if no flow has been generated
     */
    public String getActiveFlow(UUID sessionId) {
        if (sessionId == null) return null;
        return SessionMemoryStore.getFlowJson(sessionId);
    }

    /**
     * Returns the stored Internal Flow Model for a session.
     *
     * @param sessionId the active chat session UUID
     * @return FlowModel, or null if no flow has been generated
     */
    public FlowModel getActiveFlowModel(UUID sessionId) {
        if (sessionId == null) return null;
        return SessionMemoryStore.getFlowModel(sessionId);
    }

    /**
     * Updates the active flow context for a session with a FlowModel.
     *
     * @param sessionId the active chat session UUID
     * @param flowModel the Internal Flow Model to store
     */
    public void updateFlowContext(UUID sessionId, FlowModel flowModel) {
        if (sessionId == null || flowModel == null) return;
        SessionMemoryStore.saveModel(sessionId, flowModel);
        logFlowDetails("UpdateFlowContext", sessionId, flowModel);
    }

    /**
     * Updates the active flow context for a session from a raw string.
     *
     * @param sessionId the active chat session UUID
     * @param flowData  VoiceXML or React Flow JSON string
     */
    public void updateFlowContext(UUID sessionId, String flowData) {
        if (sessionId == null || flowData == null || flowData.isBlank()) {
            return;
        }
        String trimmed = flowData.trim();
        FlowModel model = null;
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<vxml")) {
            model = convertVxmlToModel(flowData);
        } else if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            model = convertJsonToModel(flowData);
        } else {
            logger.warn("[FlowContextService] Unrecognized flow format for session {}. Expected VXML or JSON.", sessionId);
        }
        if (model != null) {
            updateFlowContext(sessionId, model);
        } else {
            logger.warn("[FlowContextService] Could not convert flow data to FlowModel for session {}. Flow not updated.", sessionId);
        }
    }

    /**
     * Clears outdated flow context for a session.
     */
    public void clearOutdatedFlows(UUID sessionId) {
        if (sessionId == null) return;
        SessionMemoryStore.clear(sessionId);
        logger.info("[FlowContextService] Cleared outdated flow context for session {}", sessionId);
    }

    // ----------------------------------------------------------------
    // Format conversion
    // ----------------------------------------------------------------

    /**
     * Converts a VoiceXML string to a FlowModel.
     *
     * @param vxml VoiceXML string
     * @return FlowModel, or null if conversion fails
     */
    public static FlowModel convertVxmlToModel(String vxml) {
        if (vxml == null || vxml.isBlank()) {
            return null;
        }
        try {
            String sanitized = stripMarkdownCodeFences(vxml.trim());
            return new VxmlToModelConverter().convert(sanitized);
        } catch (Exception e) {
            logger.warn("[FlowContextService] Failed to convert VoiceXML to FlowModel: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converts React Flow JSON to a minimal FlowModel.
     * <p>
     * This handles the case where the frontend sends updated flow JSON.
     * The JSON is expected to have {@code nodes} and {@code edges} arrays.
     */
    public static FlowModel convertJsonToModel(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("connections") && !obj.has("edges")) {
                FlowModel directModel = new com.google.gson.Gson().fromJson(json, FlowModel.class);
                if (directModel != null && directModel.getNodes() != null) {
                    return directModel;
                }
            }

            FlowModel model = new FlowModel();
            model.setName(obj.has("name") ? obj.get("name").getAsString() : "Frontend Flow");
            model.setDescription("Imported from React Flow JSON");

            if (obj.has("nodes") && obj.get("nodes").isJsonArray()) {
                com.google.gson.JsonArray nodes = obj.getAsJsonArray("nodes");
                for (int i = 0; i < nodes.size(); i++) {
                    com.google.gson.JsonObject nodeObj = nodes.get(i).getAsJsonObject();
                    String id = nodeObj.has("id") ? nodeObj.get("id").getAsString() : "n" + (i + 1);
                    String typeStr = nodeObj.has("type") ? nodeObj.get("type").getAsString() : "prompt";
                    FlowNodeType type = FlowNodeType.fromString(typeStr);
                    if (type == null) {
                        type = FlowNodeType.PROMPT;
                    }
                    String title = nodeObj.has("title") ? nodeObj.get("title").getAsString() :
                                   nodeObj.has("label") ? nodeObj.get("label").getAsString() :
                                   nodeObj.has("name") ? nodeObj.get("name").getAsString() : id;
                    FlowNode node = new FlowNode(id, type, title);
                    if (nodeObj.has("subtitle")) {
                        node.setSubtitle(nodeObj.get("subtitle").getAsString());
                    }
                    if (nodeObj.has("prompt")) {
                        com.nexusivr.ai.model.flow.FlowPrompt prompt = new com.nexusivr.ai.model.flow.FlowPrompt();
                        prompt.setText(nodeObj.get("prompt").getAsString());
                        node.setPrompt(prompt);
                    } else if (nodeObj.has("subtitle")) {
                        com.nexusivr.ai.model.flow.FlowPrompt prompt = new com.nexusivr.ai.model.flow.FlowPrompt();
                        prompt.setText(nodeObj.get("subtitle").getAsString());
                        node.setPrompt(prompt);
                    }

                    if (type == FlowNodeType.MENU && nodeObj.has("ports") && nodeObj.get("ports").isJsonArray()) {
                        com.google.gson.JsonArray ports = nodeObj.getAsJsonArray("ports");
                        com.nexusivr.ai.model.flow.FlowMenu menu = new com.nexusivr.ai.model.flow.FlowMenu();
                        for (int p = 0; p < ports.size(); p++) {
                            com.google.gson.JsonObject port = ports.get(p).getAsJsonObject();
                            String portId = port.has("id") ? port.get("id").getAsString() : "key" + (p + 1);
                            String portLabel = port.has("label") ? port.get("label").getAsString() : "Option " + portId;
                            menu.addChoice(new com.nexusivr.ai.model.flow.FlowChoice(portId, portLabel, ""));
                        }
                        node.setMenu(menu);
                    }
                    model.addNode(node);
                }
            }

            if (obj.has("edges") && obj.get("edges").isJsonArray()) {
                com.google.gson.JsonArray edges = obj.getAsJsonArray("edges");
                for (int i = 0; i < edges.size(); i++) {
                    com.google.gson.JsonObject edgeObj = edges.get(i).getAsJsonObject();
                    String id = edgeObj.has("id") ? edgeObj.get("id").getAsString() : "e" + (i + 1);
                    String source = edgeObj.has("source") ? edgeObj.get("source").getAsString() :
                                    edgeObj.has("sourceId") ? edgeObj.get("sourceId").getAsString() : "";
                    String sourcePort = edgeObj.has("sourcePort") ? edgeObj.get("sourcePort").getAsString() : "out";
                    String target = edgeObj.has("target") ? edgeObj.get("target").getAsString() :
                                    edgeObj.has("targetId") ? edgeObj.get("targetId").getAsString() : "";
                    String targetPort = edgeObj.has("targetPort") ? edgeObj.get("targetPort").getAsString() : "in";
                    if (!source.isBlank() && !target.isBlank()) {
                        model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(id, source, sourcePort, target, targetPort));
                    }
                }
            }

            return model;
        } catch (Exception e) {
            logger.warn("[FlowContextService] Failed to convert React Flow JSON to FlowModel: {}", e.getMessage());
            return null;
        }
    }

    // ----------------------------------------------------------------
    // Debugging / logging helpers
    // ----------------------------------------------------------------

    private void logFlowDetails(String action, UUID sessionId, FlowModel flowModel) {
        if (flowModel == null) return;
        String flowId = flowModel.getId() != null ? flowModel.getId() : "unknown-flow-id";
        int nodeCount = flowModel.getNodes().size();
        List<String> nodeIds = new ArrayList<>();
        List<String> nodeNames = new ArrayList<>();
        for (FlowNode node : flowModel.getNodes()) {
            nodeIds.add(node.getId());
            nodeNames.add(resolveLabel(node));
        }
        logger.info("[FlowContextService - {}] Session ID: {}, Flow ID: {}, Nodes Count: {}, Node IDs: {}, Node Names: {}",
                action, sessionId, flowId, nodeCount, nodeIds, nodeNames);
    }

    private void logFlowDetails(String action, UUID sessionId, String flowJson) {
        FlowModel model = SessionMemoryStore.getFlowModel(sessionId);
        if (model != null) {
            logFlowDetails(action, sessionId, model);
        } else {
            logger.warn("[FlowContextService] Cannot log flow details: no FlowModel stored for session {}", sessionId);
        }
    }

    private String resolveLabel(FlowNode node) {
        String label = node.getTitle();
        if (label == null || label.isBlank()) {
            label = node.getSubtitle();
        }
        if (label == null || label.isBlank()) {
            label = node.getId();
        }
        return label != null ? label : "Unnamed Node";
    }

    private static String stripMarkdownCodeFences(String vxml) {
        String trimmed = vxml.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                int closingFence = trimmed.lastIndexOf("```");
                if (closingFence > firstNewline) {
                    return trimmed.substring(firstNewline + 1, closingFence).trim();
                }
            }
        }
        return trimmed;
    }
}
