package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Repairs an Internal Flow Model.
 * <p>
 * Philosophy: repair first, delete last.
 * <ol>
 *   <li>Insert missing Start node</li>
 *   <li>Insert missing End node</li>
 *   <li>Reconnect disconnected nodes</li>
 *   <li>Fix missing references</li>
 *   <li>Repair broken transitions</li>
 *   <li>Repair unreachable branches</li>
 *   <li>Only delete nodes that are truly unrecoverable</li>
 * </ol>
 */
public class ModelAutoRepair {

    private static final Logger logger = LoggerFactory.getLogger(ModelAutoRepair.class);

    public FlowModel repair(FlowModel model, FlowValidationResponse validation) {
        return repair(model, validation, List.of(), new ArrayList<>());
    }

    public FlowModel repair(FlowModel model, FlowValidationResponse validation, List<String> requestedFeatureKeywords, List<String> outDroppedFeatures) {
        if (model == null) {
            model = new FlowModel();
        }

        if (validation == null || validation.getIssues() == null || validation.getIssues().isEmpty()) {
            return model;
        }

        logger.info("[ModelAutoRepair] Starting repair with {} issues", validation.getIssues().size());

        // Phase 0: Fix duplicate node IDs before any other repair
        fixDuplicateNodeIds(model);

        // Phase 1: Insert missing structural nodes
        ensureStartNode(model);
        ensureEndNode(model);

        // Phase 2: Reconnect disconnected nodes
        reconnectOrphanNodes(model);

        // Phase 3: Fix missing references
        fixMissingReferences(model);

        // Phase 3b: Remap invalid output ports to valid ones for their node type
        remapInvalidPorts(model);

        // Phase 4: Repair broken transitions
        repairBrokenTransitions(model);

        // Phase 5: Repair unreachable branches (last resort: reconnect to nearest branch)
        repairUnreachableBranches(model);

        // Phase 6: Only now consider deletion of truly unrecoverable nodes (with safeguard for requested features)
        removeTrulyUnreachableNodes(model, requestedFeatureKeywords, outDroppedFeatures);

        logger.info("[ModelAutoRepair] Repair complete. Nodes: {}, Connections: {}",
                model.getNodes().size(), model.getConnections().size());

        return model;
    }

    private static final Map<FlowNodeType, Set<String>> VALID_PORTS = new HashMap<>();

    static {
        VALID_PORTS.put(FlowNodeType.START, Set.of("out"));
        VALID_PORTS.put(FlowNodeType.PROMPT, Set.of("out"));
        VALID_PORTS.put(FlowNodeType.MENU, Set.of("key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key0", "timeout"));
        VALID_PORTS.put(FlowNodeType.INPUT, Set.of("success", "timeout"));
        VALID_PORTS.put(FlowNodeType.TRANSFER, Set.of("success", "fail"));
        VALID_PORTS.put(FlowNodeType.QUEUE, Set.of("answered", "abandoned", "overflow"));
        VALID_PORTS.put(FlowNodeType.CONDITION, Set.of("true", "false"));
        VALID_PORTS.put(FlowNodeType.BUSINESS_HOURS, Set.of("open", "closed"));
        VALID_PORTS.put(FlowNodeType.HOLIDAY, Set.of("holiday", "normal"));
        VALID_PORTS.put(FlowNodeType.RECORDING, Set.of("out"));
        VALID_PORTS.put(FlowNodeType.API, Set.of("success", "error"));
        VALID_PORTS.put(FlowNodeType.DATABASE, Set.of("found", "notfound"));
        VALID_PORTS.put(FlowNodeType.VOICEMAIL, Set.of("done"));
        VALID_PORTS.put(FlowNodeType.WEBHOOK, Set.of("success", "error"));
        VALID_PORTS.put(FlowNodeType.AI, Set.of("resolved", "escalate"));
        VALID_PORTS.put(FlowNodeType.END, Set.of());
        VALID_PORTS.put(FlowNodeType.DISCONNECT, Set.of());
    }

    private void fixDuplicateNodeIds(FlowModel model) {
        Map<String, Integer> idCount = new HashMap<>();
        for (FlowNode node : model.getNodes()) {
            String id = node.getId();
            if (id != null && !id.isBlank()) {
                idCount.merge(id, 1, Integer::sum);
            }
        }

        int suffix = 1;
        for (FlowNode node : model.getNodes()) {
            String id = node.getId();
            if (id != null && idCount.getOrDefault(id, 0) > 1) {
                String newId = id + "_" + suffix;
                boolean exists;
                do {
                    exists = false;
                    for (FlowNode check : model.getNodes()) {
                        if (newId.equals(check.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) break;
                    suffix++;
                    newId = id + "_" + suffix;
                } while (exists);

                logger.info("[ModelAutoRepair] Renaming duplicate node ID '{}' to '{}'", id, newId);
                node.setId(newId);

                for (FlowConnection conn : model.getConnections()) {
                    if (id.equals(conn.getSourceNodeId())) {
                        conn.setSourceNodeId(newId);
                    }
                    if (id.equals(conn.getTargetNodeId())) {
                        conn.setTargetNodeId(newId);
                    }
                }
                idCount.put(newId, 1);
                suffix++;
            }
        }
    }

    private void remapInvalidPorts(FlowModel model) {
        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : model.getNodes()) {
            nodeMap.put(node.getId(), node);
        }

        List<FlowConnection> toFix = new ArrayList<>();
        for (FlowConnection conn : model.getConnections()) {
            FlowNode sourceNode = nodeMap.get(conn.getSourceNodeId());
            if (sourceNode == null) continue;

            Set<String> validPorts = VALID_PORTS.getOrDefault(sourceNode.getType(), Set.of("out"));
            String port = conn.getSourcePort();
            if (port == null || port.isBlank() || !validPorts.contains(port)) {
                toFix.add(conn);
            }
        }

        if (toFix.isEmpty()) return;

        logger.info("[ModelAutoRepair] Remapping {} invalid output port(s)", toFix.size());

        for (FlowConnection conn : toFix) {
            FlowNode sourceNode = nodeMap.get(conn.getSourceNodeId());
            if (sourceNode == null) continue;

            Set<String> validPorts = VALID_PORTS.getOrDefault(sourceNode.getType(), Set.of("out"));
            String oldPort = conn.getSourcePort();
            String newPort = validPorts.isEmpty() ? "out" : validPorts.iterator().next();

            if (oldPort.equals(newPort)) {
                logger.warn("[ModelAutoRepair] No-op remap detected on node '{}' port '{}' — skipping because new port equals old invalid port. Check VALID_PORTS for node type {}.",
                        sourceNode.getId(), oldPort, sourceNode.getType());
                continue;
            }

            conn.setSourcePort(newPort);
            logger.info("[ModelAutoRepair] Remapped invalid port on node '{}' from '{}' to '{}'",
                    sourceNode.getId(), oldPort, newPort);
        }
    }

    private void ensureStartNode(FlowModel model) {
        boolean hasStart = model.getNodes().stream()
                .anyMatch(n -> n.getType() == FlowNodeType.START);

        if (!hasStart) {
            logger.info("[ModelAutoRepair] Inserting missing Start node");
            FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start");
            startNode.setSubtitle("Entry Point");

            if (!model.getNodes().isEmpty()) {
                FlowNode firstNode = model.getNodes().get(0);
                model.addConnection(new FlowConnection("c_start_" + firstNode.getId(), "start", "out", firstNode.getId(), "in"));
            }

            model.getNodes().add(0, startNode);
        }
    }

    private void ensureEndNode(FlowModel model) {
        boolean hasEnd = model.getNodes().stream()
                .anyMatch(n -> n.getType() == FlowNodeType.END || n.getType() == FlowNodeType.DISCONNECT);

        if (!hasEnd) {
            logger.info("[ModelAutoRepair] Inserting missing End node");
            FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
            endNode.setSubtitle("Hang up");
            model.addNode(endNode);

            List<FlowNode> leafNodes = findLeafNodes(model);
            for (FlowNode leaf : leafNodes) {
                if (leaf.getType() != FlowNodeType.START && leaf.getType() != FlowNodeType.END) {
                    model.addConnection(new FlowConnection(
                            "c_" + leaf.getId() + "_end",
                            leaf.getId(),
                            "out",
                            "end",
                            "in"
                    ));
                }
            }
        }
    }

    private List<FlowNode> findLeafNodes(FlowModel model) {
        Set<String> nodesWithOutgoing = model.getConnections().stream()
                .map(FlowConnection::getSourceNodeId)
                .collect(Collectors.toSet());

        return model.getNodes().stream()
                .filter(n -> !nodesWithOutgoing.contains(n.getId()))
                .filter(n -> n.getType() != FlowNodeType.END && n.getType() != FlowNodeType.DISCONNECT && n.getType() != FlowNodeType.TRANSFER)
                .collect(Collectors.toList());
    }

    private void reconnectOrphanNodes(FlowModel model) {
        Set<String> connectedIds = new HashSet<>();
        for (FlowConnection conn : model.getConnections()) {
            connectedIds.add(conn.getSourceNodeId());
            connectedIds.add(conn.getTargetNodeId());
        }

        List<FlowNode> orphans = model.getNodes().stream()
                .filter(n -> n.getType() != FlowNodeType.START)
                .filter(n -> !connectedIds.contains(n.getId()))
                .collect(Collectors.toList());

        if (orphans.isEmpty()) {
            return;
        }

        logger.info("[ModelAutoRepair] Reconnecting {} orphan node(s)", orphans.size());

        List<FlowNode> branchNodes = model.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.MENU || n.getType() == FlowNodeType.CONDITION || n.getType() == FlowNodeType.BUSINESS_HOURS)
                .collect(Collectors.toList());

        for (FlowNode orphan : orphans) {
            boolean reconnected = false;

            for (FlowNode branch : branchNodes) {
                if (reconnectToBranch(model, orphan, branch)) {
                    reconnected = true;
                    break;
                }
            }

            if (!reconnected) {
                FlowNode endNode = model.findEndNodes().stream().findFirst().orElse(null);
                if (endNode != null) {
                    model.addConnection(new FlowConnection(
                            "c_repair_" + orphan.getId() + "_end",
                            orphan.getId(),
                            "out",
                            endNode.getId(),
                            "in"
                    ));
                    logger.info("[ModelAutoRepair] Connected orphan '{}' to End node '{}'", orphan.getId(), endNode.getId());
                } else {
                    logger.warn("[ModelAutoRepair] Could not reconnect orphan node '{}' - no branch or end node available", orphan.getId());
                }
            }
        }
    }

    private boolean reconnectToBranch(FlowModel model, FlowNode orphan, FlowNode branch) {
        if (branch.getType() == FlowNodeType.MENU && branch.getMenu() != null) {
            List<FlowChoice> choices = branch.getMenu().getChoices();
            for (int i = 0; i < choices.size(); i++) {
                FlowChoice choice = choices.get(i);
                if (choice.getTargetNodeId() == null || choice.getTargetNodeId().isBlank()) {
                    choice.setTargetNodeId(orphan.getId());
                    model.addConnection(new FlowConnection(
                            "c_repair_" + branch.getId() + "_" + orphan.getId() + "_" + i,
                            branch.getId(),
                            choice.getKey(),
                            orphan.getId(),
                            "in"
                    ));
                    logger.info("[ModelAutoRepair] Reconnected orphan '{}' to menu '{}' port '{}'",
                            orphan.getId(), branch.getId(), choice.getKey());
                    return true;
                }
            }
            if (choices.size() < 10) {
                String newKey = "key" + (choices.size() + 1);
                branch.getMenu().addChoice(new FlowChoice(newKey, orphan.getTitle() != null ? orphan.getTitle() : orphan.getId(), orphan.getId()));
                model.addConnection(new FlowConnection(
                        "c_repair_" + branch.getId() + "_" + orphan.getId(),
                        branch.getId(),
                        newKey,
                        orphan.getId(),
                        "in"
                ));
                logger.info("[ModelAutoRepair] Added new menu choice '{}' in '{}' for orphan '{}'",
                        newKey, branch.getId(), orphan.getId());
                return true;
            }
        } else if (branch.getType() == FlowNodeType.CONDITION && branch.getCondition() != null) {
            if (branch.getCondition().getTrueTargetNodeId() == null) {
                branch.getCondition().setTrueTargetNodeId(orphan.getId());
                model.addConnection(new FlowConnection(
                        "c_repair_" + branch.getId() + "_" + orphan.getId() + "_true",
                        branch.getId(),
                        "true",
                        orphan.getId(),
                        "in"
                ));
                logger.info("[ModelAutoRepair] Connected orphan '{}' to condition '{}' true branch",
                        orphan.getId(), branch.getId());
                return true;
            } else if (branch.getCondition().getFalseTargetNodeId() == null) {
                branch.getCondition().setFalseTargetNodeId(orphan.getId());
                model.addConnection(new FlowConnection(
                        "c_repair_" + branch.getId() + "_" + orphan.getId() + "_false",
                        branch.getId(),
                        "false",
                        orphan.getId(),
                        "in"
                ));
                logger.info("[ModelAutoRepair] Connected orphan '{}' to condition '{}' false branch",
                        orphan.getId(), branch.getId());
                return true;
            }
        }

        return false;
    }

    private void fixMissingReferences(FlowModel model) {
        Set<String> validNodeIds = model.getNodes().stream()
                .map(FlowNode::getId)
                .collect(Collectors.toSet());

        List<FlowConnection> toRemove = new ArrayList<>();
        List<FlowConnection> toAdd = new ArrayList<>();

        for (FlowConnection conn : model.getConnections()) {
            if (!validNodeIds.contains(conn.getSourceNodeId()) || !validNodeIds.contains(conn.getTargetNodeId())) {
                toRemove.add(conn);
            }
        }

        model.getConnections().removeAll(toRemove);

        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.MENU && node.getMenu() != null) {
                for (FlowChoice choice : node.getMenu().getChoices()) {
                    if (choice.getTargetNodeId() != null && !validNodeIds.contains(choice.getTargetNodeId())) {
                        choice.setTargetNodeId(null);
                    }
                }
            }
            if (node.getType() == FlowNodeType.CONDITION && node.getCondition() != null) {
                if (node.getCondition().getTrueTargetNodeId() != null && !validNodeIds.contains(node.getCondition().getTrueTargetNodeId())) {
                    node.getCondition().setTrueTargetNodeId(null);
                }
                if (node.getCondition().getFalseTargetNodeId() != null && !validNodeIds.contains(node.getCondition().getFalseTargetNodeId())) {
                    node.getCondition().setFalseTargetNodeId(null);
                }
            }
        }
    }

    private void repairBrokenTransitions(FlowModel model) {
        List<FlowNode> leafNodes = findLeafNodes(model);
        FlowNode endNode = model.findEndNodes().stream().findFirst().orElse(null);

        if (endNode == null) {
            return;
        }

        for (FlowNode leaf : leafNodes) {
            if (leaf.getType() == FlowNodeType.START || leaf.getType() == FlowNodeType.END || leaf.getType() == FlowNodeType.TRANSFER) {
                continue;
            }
            boolean hasOutgoing = model.getConnections().stream()
                    .anyMatch(c -> c.getSourceNodeId().equals(leaf.getId()));
            if (!hasOutgoing) {
                model.addConnection(new FlowConnection(
                        "c_repair_leaf_" + leaf.getId() + "_end",
                        leaf.getId(),
                        "out",
                        endNode.getId(),
                        "in"
                ));
            }
        }
    }

    private void repairUnreachableBranches(FlowModel model) {
        String startNodeId = model.findStartNode() != null ? model.findStartNode().getId() : null;
        if (startNodeId == null) {
            return;
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        Map<String, List<FlowConnection>> adjacency = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            adjacency.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            List<FlowConnection> outgoing = adjacency.get(currentId);
            if (outgoing != null) {
                for (FlowConnection conn : outgoing) {
                    String targetId = conn.getTargetNodeId();
                    if (targetId != null && !targetId.isBlank() && !visited.contains(targetId)) {
                        visited.add(targetId);
                        queue.add(targetId);
                    }
                }
            }
        }

        List<FlowNode> unreachable = model.getNodes().stream()
                .filter(n -> !visited.contains(n.getId()))
                .filter(n -> n.getType() != FlowNodeType.START)
                .collect(Collectors.toList());

        for (FlowNode unreachableNode : unreachable) {
            boolean reconnected = false;
            for (FlowNode node : model.getNodes()) {
                if (node.getType() == FlowNodeType.MENU && node.getMenu() != null) {
                    for (FlowChoice choice : node.getMenu().getChoices()) {
                        if (choice.getTargetNodeId() == null || choice.getTargetNodeId().isBlank()) {
                            choice.setTargetNodeId(unreachableNode.getId());
                            model.addConnection(new FlowConnection(
                                    "c_repair_unreachable_" + node.getId() + "_" + unreachableNode.getId(),
                                    node.getId(),
                                    choice.getKey(),
                                    unreachableNode.getId(),
                                    "in"
                            ));
                            reconnected = true;
                            break;
                        }
                    }
                    if (reconnected) break;
                }
            }
        }
    }

    private void removeTrulyUnreachableNodes(FlowModel model, List<String> requestedFeatureKeywords, List<String> outDroppedFeatures) {
        String startNodeId = model.findStartNode() != null ? model.findStartNode().getId() : null;
        if (startNodeId == null) {
            return;
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        Map<String, List<FlowConnection>> adjacency = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            adjacency.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            List<FlowConnection> outgoing = adjacency.get(currentId);
            if (outgoing != null) {
                for (FlowConnection conn : outgoing) {
                    String targetId = conn.getTargetNodeId();
                    if (targetId != null && !targetId.isBlank() && !visited.contains(targetId)) {
                        visited.add(targetId);
                        queue.add(targetId);
                    }
                }
            }
        }

        List<FlowNode> unreachable = model.getNodes().stream()
                .filter(n -> !visited.contains(n.getId()))
                .filter(n -> n.getType() != FlowNodeType.START && n.getType() != FlowNodeType.END)
                .collect(Collectors.toList());

        if (unreachable.isEmpty()) return;

        // SAFEGUARD: First attempt reconnecting any unreachable nodes that represent requested features
        for (FlowNode unreachableNode : new ArrayList<>(unreachable)) {
            if (matchesRequestedFeature(unreachableNode, requestedFeatureKeywords)) {
                boolean reconnected = false;
                for (FlowNode branch : model.getNodes()) {
                    if (branch.getType() == FlowNodeType.MENU || branch.getType() == FlowNodeType.CONDITION) {
                        if (reconnectToBranch(model, unreachableNode, branch)) {
                            logger.info("[ModelAutoRepair] SAFEGUARD: Reconnected requested feature node '{}' ({}) to branch '{}' instead of deleting it.",
                                    unreachableNode.getId(), unreachableNode.getTitle(), branch.getId());
                            reconnected = true;
                            visited.add(unreachableNode.getId());
                            break;
                        }
                    }
                }
                if (!reconnected) {
                    // Force connect to main MENU or START node
                    FlowNode menuOrStart = model.getNodes().stream()
                            .filter(n -> n.getType() == FlowNodeType.MENU || n.getType() == FlowNodeType.START)
                            .findFirst().orElse(null);
                    if (menuOrStart != null) {
                        String port = menuOrStart.getType() == FlowNodeType.MENU
                                ? "key" + (menuOrStart.getMenu() != null ? menuOrStart.getMenu().getChoices().size() + 1 : 1)
                                : "out";
                        model.addConnection(new FlowConnection("c_repair_req_" + menuOrStart.getId() + "_" + unreachableNode.getId(),
                                menuOrStart.getId(), port, unreachableNode.getId(), "in"));
                        if (menuOrStart.getType() == FlowNodeType.MENU && menuOrStart.getMenu() != null) {
                            menuOrStart.getMenu().addChoice(new FlowChoice(port, unreachableNode.getTitle() != null ? unreachableNode.getTitle() : unreachableNode.getId(), unreachableNode.getId()));
                        }
                        logger.info("[ModelAutoRepair] SAFEGUARD: Force-connected requested feature node '{}' ({}) to '{}'.",
                                unreachableNode.getId(), unreachableNode.getTitle(), menuOrStart.getId());
                        visited.add(unreachableNode.getId());
                    }
                }
            }
        }

        // Re-evaluate unreachable nodes after safeguard reconnection
        List<FlowNode> toRemove = model.getNodes().stream()
                .filter(n -> !visited.contains(n.getId()))
                .filter(n -> n.getType() != FlowNodeType.START && n.getType() != FlowNodeType.END)
                .collect(Collectors.toList());

        if (toRemove.isEmpty()) return;

        if (toRemove.size() < model.getNodes().size() * 0.5) {
            for (FlowNode node : toRemove) {
                String name = node.getTitle() != null ? node.getTitle() : node.getId();
                if (matchesRequestedFeature(node, requestedFeatureKeywords)) {
                    if (outDroppedFeatures != null && !outDroppedFeatures.contains(name)) {
                        outDroppedFeatures.add(name);
                    }
                }
                model.getNodes().removeIf(n -> n.getId().equals(node.getId()));
                model.getConnections().removeIf(c -> c.getSourceNodeId().equals(node.getId()) || c.getTargetNodeId().equals(node.getId()));
                logger.info("[ModelAutoRepair] Removed unreachable node '{}' ({} nodes removed out of {} total)",
                        node.getId(), toRemove.size(), model.getNodes().size() + toRemove.size());
            }
        } else {
            logger.warn("[ModelAutoRepair] Refusing to delete {} nodes (more than 50% of {} total). Repairing connections instead.",
                    toRemove.size(), model.getNodes().size());
            reconnectOrphanNodes(model);
        }
    }

    private boolean matchesRequestedFeature(FlowNode node, List<String> requestedFeatureKeywords) {
        if (node == null || requestedFeatureKeywords == null || requestedFeatureKeywords.isEmpty()) return false;
        String nodeText = (node.getId() + " " + (node.getTitle() != null ? node.getTitle() : "") + " " + (node.getSubtitle() != null ? node.getSubtitle() : "")).toLowerCase(Locale.ROOT);
        for (String kw : requestedFeatureKeywords) {
            if (kw == null || kw.isBlank()) continue;
            String cleanKw = kw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
            if (cleanKw.isBlank()) continue;
            for (String word : cleanKw.split("\\s+")) {
                if (word.length() > 3 && nodeText.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }
}
