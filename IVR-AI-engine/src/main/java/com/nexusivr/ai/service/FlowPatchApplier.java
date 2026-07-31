package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.patch.*;
import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic patch applier for Internal Flow Models.
 * <p>
 * Applies a list of {@link FlowPatchOperation} to a {@link FlowModel}
 * and returns the modified model. Never uses AI.
 * </p>
 */
public class FlowPatchApplier {

    private static final Logger logger = LoggerFactory.getLogger(FlowPatchApplier.class);

    public FlowModel apply(FlowModel model, List<FlowPatchOperation> patches) {
        if (model == null) {
            model = new FlowModel();
        }
        if (patches == null || patches.isEmpty()) {
            return model;
        }

        logger.info("[FlowPatchApplier] Applying {} patch(es) to model with {} nodes, {} connections",
                patches.size(), model.getNodes().size(), model.getConnections().size());

        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : model.getNodes()) {
            nodeMap.put(node.getId(), node);
        }

        for (FlowPatchOperation patch : patches) {
            try {
                applyPatch(model, nodeMap, patch);
            } catch (Exception e) {
                logger.error("[FlowPatchApplier] Failed to apply patch {}: {}", patch.getType(), e.getMessage(), e);
            }
        }

        logger.info("[FlowPatchApplier] Patch application complete. Model now has {} nodes, {} connections",
                model.getNodes().size(), model.getConnections().size());

        return model;
    }

    private void applyPatch(FlowModel model, Map<String, FlowNode> nodeMap, FlowPatchOperation patch) {
        switch (patch.getType()) {
            case "ADD_NODE" -> applyAddNode(model, nodeMap, (AddNodePatch) patch);
            case "DELETE_NODE" -> applyDeleteNode(model, nodeMap, (DeleteNodePatch) patch);
            case "ADD_EDGE" -> applyAddEdge(model, nodeMap, (AddEdgePatch) patch);
            case "DELETE_EDGE" -> applyDeleteEdge(model, (DeleteEdgePatch) patch);
            case "RENAME_NODE" -> applyRenameNode(model, nodeMap, (RenameNodePatch) patch);
            case "UPDATE_PROMPT" -> applyUpdatePrompt(model, nodeMap, (UpdatePromptPatch) patch);
            case "CHANGE_MENU_OPTION" -> applyChangeMenuOption(model, nodeMap, (ChangeMenuOptionPatch) patch);
            case "MOVE_SUBTREE" -> applyMoveSubtree(model, nodeMap, (MoveSubtreePatch) patch);
            default -> logger.warn("[FlowPatchApplier] Unknown patch type: {}", patch.getType());
        }
    }

    private void applyAddNode(FlowModel model, Map<String, FlowNode> nodeMap, AddNodePatch patch) {
        String newNodeId = patch.getNewNodeId();
        if (newNodeId == null || newNodeId.isBlank()) {
            newNodeId = "n_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (nodeMap.containsKey(newNodeId)) {
            logger.warn("[FlowPatchApplier] ADD_NODE skipped: node '{}' already exists", newNodeId);
            return;
        }

        String nodeTypeStr = patch.getNodeType();
        if (nodeTypeStr == null || nodeTypeStr.isBlank()) {
            nodeTypeStr = "prompt";
        }
        FlowNodeType nodeType = FlowNodeType.fromString(nodeTypeStr.toLowerCase());
        if (nodeType == null) {
            nodeType = FlowNodeType.PROMPT;
        }

        FlowNode newNode = new FlowNode(newNodeId, nodeType, patch.getTitle() != null ? patch.getTitle() : newNodeId);
        if (patch.getPromptText() != null && !patch.getPromptText().isBlank()) {
            newNode.setPrompt(new FlowPrompt(patch.getPromptText()));
        }

        if (nodeType == FlowNodeType.MENU && newNode.getMenu() == null) {
            newNode.setMenu(new FlowMenu());
        } else if (nodeType == FlowNodeType.CONDITION && newNode.getCondition() == null) {
            newNode.setCondition(new FlowCondition());
        }

        model.addNode(newNode);
        nodeMap.put(newNodeId, newNode);

        if (patch.getTargetNodeId() != null && !patch.getTargetNodeId().isBlank()) {
            String sourcePort = resolveValidSourcePort(newNode, patch.getSourcePort(), patch.getTargetNodeId());
            model.addConnection(new FlowConnection(
                    "c_" + newNodeId + "_" + patch.getTargetNodeId(),
                    newNodeId,
                    sourcePort,
                    patch.getTargetNodeId(),
                    "in"
            ));
        }

        logger.info("[FlowPatchApplier] Added node '{}' of type {}", newNodeId, nodeType);
    }

    private void applyDeleteNode(FlowModel model, Map<String, FlowNode> nodeMap, DeleteNodePatch patch) {
        String nodeIdToDelete = patch.getNodeIdToDelete();
        if (nodeIdToDelete == null || !nodeMap.containsKey(nodeIdToDelete)) {
            logger.warn("[FlowPatchApplier] DELETE_NODE skipped: node '{}' not found", nodeIdToDelete);
            return;
        }

        FlowNode node = nodeMap.get(nodeIdToDelete);
        if (node.getType() == FlowNodeType.START) {
            logger.warn("[FlowPatchApplier] DELETE_NODE skipped: cannot delete START node '{}'", nodeIdToDelete);
            return;
        }

        model.getNodes().removeIf(n -> n.getId().equals(nodeIdToDelete));
        model.getConnections().removeIf(c -> c.getSourceNodeId().equals(nodeIdToDelete) || c.getTargetNodeId().equals(nodeIdToDelete));
        nodeMap.remove(nodeIdToDelete);

        logger.info("[FlowPatchApplier] Deleted node '{}'", nodeIdToDelete);
    }

    private void applyAddEdge(FlowModel model, Map<String, FlowNode> nodeMap, AddEdgePatch patch) {
        String sourceId = patch.getSourceNodeId();
        String targetId = patch.getTargetNodeId();
        if (sourceId == null || !nodeMap.containsKey(sourceId)) {
            logger.warn("[FlowPatchApplier] ADD_EDGE skipped: source node '{}' not found", sourceId);
            return;
        }
        if (targetId == null || !nodeMap.containsKey(targetId)) {
            logger.warn("[FlowPatchApplier] ADD_EDGE skipped: target node '{}' not found", targetId);
            return;
        }

        FlowNode sourceNode = nodeMap.get(sourceId);
        String resolvedSourcePort = resolveValidSourcePort(sourceNode, patch.getSourcePort(), targetId);

        if (sourceNode != null) {
            FlowNodeType type = sourceNode.getType();
            if (type == FlowNodeType.MENU) {
                model.getConnections().removeIf(c -> c.getSourceNodeId().equals(sourceId) &&
                        (c.getTargetNodeId().equals(targetId) || "out".equalsIgnoreCase(c.getSourcePort()) || !c.getSourcePort().startsWith("key")));
            } else if (type == FlowNodeType.CONDITION) {
                model.getConnections().removeIf(c -> c.getSourceNodeId().equals(sourceId) &&
                        (c.getTargetNodeId().equals(targetId) || "out".equalsIgnoreCase(c.getSourcePort()) || (!"true".equalsIgnoreCase(c.getSourcePort()) && !"false".equalsIgnoreCase(c.getSourcePort()))));
            }
        }

        String edgeId = patch.getEdgeId();
        if (edgeId == null || edgeId.isBlank()) {
            edgeId = "c_" + sourceId + "_" + targetId + "_" + resolvedSourcePort;
        }

        final String finalEdgeId = edgeId;
        boolean exists = model.getConnections().stream()
                .anyMatch(c -> finalEdgeId.equals(c.getId()) ||
                        (Objects.equals(c.getSourceNodeId(), sourceId) &&
                         Objects.equals(c.getSourcePort(), resolvedSourcePort) &&
                         Objects.equals(c.getTargetNodeId(), targetId)));
        if (exists) {
            logger.warn("[FlowPatchApplier] ADD_EDGE skipped: edge from '{}' (port '{}') to '{}' already exists", sourceId, resolvedSourcePort, targetId);
            return;
        }

        model.addConnection(new FlowConnection(
                edgeId,
                sourceId,
                resolvedSourcePort,
                targetId,
                patch.getTargetPort() != null ? patch.getTargetPort() : "in"
        ));

        logger.info("[FlowPatchApplier] Added edge '{}' from '{}' (port '{}') to '{}'", edgeId, sourceId, resolvedSourcePort, targetId);
    }

    private String resolveValidSourcePort(FlowNode sourceNode, String requestedPort, String targetId) {
        if (sourceNode == null) return requestedPort != null ? requestedPort : "out";
        FlowNodeType type = sourceNode.getType();
        if (type == FlowNodeType.MENU) {
            if (sourceNode.getMenu() == null) {
                sourceNode.setMenu(new FlowMenu());
            }
            List<FlowChoice> choices = sourceNode.getMenu().getChoices();
            if (requestedPort != null && requestedPort.startsWith("key")) {
                boolean exists = choices.stream().anyMatch(c -> requestedPort.equals(c.getKey()));
                if (!exists) {
                    String dtmf = requestedPort.replace("key", "");
                    if (!dtmf.matches("\\d")) dtmf = String.valueOf(choices.size() + 1);
                    FlowChoice choice = new FlowChoice(requestedPort, "Option " + dtmf, targetId);
                    choice.setDtmf(dtmf);
                    sourceNode.getMenu().addChoice(choice);
                }
                return requestedPort;
            } else {
                Set<String> usedDtmf = choices.stream()
                        .map(FlowChoice::getDtmf)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                int nextIndex = choices.size() + 1;
                String nextKey = "key" + nextIndex;
                String nextDtmf = findAvailableDigit(usedDtmf);
                FlowChoice choice = new FlowChoice(nextKey, "Option " + nextIndex, targetId);
                choice.setDtmf(nextDtmf);
                sourceNode.getMenu().addChoice(choice);
                return nextKey;
            }
        } else if (type == FlowNodeType.CONDITION) {
            if (sourceNode.getCondition() == null) {
                sourceNode.setCondition(new FlowCondition());
            }
            if ("true".equalsIgnoreCase(requestedPort) || "false".equalsIgnoreCase(requestedPort)) {
                if ("true".equalsIgnoreCase(requestedPort)) {
                    sourceNode.getCondition().setTrueTargetNodeId(targetId);
                    return "true";
                } else {
                    sourceNode.getCondition().setFalseTargetNodeId(targetId);
                    return "false";
                }
            } else {
                if (sourceNode.getCondition().getTrueTargetNodeId() == null) {
                    sourceNode.getCondition().setTrueTargetNodeId(targetId);
                    return "true";
                } else {
                    sourceNode.getCondition().setFalseTargetNodeId(targetId);
                    return "false";
                }
            }
        } else {
            return (requestedPort != null && !requestedPort.isBlank()) ? requestedPort : "out";
        }
    }

    private String findAvailableDigit(Set<String> used) {
        for (char d = '1'; d <= '9'; d++) {
            if (!used.contains(String.valueOf(d))) return String.valueOf(d);
        }
        if (!used.contains("0")) return "0";
        return "1";
    }

    private void applyDeleteEdge(FlowModel model, DeleteEdgePatch patch) {
        String edgeId = patch.getEdgeId();
        if (edgeId == null || edgeId.isBlank()) {
            if (patch.getSourceNodeId() != null && patch.getTargetNodeId() != null) {
                model.getConnections().removeIf(c ->
                        patch.getSourceNodeId().equals(c.getSourceNodeId()) &&
                        patch.getTargetNodeId().equals(c.getTargetNodeId())
                );
                logger.info("[FlowPatchApplier] Deleted edge from '{}' to '{}'", patch.getSourceNodeId(), patch.getTargetNodeId());
                return;
            }
            return;
        }

        boolean removed = model.getConnections().removeIf(c -> edgeId.equals(c.getId()));
        if (removed) {
            logger.info("[FlowPatchApplier] Deleted edge '{}'", edgeId);
        }
    }

    private void applyRenameNode(FlowModel model, Map<String, FlowNode> nodeMap, RenameNodePatch patch) {
        String nodeId = patch.getNodeId();
        if (nodeId == null || !nodeMap.containsKey(nodeId)) {
            logger.warn("[FlowPatchApplier] RENAME_NODE skipped: node '{}' not found", nodeId);
            return;
        }
        FlowNode node = nodeMap.get(nodeId);
        node.setTitle(patch.getNewTitle());
        logger.info("[FlowPatchApplier] Renamed node '{}' to '{}'", nodeId, patch.getNewTitle());
    }

    private void applyUpdatePrompt(FlowModel model, Map<String, FlowNode> nodeMap, UpdatePromptPatch patch) {
        String nodeId = patch.getNodeId();
        if (nodeId == null || !nodeMap.containsKey(nodeId)) {
            logger.warn("[FlowPatchApplier] UPDATE_PROMPT skipped: node '{}' not found", nodeId);
            return;
        }
        FlowNode node = nodeMap.get(nodeId);
        node.setPrompt(new FlowPrompt(patch.getNewPromptText()));
        logger.info("[FlowPatchApplier] Updated prompt on node '{}'", nodeId);
    }

    private void applyChangeMenuOption(FlowModel model, Map<String, FlowNode> nodeMap, ChangeMenuOptionPatch patch) {
        String nodeId = patch.getNodeId();
        if (nodeId == null || !nodeMap.containsKey(nodeId)) {
            logger.warn("[FlowPatchApplier] CHANGE_MENU_OPTION skipped: node '{}' not found", nodeId);
            return;
        }

        FlowNode node = nodeMap.get(nodeId);
        if (node.getType() != FlowNodeType.MENU) {
            logger.info("[FlowPatchApplier] Upgrading node '{}' to MENU type for CHANGE_MENU_OPTION patch", nodeId);
            node.setType(FlowNodeType.MENU);
        }
        if (node.getMenu() == null) {
            node.setMenu(new FlowMenu());
        }

        FlowMenu menu = node.getMenu();
        String action = patch.getAction() != null ? patch.getAction().toUpperCase() : "ADD";
        String dtmf = patch.getDtmf();
        String label = patch.getLabel();
        String target = patch.getTargetNodeId();

        switch (action) {
            case "REMOVE" -> {
                if (dtmf != null) {
                    menu.getChoices().removeIf(c -> dtmf.equals(c.getDtmf()));
                }
            }
            case "UPDATE" -> {
                if (dtmf != null) {
                    for (FlowChoice choice : menu.getChoices()) {
                        if (dtmf.equals(choice.getDtmf())) {
                            if (label != null) choice.setLabel(label);
                            if (target != null) choice.setTargetNodeId(target);
                            break;
                        }
                    }
                }
            }
            default -> {
                FlowChoice newChoice = new FlowChoice("key" + (dtmf != null ? dtmf : "1"), label != null ? label : "Option", target);
                if (dtmf != null) newChoice.setDtmf(dtmf);
                menu.addChoice(newChoice);
            }
        }

        logger.info("[FlowPatchApplier] {} menu option in '{}'", action, nodeId);
    }

    private void applyMoveSubtree(FlowModel model, Map<String, FlowNode> nodeMap, MoveSubtreePatch patch) {
        String nodeId = patch.getNodeId();
        String newParentId = patch.getNewParentNodeId();
        if (nodeId == null || !nodeMap.containsKey(nodeId)) {
            logger.warn("[FlowPatchApplier] MOVE_SUBTREE skipped: node '{}' not found", nodeId);
            return;
        }
        if (newParentId == null || !nodeMap.containsKey(newParentId)) {
            logger.warn("[FlowPatchApplier] MOVE_SUBTREE skipped: new parent '{}' not found", newParentId);
            return;
        }

        String newPort = patch.getNewSourcePort();
        if (newPort == null || newPort.isBlank()) {
            newPort = "out";
        }

        List<FlowConnection> incomingEdges = model.getConnections().stream()
                .filter(c -> nodeId.equals(c.getTargetNodeId()))
                .collect(Collectors.toList());

        for (FlowConnection incoming : incomingEdges) {
            model.getConnections().removeIf(c -> incoming.getId().equals(c.getId()));
        }

        model.addConnection(new FlowConnection(
                "c_move_" + nodeId + "_" + newParentId,
                newParentId,
                newPort,
                nodeId,
                "in"
        ));

        logger.info("[FlowPatchApplier] Moved subtree rooted at '{}' to '{}'", nodeId, newParentId);
    }
}
