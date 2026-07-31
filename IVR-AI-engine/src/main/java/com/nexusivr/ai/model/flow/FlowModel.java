package com.nexusivr.ai.model.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical internal representation of an IVR flow.
 * <p>
 * This is the single source of truth between VoiceXML parsing and React Flow rendering.
 * All validation, auto-repair, and AI improvement operations work on this model.
 */
public class FlowModel {
    private String id;
    private String name;
    private String description;
    private String voicexmlVersion;
    private final List<FlowNode> nodes = new ArrayList<>();
    private final List<FlowConnection> connections = new ArrayList<>();

    public FlowModel() {
    }

    public FlowModel(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.voicexmlVersion = "2.1";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVoicexmlVersion() {
        return voicexmlVersion;
    }

    public void setVoicexmlVersion(String voicexmlVersion) {
        this.voicexmlVersion = voicexmlVersion;
    }

    public List<FlowNode> getNodes() {
        return nodes;
    }

    public void addNode(FlowNode node) {
        if (node != null) {
            nodes.add(node);
        }
    }

    public FlowNode getNode(String id) {
        return nodes.stream()
                .filter(n -> Objects.equals(n.getId(), id))
                .findFirst()
                .orElse(null);
    }

    public List<FlowConnection> getConnections() {
        return connections;
    }

    public void addConnection(FlowConnection connection) {
        if (connection != null) {
            boolean exists = connections.stream().anyMatch(c ->
                    Objects.equals(c.getSourceNodeId(), connection.getSourceNodeId()) &&
                    Objects.equals(c.getSourcePort(), connection.getSourcePort()) &&
                    Objects.equals(c.getTargetNodeId(), connection.getTargetNodeId())
            );
            if (!exists) {
                connections.add(connection);
            }
        }
    }

    public FlowNode findStartNode() {
        return nodes.stream()
                .filter(n -> n.getType() == FlowNodeType.START)
                .findFirst()
                .orElse(null);
    }

    public List<FlowNode> findEndNodes() {
        return nodes.stream()
                .filter(n -> n.getType() == FlowNodeType.END)
                .toList();
    }

    public boolean hasStartNode() {
        return findStartNode() != null;
    }

    public boolean hasEndNode() {
        return !findEndNodes().isEmpty();
    }

    @Override
    public String toString() {
        return "FlowModel{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", nodes=" + nodes.size() +
                ", connections=" + connections.size() +
                '}';
    }
}
