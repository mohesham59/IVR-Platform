package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * One finding from running a flow (or, via Flow Improvement, a proposed
 * flow) through validation. nodeId/edgeId are both nullable and mutually
 * exclusive in practice — a finding is either about a specific node, a
 * specific edge, or the graph as a whole (both null, e.g. "unreachable
 * region" or "no HANGUP node found").
 */
public class ValidationIssueDto {

    private ValidationSeverity severity;
    private String code;
    private String message;
    private String nodeId;
    private String edgeId;

    public ValidationIssueDto() {
    }

    public ValidationIssueDto(ValidationSeverity severity, String code, String message,
                               String nodeId, String edgeId) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.nodeId = nodeId;
        this.edgeId = edgeId;
    }

    public ValidationSeverity getSeverity() { return severity; }
    public void setSeverity(ValidationSeverity severity) { this.severity = severity; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    @Override
    public String toString() {
        return "ValidationIssueDto{" +
                "severity=" + severity +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", edgeId='" + edgeId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationIssueDto)) return false;
        ValidationIssueDto that = (ValidationIssueDto) o;
        return severity == that.severity && Objects.equals(code, that.code) &&
                Objects.equals(message, that.message) && Objects.equals(nodeId, that.nodeId) &&
                Objects.equals(edgeId, that.edgeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, code, message, nodeId, edgeId);
    }
}
