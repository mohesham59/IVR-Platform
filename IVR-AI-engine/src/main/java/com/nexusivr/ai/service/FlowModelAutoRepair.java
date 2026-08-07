package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic auto-repair for Internal Flow Models.
 * <p>
 * Never uses AI. Repairs are purely graph/structural transformations.
 * </p>
 */
public class FlowModelAutoRepair {

    private static final Logger logger = LoggerFactory.getLogger(FlowModelAutoRepair.class);

    private static final Map<FlowNodeType, Set<String>> VALID_PORTS;
    static {
        Map<FlowNodeType, Set<String>> m = new HashMap<>();
        m.put(FlowNodeType.START, Set.of("out"));
        m.put(FlowNodeType.PROMPT, Set.of("out"));
        m.put(FlowNodeType.MENU, Set.of("key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key0", "timeout"));
        m.put(FlowNodeType.INPUT, Set.of("success", "timeout", "error", "invalid"));
        m.put(FlowNodeType.TRANSFER, Set.of("success", "fail"));
        m.put(FlowNodeType.QUEUE, Set.of("answered", "abandoned", "overflow"));
        m.put(FlowNodeType.CONDITION, Set.of("true", "false"));
        m.put(FlowNodeType.BUSINESS_HOURS, Set.of("open", "closed"));
        m.put(FlowNodeType.HOLIDAY, Set.of("holiday", "normal"));
        m.put(FlowNodeType.RECORDING, Set.of("out"));
        m.put(FlowNodeType.API, Set.of("success", "error"));
        m.put(FlowNodeType.DATABASE, Set.of("found", "notfound"));
        m.put(FlowNodeType.VOICEMAIL, Set.of("done"));
        m.put(FlowNodeType.WEBHOOK, Set.of("success", "error"));
        m.put(FlowNodeType.AI, Set.of("resolved", "escalate"));
        m.put(FlowNodeType.END, Set.of());
        m.put(FlowNodeType.DISCONNECT, Set.of());
        VALID_PORTS = Collections.unmodifiableMap(m);
    }

    public FlowModel repair(FlowModel model) {
        if (model == null) {
            model = new FlowModel();
        }

        logger.info("[FlowModelAutoRepair] Starting repair. Nodes={}, Connections={}",
                model.getNodes().size(), model.getConnections().size());

        fixDuplicateIds(model);
        ensureEndNode(model);
        fixInvalidMenuOptions(model);
        reconnectOrphanNodes(model);
        fixMissingReferences(model);
        remapInvalidPorts(model);
        repairBrokenTransitions(model);
        repairUnreachableBranches(model);
        repairCollapsedBranchingHubs(model);
        breakCycles(model);

        logger.info("[FlowModelAutoRepair] Repair complete. Nodes={}, Connections={}",
                model.getNodes().size(), model.getConnections().size());

        return model;
    }

    private void breakCycles(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) return;

        Map<String, List<FlowConnection>> adjacency = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            adjacency.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        List<FlowConnection> backEdges = new ArrayList<>();

        for (FlowNode node : model.getNodes()) {
            if (!visited.contains(node.getId())) {
                findBackEdges(node.getId(), adjacency, visited, inStack, backEdges);
            }
        }

        if (backEdges.isEmpty()) return;

        logger.info("[FlowModelAutoRepair] Found {} cycle-forming back-edge(s). Breaking cycles...", backEdges.size());
        FlowNode endNode = model.findEndNodes().stream().findFirst().orElse(null);

        for (FlowConnection conn : backEdges) {
            model.getConnections().remove(conn);
            logger.info("[FlowModelAutoRepair] Removed cycle back-edge connection '{}' ({} -> {})",
                    conn.getId(), conn.getSourceNodeId(), conn.getTargetNodeId());

            FlowNode sourceNode = model.getNode(conn.getSourceNodeId());
            if (sourceNode != null) {
                if (sourceNode.getType() == FlowNodeType.CONDITION && sourceNode.getCondition() != null) {
                    if (conn.getTargetNodeId().equals(sourceNode.getCondition().getTrueTargetNodeId())) {
                        sourceNode.getCondition().setTrueTargetNodeId(endNode != null ? endNode.getId() : null);
                    }
                    if (conn.getTargetNodeId().equals(sourceNode.getCondition().getFalseTargetNodeId())) {
                        sourceNode.getCondition().setFalseTargetNodeId(endNode != null ? endNode.getId() : null);
                    }
                }
                if (sourceNode.getType() == FlowNodeType.MENU && sourceNode.getMenu() != null) {
                    for (FlowChoice choice : sourceNode.getMenu().getChoices()) {
                        if (conn.getTargetNodeId().equals(choice.getTargetNodeId())) {
                            choice.setTargetNodeId(endNode != null ? endNode.getId() : null);
                        }
                    }
                }
            }

            if (endNode != null && sourceNode != null && !conn.getSourceNodeId().equals(endNode.getId())) {
                boolean hasOtherOut = model.getConnections().stream()
                        .anyMatch(c -> c.getSourceNodeId().equals(sourceNode.getId()));
                if (!hasOtherOut) {
                    model.addConnection(new FlowConnection(
                            "c_break_cycle_" + sourceNode.getId() + "_end",
                            sourceNode.getId(),
                            conn.getSourcePort() != null ? conn.getSourcePort() : "out",
                            endNode.getId(),
                            "in"
                    ));
                    logger.info("[FlowModelAutoRepair] Redirected source '{}' to End node '{}' after cycle break",
                            sourceNode.getId(), endNode.getId());
                }
            }
        }
    }

    private void findBackEdges(String nodeId, Map<String, List<FlowConnection>> adjacency,
                               Set<String> visited, Set<String> inStack, List<FlowConnection> backEdges) {
        visited.add(nodeId);
        inStack.add(nodeId);

        List<FlowConnection> outgoing = adjacency.get(nodeId);
        if (outgoing != null) {
            for (FlowConnection conn : outgoing) {
                String target = conn.getTargetNodeId();
                if (target == null || target.isBlank()) continue;
                if (inStack.contains(target)) {
                    backEdges.add(conn);
                } else if (!visited.contains(target)) {
                    findBackEdges(target, adjacency, visited, inStack, backEdges);
                }
            }
        }

        inStack.remove(nodeId);
    }

    private void fixDuplicateIds(FlowModel model) {
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
                while (true) {
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
                }
                logger.info("[FlowModelAutoRepair] Renaming duplicate node ID '{}' to '{}'", id, newId);
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

    private void ensureEndNode(FlowModel model) {
        boolean hasEnd = model.getNodes().stream()
                .anyMatch(n -> n.getType() == FlowNodeType.END || n.getType() == FlowNodeType.DISCONNECT);

        if (!hasEnd) {
            logger.info("[FlowModelAutoRepair] Inserting missing End node");
            FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
            endNode.setSubtitle("Hang up");
            model.addNode(endNode);

            List<FlowNode> leafNodes = findLeafNodes(model);
            for (FlowNode leaf : leafNodes) {
                if (leaf.getType() != FlowNodeType.START && leaf.getType() != FlowNodeType.END && leaf.getType() != FlowNodeType.DISCONNECT) {
                    model.addConnection(new FlowConnection(
                            "c_repair_" + leaf.getId() + "_end",
                            leaf.getId(),
                            "out",
                            "end",
                            "in"
                    ));
                }
            }
        }
    }

    private void fixInvalidMenuOptions(FlowModel model) {
        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.MENU && node.getMenu() != null) {
                List<FlowChoice> choices = node.getMenu().getChoices();
                List<FlowConnection> outgoing = model.getConnections().stream()
                        .filter(c -> node.getId().equals(c.getSourceNodeId()))
                        .collect(Collectors.toList());

                // Pre-match choices to connections by index for duplicates
                Map<FlowChoice, FlowConnection> choiceToConn = new HashMap<>();
                Map<String, List<FlowConnection>> connsByPort = outgoing.stream()
                        .collect(Collectors.groupingBy(c -> c.getSourcePort() == null ? "" : c.getSourcePort()));
                Map<String, Integer> portCounts = new HashMap<>();
                for (FlowChoice choice : choices) {
                    String port = choice.getKey();
                    List<FlowConnection> conns = connsByPort.getOrDefault(port, List.of());
                    int idx = portCounts.merge(port, 0, (old, val) -> old + 1);
                    if (idx < conns.size()) {
                        choiceToConn.put(choice, conns.get(idx));
                    }
                }

                Set<String> usedKeys = new HashSet<>();

                for (int i = 0; i < choices.size(); i++) {
                    FlowChoice choice = choices.get(i);
                    String dtmf = choice.getDtmf();
                    if (dtmf == null || dtmf.isBlank() || !dtmf.matches("\\d")) {
                        String newDtmf = findAvailableDigit(usedKeys);
                        String newKey = "key" + newDtmf;
                        choice.setDtmf(newDtmf);
                        choice.setKey(newKey);
                        usedKeys.add(newDtmf);
                        logger.info("[FlowModelAutoRepair] Fixed invalid menu option DTMF in node '{}', assigned '{}'",
                                node.getId(), newDtmf);

                        // Update the corresponding connection
                        FlowConnection conn = choiceToConn.get(choice);
                        if (conn != null) {
                            conn.setSourcePort(newKey);
                        }
                    } else {
                        usedKeys.add(dtmf);
                    }

                    if (choice.getTargetNodeId() != null && !choice.getTargetNodeId().isBlank() &&
                            model.getNode(choice.getTargetNodeId()) == null) {
                        choice.setTargetNodeId(null);
                        logger.info("[FlowModelAutoRepair] Removed invalid target in menu option of node '{}'", node.getId());
                    }
                }

                Set<String> seen = new HashSet<>();
                for (FlowChoice choice : choices) {
                    String dtmf = choice.getDtmf();
                    if (dtmf == null || dtmf.isBlank() || !seen.add(dtmf)) {
                        String newDtmf = findAvailableDigit(seen);
                        String newKey = "key" + newDtmf;
                        choice.setDtmf(newDtmf);
                        choice.setKey(newKey);
                        seen.add(newDtmf);
                        logger.info("[FlowModelAutoRepair] Reassigned duplicate/invalid menu option DTMF in node '{}', assigned '{}'",
                                node.getId(), newDtmf);

                        // Update the corresponding connection
                        FlowConnection conn = choiceToConn.get(choice);
                        if (conn != null) {
                            conn.setSourcePort(newKey);
                        }
                    }
                }
            }
        }
    }

    private String findAvailableDigit(Set<String> used) {
        for (char d = '1'; d <= '9'; d++) {
            if (!used.contains(String.valueOf(d))) return String.valueOf(d);
        }
        if (!used.contains("0")) return "0";
        return "1";
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

        if (orphans.isEmpty()) return;

        logger.info("[FlowModelAutoRepair] Reconnecting {} orphan node(s)", orphans.size());

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
                    logger.info("[FlowModelAutoRepair] Connected orphan '{}' to End node '{}'",
                            orphan.getId(), endNode.getId());
                } else {
                    logger.warn("[FlowModelAutoRepair] Could not reconnect orphan node '{}' - no end node available", orphan.getId());
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
                    logger.info("[FlowModelAutoRepair] Reconnected orphan '{}' to menu '{}' port '{}'",
                            orphan.getId(), branch.getId(), choice.getKey());
                    return true;
                }
            }
            if (choices.size() < 10) {
                String newKey = "key" + (choices.size() + 1);
                String newDtmf = String.valueOf((choices.size() % 10) + 1);
                branch.getMenu().addChoice(new FlowChoice(newKey, orphan.getTitle() != null ? orphan.getTitle() : orphan.getId(), orphan.getId()));
                choices.get(choices.size() - 1).setDtmf(newDtmf);
                model.addConnection(new FlowConnection(
                        "c_repair_" + branch.getId() + "_" + orphan.getId(),
                        branch.getId(),
                        newKey,
                        orphan.getId(),
                        "in"
                ));
                logger.info("[FlowModelAutoRepair] Added new menu choice '{}' in '{}' for orphan '{}'",
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
                logger.info("[FlowModelAutoRepair] Connected orphan '{}' to condition '{}' true branch",
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
                logger.info("[FlowModelAutoRepair] Connected orphan '{}' to condition '{}' false branch",
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

    private void remapInvalidPorts(FlowModel model) {
        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : model.getNodes()) {
            nodeMap.put(node.getId(), node);
        }

        Map<String, List<FlowConnection>> outgoingMap = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            if (conn.getSourceNodeId() != null) {
                outgoingMap.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
            }
        }

        for (Map.Entry<String, List<FlowConnection>> entry : outgoingMap.entrySet()) {
            String sourceId = entry.getKey();
            FlowNode sourceNode = nodeMap.get(sourceId);
            if (sourceNode == null) continue;

            List<FlowConnection> connections = entry.getValue();
            Set<String> validPorts = VALID_PORTS.getOrDefault(sourceNode.getType(), Set.of("out"));

            if (sourceNode.getType() == FlowNodeType.MENU) {
                if (sourceNode.getMenu() == null) {
                    sourceNode.setMenu(new FlowMenu());
                }
                Set<String> usedKeys = new HashSet<>();
                Set<String> usedDtmf = new HashSet<>();

                for (FlowChoice choice : sourceNode.getMenu().getChoices()) {
                    if (choice.getKey() != null) usedKeys.add(choice.getKey());
                    if (choice.getDtmf() != null) usedDtmf.add(choice.getDtmf());
                }

                int keySeq = 1;
                for (FlowConnection conn : connections) {
                    String port = conn.getSourcePort();
                    if (port == null || port.isBlank() || !validPorts.contains(port) || usedKeys.contains(port)) {
                        while (usedKeys.contains("key" + keySeq)) {
                            keySeq++;
                        }
                        String newKey = "key" + keySeq;
                        String newDtmf = findAvailableDigit(usedDtmf);
                        usedKeys.add(newKey);
                        usedDtmf.add(newDtmf);

                        boolean choiceExists = sourceNode.getMenu().getChoices().stream()
                                .anyMatch(c -> newKey.equals(c.getKey()));
                        if (!choiceExists) {
                            FlowNode targetNode = nodeMap.get(conn.getTargetNodeId());
                            String label = targetNode != null ? targetNode.getTitle() : "Option " + keySeq;
                            FlowChoice choice = new FlowChoice(newKey, label, conn.getTargetNodeId());
                            choice.setDtmf(newDtmf);
                            sourceNode.getMenu().addChoice(choice);
                        }

                        conn.setSourcePort(newKey);
                        logger.info("[FlowModelAutoRepair] Remapped invalid/duplicate port on MENU node '{}' to '{}'",
                                sourceId, newKey);
                    } else {
                        usedKeys.add(port);
                    }
                }
            } else if (sourceNode.getType() == FlowNodeType.CONDITION) {
                if (sourceNode.getCondition() == null) {
                    sourceNode.setCondition(new FlowCondition());
                }
                boolean trueAssigned = false;
                for (FlowConnection conn : connections) {
                    String port = conn.getSourcePort();
                    if (!"true".equalsIgnoreCase(port) && !"false".equalsIgnoreCase(port)) {
                        if (!trueAssigned && sourceNode.getCondition().getTrueTargetNodeId() == null) {
                            conn.setSourcePort("true");
                            sourceNode.getCondition().setTrueTargetNodeId(conn.getTargetNodeId());
                            trueAssigned = true;
                            logger.info("[FlowModelAutoRepair] Remapped invalid port on CONDITION node '{}' to 'true'", sourceId);
                        } else {
                            conn.setSourcePort("false");
                            sourceNode.getCondition().setFalseTargetNodeId(conn.getTargetNodeId());
                            logger.info("[FlowModelAutoRepair] Remapped invalid port on CONDITION node '{}' to 'false'", sourceId);
                        }
                    } else if ("true".equalsIgnoreCase(port)) {
                        sourceNode.getCondition().setTrueTargetNodeId(conn.getTargetNodeId());
                        trueAssigned = true;
                    } else if ("false".equalsIgnoreCase(port)) {
                        sourceNode.getCondition().setFalseTargetNodeId(conn.getTargetNodeId());
                    }
                }
            } else {
                Set<String> usedPorts = new HashSet<>();
                for (FlowConnection conn : connections) {
                    String port = conn.getSourcePort();
                    if (port == null || port.isBlank() || !validPorts.contains(port)) {
                        String newPort = validPorts.stream()
                                .filter(p -> !usedPorts.contains(p))
                                .findFirst()
                                .orElse(validPorts.isEmpty() ? "out" : validPorts.iterator().next());
                        conn.setSourcePort(newPort);
                        usedPorts.add(newPort);
                        logger.info("[FlowModelAutoRepair] Remapped invalid port on node '{}' ({}) to '{}'",
                                sourceId, sourceNode.getType(), newPort);
                    } else {
                        usedPorts.add(port);
                    }
                }
            }
        }
    }

    private void repairBrokenTransitions(FlowModel model) {
        List<FlowNode> leafNodes = findLeafNodes(model);
        FlowNode endNode = model.findEndNodes().stream().findFirst().orElse(null);

        if (endNode == null) return;

        for (FlowNode leaf : leafNodes) {
            if (leaf.getType() == FlowNodeType.START || leaf.getType() == FlowNodeType.END ||
                leaf.getType() == FlowNodeType.DISCONNECT || leaf.getType() == FlowNodeType.TRANSFER) {
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

        FlowNode startNode = model.findStartNode();
        if (startNode != null) {
            boolean hasOutgoing = model.getConnections().stream()
                    .anyMatch(c -> c.getSourceNodeId().equals(startNode.getId()));
            if (!hasOutgoing) {
                List<FlowNode> targets = model.getNodes().stream()
                        .filter(n -> n.getType() != FlowNodeType.START && n.getType() != FlowNodeType.END && n.getType() != FlowNodeType.DISCONNECT)
                        .collect(Collectors.toList());
                if (!targets.isEmpty()) {
                    model.addConnection(new FlowConnection(
                            "c_repair_start_" + startNode.getId() + "_" + targets.get(0).getId(),
                            startNode.getId(),
                            "out",
                            targets.get(0).getId(),
                            "in"
                    ));
                } else if (endNode != null) {
                    model.addConnection(new FlowConnection(
                            "c_repair_start_" + startNode.getId() + "_end",
                            startNode.getId(),
                            "out",
                            endNode.getId(),
                            "in"
                    ));
                }
            }
        }
    }

    private void repairUnreachableBranches(FlowModel model) {
        String startNodeId = model.findStartNode() != null ? model.findStartNode().getId() : null;
        if (startNodeId == null) return;

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        Map<String, List<FlowConnection>> adjacency = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            adjacency.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<FlowConnection> out = adjacency.get(cur);
            if (out != null) {
                for (FlowConnection conn : out) {
                    String t = conn.getTargetNodeId();
                    if (t != null && !t.isBlank() && !visited.contains(t)) {
                        visited.add(t);
                        queue.add(t);
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

    private List<FlowNode> findLeafNodes(FlowModel model) {
        Set<String> nodesWithOutgoing = model.getConnections().stream()
                .map(FlowConnection::getSourceNodeId)
                .collect(Collectors.toSet());

        return model.getNodes().stream()
                .filter(n -> !nodesWithOutgoing.contains(n.getId()))
                .filter(n -> n.getType() != FlowNodeType.END && n.getType() != FlowNodeType.DISCONNECT && n.getType() != FlowNodeType.TRANSFER)
                .collect(Collectors.toList());
    }

    private void repairCollapsedBranchingHubs(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) return;

        Map<String, List<FlowConnection>> outgoingMap = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            if (conn.getSourceNodeId() != null) {
                outgoingMap.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
            }
        }

        for (FlowNode node : new ArrayList<>(model.getNodes())) {
            if (node.getType() == FlowNodeType.PROMPT || node.getType() == FlowNodeType.START) {
                List<FlowConnection> outConns = outgoingMap.get(node.getId());
                if (outConns != null && outConns.size() >= 2) {
                    FlowNode existingMenu = model.getNodes().stream()
                            .filter(n -> n.getType() == FlowNodeType.MENU)
                            .findFirst().orElse(null);

                    if (existingMenu != null && node != existingMenu) {
                        logger.info("[FlowModelAutoRepair] Reusing existing MENU node '{}' ({}) to fix DELETED_BRANCHING_HUB on node '{}'",
                                existingMenu.getId(), existingMenu.getTitle(), node.getId());
                        if (existingMenu.getMenu() == null) {
                            existingMenu.setMenu(new FlowMenu());
                        }
                        int keyIndex = existingMenu.getMenu().getChoices().size() + 1;
                        for (FlowConnection conn : outConns) {
                            String port = "key" + keyIndex;
                            conn.setSourceNodeId(existingMenu.getId());
                            conn.setSourcePort(port);
                            final String currentPort = port;
                            if (existingMenu.getMenu().getChoices().stream().noneMatch(c -> currentPort.equals(c.getKey()))) {
                                String label = conn.getTargetNodeId() != null ? conn.getTargetNodeId() : "Option " + keyIndex;
                                existingMenu.getMenu().addChoice(new FlowChoice(port, label, conn.getTargetNodeId()));
                            }
                            keyIndex++;
                        }
                        model.addConnection(new FlowConnection("c_hub_" + node.getId() + "_" + existingMenu.getId(),
                                node.getId(), "out", existingMenu.getId(), "in"));
                    } else if (node.getType() == FlowNodeType.PROMPT) {
                        logger.info("[FlowModelAutoRepair] Auto-upgrading node '{}' ({}) to MENU to fix DELETED_BRANCHING_HUB", node.getId(), node.getType());
                        node.setType(FlowNodeType.MENU);
                        if (node.getMenu() == null) {
                            node.setMenu(new FlowMenu());
                        }
                        int keyIndex = 1;
                        for (FlowConnection conn : outConns) {
                            String port = "key" + keyIndex;
                            conn.setSourcePort(port);
                            final String currentPort = port;
                            if (node.getMenu().getChoices().stream().noneMatch(c -> currentPort.equals(c.getKey()))) {
                                String label = conn.getTargetNodeId() != null ? conn.getTargetNodeId() : "Option " + keyIndex;
                                node.getMenu().addChoice(new FlowChoice(port, label, conn.getTargetNodeId()));
                            }
                            keyIndex++;
                        }
                    } else if (node.getType() == FlowNodeType.START) {
                        logger.info("[FlowModelAutoRepair] Pruning duplicate unbranched edges on START node '{}' to fix DELETED_BRANCHING_HUB", node.getId());
                        for (int i = 1; i < outConns.size(); i++) {
                            model.getConnections().remove(outConns.get(i));
                        }
                    }
                }
            }
        }
    }
}
