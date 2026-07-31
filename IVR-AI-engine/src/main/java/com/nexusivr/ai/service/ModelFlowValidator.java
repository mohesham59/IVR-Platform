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
 * Validates an Internal Flow Model.
 * <p>
 * Works on the FlowModel object graph directly, not on JSON.
 * This eliminates JSON parsing errors and allows structural validation
 * at the semantic level.
 */
public class ModelFlowValidator {

    private static final Logger logger = LoggerFactory.getLogger(ModelFlowValidator.class);

    public FlowValidationResponse validate(FlowModel model) {
        List<ValidationIssueDto> issues = new ArrayList<>();

        logger.info("[ModelFlowValidator] Validation Stage: FlowModel. Nodes={}, Connections={}.",
                model != null ? model.getNodes().size() : 0,
                model != null ? model.getConnections().size() : 0);

        if (model == null || model.getNodes().isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "EMPTY_FLOW", "Flow contains no nodes", null, null));
            logger.warn("[ModelFlowValidator] Validation Result: INVALID. Reason: Flow contains no nodes.");
            return new FlowValidationResponse(false, issues, 0);
        }

        // Rule 1: Exactly one Start node
        List<FlowNode> startNodes = model.getNodes().stream()
                .filter(n -> n.getType() == FlowNodeType.START)
                .collect(Collectors.toList());

        if (startNodes.isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_START", "Flow must contain exactly one Start node", null, null));
        } else if (startNodes.size() > 1) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MULTIPLE_STARTS",
                    "Flow must contain exactly one Start node (found: " + startNodes.size() + ")",
                    startNodes.get(1).getId(), null));
        }

        // Rule 2: At least one End node
        List<FlowNode> endNodes = model.findEndNodes();
        if (endNodes.isEmpty()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_END", "Flow must contain at least one End node", null, null));
        }

        // Rule 3: No duplicate IDs
        Set<String> nodeIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        for (FlowNode node : model.getNodes()) {
            String id = node.getId();
            if (id == null || id.isBlank()) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_NODE_ID", "Node has missing ID", null, null));
            } else {
                if (!nodeIds.add(id)) {
                    duplicateIds.add(id);
                }
            }
        }
        for (String dupId : duplicateIds) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "DUPLICATE_NODE_ID", "Duplicate node ID: " + dupId, dupId, null));
        }

        // Rule 4: No orphan nodes
        Set<String> connectedNodeIds = new HashSet<>();
        Map<String, List<FlowConnection>> adjacency = new HashMap<>();

        for (FlowConnection conn : model.getConnections()) {
            connectedNodeIds.add(conn.getSourceNodeId());
            connectedNodeIds.add(conn.getTargetNodeId());
            adjacency.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.START) {
                continue; // Start node is allowed to be unconnected initially
            }
            if (!connectedNodeIds.contains(node.getId())) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "ORPHAN_NODE",
                        "Node is completely disconnected from the flow", node.getId(), null));
            }
        }

        // Rule 5: Reachability from Start
        String startNodeId = startNodes.isEmpty() ? null : startNodes.get(0).getId();
        if (startNodeId != null && nodeIds.contains(startNodeId)) {
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(startNodeId);
            visited.add(startNodeId);

            while (!queue.isEmpty()) {
                String currentId = queue.poll();
                List<FlowConnection> outgoing = adjacency.get(currentId);
                if (outgoing != null) {
                    for (FlowConnection conn : outgoing) {
                        String targetId = conn.getTargetNodeId();
                        if (targetId != null && !targetId.isBlank() && nodeIds.contains(targetId) && !visited.contains(targetId)) {
                            visited.add(targetId);
                            queue.add(targetId);
                        }
                    }
                }
            }

            boolean hasReachableEnd = endNodes.stream().anyMatch(n -> visited.contains(n.getId()));
            if (!hasReachableEnd && !endNodes.isEmpty()) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "REACHABLE_END_REQUIRED",
                        "No End node is reachable from the Start node", null, null));
            }

            // Rule 6: Unreachable nodes
            for (FlowNode node : model.getNodes()) {
                if (node.getType() == FlowNodeType.START) continue;
                if (!visited.contains(node.getId())) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "UNREACHABLE_NODE",
                            "Node is unreachable from Start: " + node.getId(), node.getId(), null));
                }
            }
        }

        // Rule 7: Menu nodes should have choices
        for (FlowNode node : model.getNodes()) {
            if (node.getType() == FlowNodeType.MENU) {
                boolean hasChoices = node.getMenu() != null && !node.getMenu().getChoices().isEmpty();
                boolean hasConnections = model.getConnections().stream()
                        .anyMatch(c -> c.getSourceNodeId().equals(node.getId()));
                if (!hasChoices && !hasConnections) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "EMPTY_MENU",
                            "Menu node has no choices: " + node.getId(), node.getId(), null));
                }
            }

            // Rule 8: Transfer nodes should have destination
            if (node.getType() == FlowNodeType.TRANSFER && (node.getTransfer() == null || node.getTransfer().getDestination() == null || node.getTransfer().getDestination().isBlank())) {
                issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "MISSING_TRANSFER_DEST",
                        "Transfer node has no destination: " + node.getId(), node.getId(), null));
            }
        }

        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);
        int score = computeScore(issues, model);
        FlowValidationResponse response = new FlowValidationResponse(!hasErrors, issues, score);
        logger.info("[ModelFlowValidator] Validation Result: {}. Errors count={}, Warnings count={}. Score={}.",
                response.getStatus(), response.getErrorCount(), response.getWarningCount(), response.getScore());
        return response;
    }

    private int computeScore(List<ValidationIssueDto> issues, FlowModel model) {
        int score = 100;

        long startNodes = model != null ? model.getNodes().stream().filter(n -> n.getType() == FlowNodeType.START).count() : 0;
        if (startNodes == 0) score -= 30;
        long endNodes = model != null ? model.findEndNodes().size() : 0;
        if (endNodes == 0) score -= 15;

        long orphanNodes = issues.stream().filter(i -> "ORPHAN_NODE".equals(i.getCode())).count();
        score -= orphanNodes * 10;

        long unreachableNodes = issues.stream().filter(i -> "UNREACHABLE_NODE".equals(i.getCode())).count();
        score -= unreachableNodes * 5;

        long emptyMenus = issues.stream().filter(i -> "EMPTY_MENU".equals(i.getCode())).count();
        score -= emptyMenus * 5;

        long missingTransferDests = issues.stream().filter(i -> "MISSING_TRANSFER_DEST".equals(i.getCode())).count();
        score -= missingTransferDests * 5;

        long duplicateIds = issues.stream().filter(i -> "DUPLICATE_NODE_ID".equals(i.getCode())).count();
        score -= duplicateIds * 10;

        return Math.max(0, Math.min(100, score));
    }
}
