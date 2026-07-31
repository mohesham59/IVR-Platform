package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic validator for Internal Flow Models.
 * <p>
 * Never uses AI. All checks are pure graph/XML analysis.
 * </p>
 */
public class FlowModelValidator {

    private static final Logger logger = LoggerFactory.getLogger(FlowModelValidator.class);

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

    public FlowValidationResponse validate(FlowModel model) {
        List<ValidationIssueDto> issues = new ArrayList<>();

        logger.info("[FlowModelValidator] Validation start. Nodes={}, Connections={}",
                model != null ? model.getNodes().size() : 0,
                model != null ? model.getConnections().size() : 0);

        if (model == null || model.getNodes().isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "EMPTY_FLOW", "Flow contains no nodes", null, null));
            return new FlowValidationResponse(false, issues, 0);
        }

        Set<String> nodeIds = new HashSet<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        Map<String, FlowNode> nodeMap = new HashMap<>();

        for (FlowNode node : model.getNodes()) {
            String id = node.getId();
            if (id == null || id.isBlank()) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_NODE_ID", "Node has missing ID", null, null));
            } else {
                if (!nodeIds.add(id)) {
                    duplicateIds.add(id);
                }
                nodeMap.put(id, node);
            }
        }
        for (String dupId : duplicateIds) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "DUPLICATE_NODE_ID", "Duplicate node ID: " + dupId, dupId, null));
        }

        List<FlowNode> startNodes = model.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.START)
                .collect(Collectors.toList());
        if (startNodes.isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_START", "Flow must contain exactly one Start node", null, null));
        } else if (startNodes.size() > 1) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MULTIPLE_STARTS",
                    "Flow must contain exactly one Start node (found: " + startNodes.size() + ")", startNodes.get(1).getId(), null));
        }

        List<FlowNode> endNodes = model.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.END || n.getType() == FlowNodeType.DISCONNECT)
                .collect(Collectors.toList());
        if (endNodes.isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_END", "Flow must contain at least one End node", null, null));
        }

        Set<String> connectedIds = new HashSet<>();
        Map<String, List<FlowConnection>> adjacency = new HashMap<>();
        Set<String> brokenEdgeIds = new LinkedHashSet<>();

        for (FlowConnection conn : model.getConnections()) {
            String src = conn.getSourceNodeId();
            String tgt = conn.getTargetNodeId();
            if (src != null && !src.isBlank()) connectedIds.add(src);
            if (tgt != null && !tgt.isBlank()) connectedIds.add(tgt);
            adjacency.computeIfAbsent(src, k -> new ArrayList<>()).add(conn);

            if ((src != null && !src.isBlank() && !nodeMap.containsKey(src)) ||
                (tgt != null && !tgt.isBlank() && !nodeMap.containsKey(tgt))) {
                brokenEdgeIds.add(conn.getId());
            }
        }
        for (String brokenId : brokenEdgeIds) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "BROKEN_EDGE", "Edge references non-existent node: " + brokenId, null, brokenId));
        }

        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.START) continue;
            if (!connectedIds.contains(node.getId())) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "DISCONNECTED_NODE",
                        "Node is disconnected from the flow", node.getId(), null));
            }
        }

        if (startNodes.size() == 1) {
            String startId = startNodes.get(0).getId();
            if (nodeIds.contains(startId)) {
                Set<String> visited = new HashSet<>();
                detectCycles(startId, adjacency, visited, new HashSet<>(), issues);

                visited.clear();
                Queue<String> queue = new LinkedList<>();
                queue.add(startId);
                visited.add(startId);
                Set<String> reachableEnds = new HashSet<>();

                while (!queue.isEmpty()) {
                    String cur = queue.poll();
                    List<FlowConnection> out = adjacency.get(cur);
                    if (out != null) {
                        for (FlowConnection conn : out) {
                            String t = conn.getTargetNodeId();
                            if (t != null && !t.isBlank() && nodeIds.contains(t) && !visited.contains(t)) {
                                visited.add(t);
                                queue.add(t);
                                if (nodeMap.get(t) != null && (nodeMap.get(t).getType() == FlowNodeType.END || nodeMap.get(t).getType() == FlowNodeType.DISCONNECT)) {
                                    reachableEnds.add(t);
                                }
                            }
                        }
                    }
                }

                if (reachableEnds.isEmpty() && !endNodes.isEmpty()) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "NO_REACHABLE_END",
                            "No End node is reachable from the Start node", null, null));
                }

                for (FlowNode node : model.getNodes()) {
                    if (node.getType() == FlowNodeType.START) continue;
                    if (!visited.contains(node.getId())) {
                        issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "UNREACHABLE_NODE",
                                "Node is unreachable from Start: " + node.getId(), node.getId(), null));
                    }
                }
            }
        }

        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.END || node.getType() == FlowNodeType.DISCONNECT) continue;

            List<FlowConnection> outgoing = adjacency.get(node.getId());
            boolean hasOutgoing = outgoing != null && !outgoing.isEmpty();

            if (!hasOutgoing && node.getType() != FlowNodeType.START && node.getType() != FlowNodeType.TRANSFER) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "DEAD_END",
                        "Node has no outgoing connections (dead end): " + node.getId(), node.getId(), null));
            }

            if (node.getType() == FlowNodeType.MENU) {
                boolean hasChoices = node.getMenu() != null && !node.getMenu().getChoices().isEmpty();
                if (!hasChoices && !hasOutgoing) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "INVALID_MENU",
                            "Menu node has no choices and no outgoing connections: " + node.getId(), node.getId(), null));
                } else if (hasChoices) {
                    List<FlowChoice> choices = node.getMenu().getChoices();
                    Set<String> dtmfKeys = new HashSet<>();
                    for (FlowChoice choice : choices) {
                        String dtmf = choice.getDtmf();
                        if (dtmf == null || dtmf.isBlank()) {
                            issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "INVALID_MENU_OPTION",
                                    "Menu choice missing DTMF key in node: " + node.getId(), node.getId(), null));
                        } else if (!dtmf.matches("\\d")) {
                            issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "INVALID_MENU_OPTION",
                                    "Menu choice has invalid DTMF key '" + dtmf + "' in node: " + node.getId(), node.getId(), null));
                        } else if (!dtmfKeys.add(dtmf)) {
                            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "INVALID_MENU",
                                    "Duplicate DTMF key '" + dtmf + "' in menu node: " + node.getId(), node.getId(), null));
                        }
                    }
                }
            }

            if (node.getType() == FlowNodeType.INPUT || node.getType() == FlowNodeType.START ||
                node.getType() == FlowNodeType.PROMPT || node.getType() == FlowNodeType.MENU ||
                node.getType() == FlowNodeType.CONDITION || node.getType() == FlowNodeType.BUSINESS_HOURS ||
                node.getType() == FlowNodeType.HOLIDAY || node.getType() == FlowNodeType.QUEUE ||
                node.getType() == FlowNodeType.RECORDING) {
                if (node.getPrompt() == null || node.getPrompt().getText() == null || node.getPrompt().getText().isBlank()) {
                    if (node.getTitle() == null || node.getTitle().isBlank()) {
                        issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "MISSING_PROMPT",
                                "Node missing prompt text and title: " + node.getId(), node.getId(), null));
                    }
                }
            }

            if (node.getType() == FlowNodeType.TRANSFER && (node.getTransfer() == null || node.getTransfer().getDestination() == null || node.getTransfer().getDestination().isBlank())) {
                issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "MISSING_TRANSFER_DEST",
                        "Transfer node has no destination: " + node.getId(), node.getId(), null));
            }
        }

        for (FlowConnection conn : model.getConnections()) {
            FlowNode srcNode = nodeMap.get(conn.getSourceNodeId());
            if (srcNode != null) {
                Set<String> validPorts = VALID_PORTS.getOrDefault(srcNode.getType(), Set.of("out"));
                String port = conn.getSourcePort();
                if (port == null || port.isBlank()) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "PORT_MISMATCH",
                            "Connection missing source port on node: " + conn.getSourceNodeId(), conn.getSourceNodeId(), conn.getId()));
                } else if (!validPorts.contains(port)) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "PORT_MISMATCH",
                            "Invalid source port '" + port + "' for node type " + srcNode.getType() + " on node: " + conn.getSourceNodeId(), conn.getSourceNodeId(), conn.getId()));
                }
            }
        }

        Map<String, List<FlowConnection>> incomingMap = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                incomingMap.computeIfAbsent(conn.getTargetNodeId(), k -> new ArrayList<>()).add(conn);
            }
        }

        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.PROMPT || node.getType() == FlowNodeType.START || node.getType() == FlowNodeType.RECORDING) {
                List<FlowConnection> outgoing = adjacency.get(node.getId());
                if (outgoing != null && outgoing.size() >= 2) {
                    String nodeName = node.getTitle() != null && !node.getTitle().isBlank() ? node.getTitle() : node.getId();
                    issues.add(new ValidationIssueDto(
                            ValidationSeverity.WARNING,
                            "DELETED_BRANCHING_HUB",
                            String.format("Node '%s' (%s) has %d outgoing connections without a Menu or Condition node — a branching menu node was likely deleted.", nodeName, node.getType().getBuilderType(), outgoing.size()),
                            node.getId(),
                            null
                    ));
                }
            }
        }

        for (Map.Entry<String, List<FlowConnection>> entry : incomingMap.entrySet()) {
            String targetId = entry.getKey();
            List<FlowConnection> incoming = entry.getValue();
            if (incoming.size() > 1) {
                FlowNode targetNode = nodeMap.get(targetId);
                String targetName = targetNode != null && targetNode.getTitle() != null && !targetNode.getTitle().isBlank()
                        ? targetNode.getTitle()
                        : targetId;

                Set<String> distinctSources = incoming.stream()
                        .map(FlowConnection::getSourceNodeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                if (distinctSources.size() > 1 || incoming.size() >= 3) {
                    issues.add(new ValidationIssueDto(
                            ValidationSeverity.WARNING,
                            "CONVERGING_PATHS",
                            String.format("Multiple incoming paths (%d) converge into node '%s' — review for potential lost business logic or over-collapsed routing.", incoming.size(), targetName),
                            targetId,
                            null
                    ));
                }
            }
        }

        List<String> vxmlErrors = validateVoiceXml(model);
        for (String vxmlError : vxmlErrors) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "INVALID_VOICEXML", vxmlError, null, null));
        }

        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);
        int score = computeScore(issues, model);
        FlowValidationResponse response = new FlowValidationResponse(!hasErrors, issues, score);

        logger.info("[FlowModelValidator] Validation Result: {} ({}). Errors count={}, Warnings count={}. Score={}.",
                response.getStatus(), response.getStatusLabel(), response.getErrorCount(), response.getWarningCount(), response.getScore());

        return response;
    }

    private void detectCycles(String nodeId, Map<String, List<FlowConnection>> adjacency,
                              Set<String> visited, Set<String> inStack, List<ValidationIssueDto> issues) {
        visited.add(nodeId);
        inStack.add(nodeId);

        List<FlowConnection> outgoing = adjacency.get(nodeId);
        if (outgoing != null) {
            for (FlowConnection conn : outgoing) {
                String target = conn.getTargetNodeId();
                if (target == null || target.isBlank()) continue;
                if (inStack.contains(target)) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "CYCLE_DETECTED",
                            "Cycle detected involving node: " + target, target, conn.getId()));
                } else if (!visited.contains(target)) {
                    detectCycles(target, adjacency, visited, inStack, issues);
                }
            }
        }

        inStack.remove(nodeId);
    }

    private List<String> validateVoiceXml(FlowModel model) {
        List<String> errors = new ArrayList<>();
        try {
            ModelToVxmlExporter exporter = new ModelToVxmlExporter();
            String vxml = exporter.export(model);
            if (vxml == null || vxml.isBlank()) {
                errors.add("Generated VoiceXML is empty");
                return errors;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(vxml)));
            doc.getDocumentElement().normalize();

            if (!"vxml".equals(doc.getDocumentElement().getNodeName())) {
                errors.add("VoiceXML root element must be <vxml>");
            }

            NodeList forms = doc.getElementsByTagName("form");
            if (forms.getLength() == 0) {
                errors.add("VoiceXML must contain at least one <form> element");
            }

            Set<String> formIds = new HashSet<>();
            for (int i = 0; i < forms.getLength(); i++) {
                org.w3c.dom.Element form = (org.w3c.dom.Element) forms.item(i);
                String id = form.getAttribute("id");
                if (id == null || id.isBlank()) {
                    errors.add("<form> element missing 'id' attribute");
                } else if (!formIds.add(id)) {
                    errors.add("Duplicate <form> id: " + id);
                }
            }

            boolean hasMenu = doc.getElementsByTagName("menu").getLength() > 0;
            if (hasMenu) {
                NodeList menus = doc.getElementsByTagName("menu");
                for (int m = 0; m < menus.getLength(); m++) {
                    org.w3c.dom.Element menu = (org.w3c.dom.Element) menus.item(m);
                    NodeList choices = menu.getElementsByTagName("choice");
                    Set<String> dtmfValues = new HashSet<>();
                    for (int i = 0; i < choices.getLength(); i++) {
                        org.w3c.dom.Element choice = (org.w3c.dom.Element) choices.item(i);
                        String dtmf = choice.getAttribute("dtmf");
                        if (dtmf == null || dtmf.isBlank()) {
                            errors.add("<choice> element missing 'dtmf' attribute in menu");
                        } else if (!dtmf.matches("\\d")) {
                            errors.add("Invalid dtmf value '" + dtmf + "' in <choice>");
                        } else if (!dtmfValues.add(dtmf)) {
                            errors.add("Duplicate dtmf value '" + dtmf + "' in <choice> elements within a menu");
                        }
                    }
                }
            }

            NodeList blocks = doc.getElementsByTagName("block");
            for (int i = 0; i < blocks.getLength(); i++) {
                org.w3c.dom.Element block = (org.w3c.dom.Element) blocks.item(i);
                boolean hasPrompt = block.getElementsByTagName("prompt").getLength() > 0;
                boolean hasGoto = block.getElementsByTagName("goto").getLength() > 0;
                boolean hasTransfer = block.getElementsByTagName("transfer").getLength() > 0;
                boolean hasDisconnect = block.getElementsByTagName("disconnect").getLength() > 0;
                if (!hasPrompt && !hasGoto && !hasTransfer && !hasDisconnect) {
                    String parentId = block.getParentNode() != null ?
                            ((org.w3c.dom.Element) block.getParentNode()).getAttribute("id") : "unknown";
                    errors.add("<block> in form '" + parentId + "' must contain at least one <prompt>, <goto>, <transfer>, or <disconnect>");
                }
            }

            NodeList fields = doc.getElementsByTagName("field");
            for (int i = 0; i < fields.getLength(); i++) {
                org.w3c.dom.Element field = (org.w3c.dom.Element) fields.item(i);
                String name = field.getAttribute("name");
                if (name == null || name.isBlank()) {
                    errors.add("<field> element missing 'name' attribute");
                }
            }

            NodeList menus = doc.getElementsByTagName("menu");
            for (int i = 0; i < menus.getLength(); i++) {
                org.w3c.dom.Element menu = (org.w3c.dom.Element) menus.item(i);
                boolean hasPrompt = menu.getElementsByTagName("prompt").getLength() > 0;
                if (!hasPrompt) {
                    String parentId = menu.getParentNode() != null ?
                            ((org.w3c.dom.Element) menu.getParentNode()).getAttribute("id") : "unknown";
                    errors.add("<menu> in form '" + parentId + "' must contain a <prompt>");
                }
            }

        } catch (Exception e) {
            errors.add("VoiceXML parsing failed: " + e.getMessage());
        }
        return errors;
    }

    private int computeScore(List<ValidationIssueDto> issues, FlowModel model) {
        int score = 100;

        long startNodes = model != null ? model.getNodes().stream().filter(n -> n.getType() == FlowNodeType.START).count() : 0;
        if (startNodes == 0) score -= 30;
        else if (startNodes > 1) score -= 20;

        long endNodes = model != null ? model.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.END || n.getType() == FlowNodeType.DISCONNECT).count() : 0;
        if (endNodes == 0) score -= 15;

        long duplicateIds = issues.stream().filter(i -> "DUPLICATE_NODE_ID".equals(i.getCode())).count();
        score -= duplicateIds * 10;

        long disconnected = issues.stream().filter(i -> "DISCONNECTED_NODE".equals(i.getCode())).count();
        score -= disconnected * 10;

        long brokenEdges = issues.stream().filter(i -> "BROKEN_EDGE".equals(i.getCode())).count();
        score -= brokenEdges * 8;

        long cycles = issues.stream().filter(i -> "CYCLE_DETECTED".equals(i.getCode())).count();
        score -= cycles * 5;

        long deadEnds = issues.stream().filter(i -> "DEAD_END".equals(i.getCode())).count();
        score -= deadEnds * 5;

        long unreachable = issues.stream().filter(i -> "UNREACHABLE_NODE".equals(i.getCode())).count();
        score -= unreachable * 5;

        long missingPrompts = issues.stream().filter(i -> "MISSING_PROMPT".equals(i.getCode())).count();
        score -= missingPrompts * 3;

        long invalidMenus = issues.stream().filter(i -> "INVALID_MENU".equals(i.getCode())).count();
        score -= invalidMenus * 5;

        long invalidMenuOptions = issues.stream().filter(i -> "INVALID_MENU_OPTION".equals(i.getCode())).count();
        score -= invalidMenuOptions * 3;

        long portMismatches = issues.stream().filter(i -> "PORT_MISMATCH".equals(i.getCode())).count();
        score -= portMismatches * 3;

        long invalidVxml = issues.stream().filter(i -> "INVALID_VOICEXML".equals(i.getCode())).count();
        score -= invalidVxml * 5;

        long convergingPaths = issues.stream().filter(i -> "CONVERGING_PATHS".equals(i.getCode())).count();
        score -= convergingPaths * 3;

        return Math.max(0, Math.min(100, score));
    }
}
