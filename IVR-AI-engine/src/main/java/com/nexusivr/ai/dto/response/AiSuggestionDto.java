package com.nexusivr.ai.dto.response;

import java.util.Objects;

/**
 * Structured DTO representing an LLM-generated flow suggestion for human review.
 */
public class AiSuggestionDto {

    private String id;
    private String text;
    private String icon;
    private String severity;
    private String nodeId;
    private String suggestionType;
    private String actionHint;

    public AiSuggestionDto() {
        this.suggestionType = "llm_advice";
        this.icon = "💡";
        this.severity = "warning";
    }

    public AiSuggestionDto(String id, String text, String icon, String severity, String nodeId, String suggestionType, String actionHint) {
        this.id = id;
        this.text = text;
        this.icon = icon != null ? icon : "💡";
        this.severity = severity != null ? severity : "warning";
        this.nodeId = nodeId;
        this.suggestionType = suggestionType != null ? suggestionType : "llm_advice";
        this.actionHint = actionHint;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getSuggestionType() {
        return suggestionType;
    }

    public void setSuggestionType(String suggestionType) {
        this.suggestionType = suggestionType;
    }

    public String getActionHint() {
        return actionHint;
    }

    public void setActionHint(String actionHint) {
        this.actionHint = actionHint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiSuggestionDto that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text);
    }

    @Override
    public String toString() {
        return "AiSuggestionDto{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", severity='" + severity + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", type='" + suggestionType + '\'' +
                '}';
    }
}
