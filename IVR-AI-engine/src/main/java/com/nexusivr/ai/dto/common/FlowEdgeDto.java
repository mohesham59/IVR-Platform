package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * A directed transition between two FlowNodeDto ids. `condition` carries
 * the machine-evaluated predicate (e.g. "dtmf=1", "intent=BILLING",
 * "no_input_timeout"); `label` carries the human-readable caption shown
 * in a flow editor UI. Kept as two separate fields rather than one,
 * since Validation checks the condition but Flow Generator/Improvement
 * mostly care about the label when explaining changes to a person.
 */
public class FlowEdgeDto {

    private String id;
    private String fromNodeId;
    private String toNodeId;
    private String condition;
    private String label;

    public FlowEdgeDto() {
    }

    public FlowEdgeDto(String id, String fromNodeId, String toNodeId, String condition, String label) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.condition = condition;
        this.label = label;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }

    public String getToNodeId() { return toNodeId; }
    public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        return "FlowEdgeDto{" +
                "id='" + id + '\'' +
                ", fromNodeId='" + fromNodeId + '\'' +
                ", toNodeId='" + toNodeId + '\'' +
                ", condition='" + condition + '\'' +
                ", label='" + label + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowEdgeDto)) return false;
        FlowEdgeDto that = (FlowEdgeDto) o;
        return Objects.equals(id, that.id) && Objects.equals(fromNodeId, that.fromNodeId) &&
                Objects.equals(toNodeId, that.toNodeId) && Objects.equals(condition, that.condition) &&
                Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fromNodeId, toNodeId, condition, label);
    }
}
