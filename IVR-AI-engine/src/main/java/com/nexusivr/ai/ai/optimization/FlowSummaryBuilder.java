package com.nexusivr.ai.ai.optimization;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds compact, token-efficient summaries of a FlowModel.
 * <p>
 * Instead of sending full React Flow JSON or VoiceXML to the LLM,
 * use these summaries to convey the essential structure.
 * </p>
 */
public class FlowSummaryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(FlowSummaryBuilder.class);

    /**
     * Build a compact text summary of the flow suitable for LLM context.
     * Typical size: 200-800 tokens vs 2,000-10,000 for full JSON/VoiceXML.
     */
    public static String buildCompactSummary(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) {
            return "[Empty flow]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Flow: ").append(model.getName() != null ? model.getName() : "Untitled").append("\n");
        sb.append("Nodes: ").append(model.getNodes().size()).append("\n");
        sb.append("Edges: ").append(model.getConnections().size()).append("\n\n");

        sb.append("Node List:\n");
        for (FlowNode node : model.getNodes()) {
            sb.append("- ").append(node.getId())
              .append(" (").append(node.getType()).append(")")
              .append(": ").append(node.getTitle() != null ? node.getTitle() : "Untitled");
            
            if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
                sb.append(" | Prompt: \"").append(truncate(node.getPrompt().getText(), 80)).append("\"");
            }
            
            if (node.getType() == FlowNodeType.MENU && node.getMenu() != null && !node.getMenu().getChoices().isEmpty()) {
                sb.append(" | Choices: ");
                sb.append(node.getMenu().getChoices().stream()
                        .map(c -> c.getDtmf() + "->" + (c.getTargetNodeId() != null ? c.getTargetNodeId() : "?"))
                        .collect(Collectors.joining(", ")));
            }
            
            if (node.getType() == FlowNodeType.TRANSFER && node.getTransfer() != null && node.getTransfer().getDestination() != null) {
                sb.append(" | Transfer to: ").append(node.getTransfer().getDestination());
            }
            
            sb.append("\n");
        }

        sb.append("\nEdge List:\n");
        for (FlowConnection conn : model.getConnections()) {
            sb.append("- ").append(conn.getSourceNodeId()).append(".").append(conn.getSourcePort() != null ? conn.getSourcePort() : "out")
              .append(" -> ").append(conn.getTargetNodeId()).append(".").append(conn.getTargetPort() != null ? conn.getTargetPort() : "in")
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * Build an even more compact summary (just counts and types).
     */
    public static String buildMinimalSummary(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) {
            return "[Empty flow]";
        }

        Map<FlowNodeType, Long> typeCounts = model.getNodes().stream()
                .collect(Collectors.groupingBy(FlowNode::getType, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("Flow: ").append(model.getName() != null ? model.getName() : "Untitled").append("\n");
        sb.append("Nodes: ").append(model.getNodes().size()).append(" | Edges: ").append(model.getConnections().size()).append("\n");
        sb.append("Types: ");
        sb.append(typeCounts.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Build a summary focused on a specific node and its immediate context.
     */
    public static String buildNodeContextSummary(FlowModel model, String nodeId) {
        if (model == null) return "[No flow]";
        
        FlowNode node = model.getNode(nodeId);
        if (node == null) return "[Node not found: " + nodeId + "]";

        StringBuilder sb = new StringBuilder();
        sb.append("Node: ").append(node.getId()).append(" (").append(node.getType()).append(")\n");
        sb.append("Title: ").append(node.getTitle() != null ? node.getTitle() : "Untitled").append("\n");
        
        if (node.getPrompt() != null && node.getPrompt().getText() != null) {
            sb.append("Prompt: \"").append(truncate(node.getPrompt().getText(), 120)).append("\"\n");
        }
        
        if (node.getType() == FlowNodeType.MENU && node.getMenu() != null) {
            sb.append("Menu choices:\n");
            for (var choice : node.getMenu().getChoices()) {
                sb.append("  ").append(choice.getDtmf()).append(": ").append(choice.getLabel())
                  .append(" -> ").append(choice.getTargetNodeId() != null ? choice.getTargetNodeId() : "?").append("\n");
            }
        }

        List<FlowConnection> incoming = model.getConnections().stream()
                .filter(c -> nodeId.equals(c.getTargetNodeId()))
                .toList();
        List<FlowConnection> outgoing = model.getConnections().stream()
                .filter(c -> nodeId.equals(c.getSourceNodeId()))
                .toList();

        if (!incoming.isEmpty()) {
            sb.append("Incoming: ");
            sb.append(incoming.stream()
                    .map(c -> c.getSourceNodeId() + "." + c.getSourcePort())
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }
        if (!outgoing.isEmpty()) {
            sb.append("Outgoing: ");
            sb.append(outgoing.stream()
                    .map(c -> c.getTargetNodeId() + "." + c.getTargetPort())
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
