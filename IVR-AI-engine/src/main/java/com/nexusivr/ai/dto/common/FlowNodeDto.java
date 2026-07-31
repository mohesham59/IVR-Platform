package com.nexusivr.ai.dto.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One step in an IVR/chat flow graph. id is a client- or generator-
 * assigned String (e.g. "node_3", "greeting") rather than a UUID: flows
 * produced by the Flow Generator do not exist as DB rows in the MVP
 * schema (flow_generations is deferred to v2), so there is no surrogate
 * key to hand out yet — callers need stable, human-readable ids to diff
 * and reference nodes across Generator -> Improvement -> Validation ->
 * Router calls within a single editing session.
 */
public class FlowNodeDto {

    private String id;
    private FlowNodeType type;
    private String label;
    private String promptText;
    private Map<String, Object> metadata;

    public FlowNodeDto() {
        this.metadata = new HashMap<>();
    }

    public FlowNodeDto(String id, FlowNodeType type, String label, String promptText,
                        Map<String, Object> metadata) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.promptText = promptText;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public FlowNodeType getType() { return type; }
    public void setType(FlowNodeType type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return "FlowNodeDto{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", label='" + label + '\'' +
                ", promptText='" + promptText + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowNodeDto)) return false;
        FlowNodeDto that = (FlowNodeDto) o;
        return Objects.equals(id, that.id) && type == that.type &&
                Objects.equals(label, that.label) && Objects.equals(promptText, that.promptText) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, label, promptText, metadata);
    }
}
