package com.nexusivr.ai.dto.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A full flow graph: the shared payload shape passed between Flow
 * Generator (produces it), Flow Improvement (consumes + re-emits it),
 * Validation (consumes it, read-only), and Router (consumes a flow's
 * nodes/edges to decide the next hop at runtime).
 *
 * flowId is a nullable UUID rather than a required one: the MVP schema
 * has no flow_generations table (explicitly deferred to v2 per the DB
 * design doc), so a freshly generated or improved flow has no row to
 * point to yet. Once v2 adds that table, flowId becomes the FK and this
 * field's contract does not need to change — only whether it is null.
 */
public class FlowDto {

    private UUID flowId;
    private String name;
    private List<FlowNodeDto> nodes;
    private List<FlowEdgeDto> edges;
    private Map<String, Object> metadata;

    public FlowDto() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public FlowDto(UUID flowId, String name, List<FlowNodeDto> nodes, List<FlowEdgeDto> edges,
                    Map<String, Object> metadata) {
        this.flowId = flowId;
        this.name = name;
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public UUID getFlowId() { return flowId; }
    public void setFlowId(UUID flowId) { this.flowId = flowId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<FlowNodeDto> getNodes() { return nodes; }
    public void setNodes(List<FlowNodeDto> nodes) { this.nodes = nodes; }

    public List<FlowEdgeDto> getEdges() { return edges; }
    public void setEdges(List<FlowEdgeDto> edges) { this.edges = edges; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return "FlowDto{" +
                "flowId=" + flowId +
                ", name='" + name + '\'' +
                ", nodes=" + nodes +
                ", edges=" + edges +
                ", metadata=" + metadata +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowDto)) return false;
        FlowDto that = (FlowDto) o;
        return Objects.equals(flowId, that.flowId) && Objects.equals(name, that.name) &&
                Objects.equals(nodes, that.nodes) && Objects.equals(edges, that.edges) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, name, nodes, edges, metadata);
    }
}
