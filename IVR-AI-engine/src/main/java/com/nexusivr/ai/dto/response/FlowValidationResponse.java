package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Output of the Validation module. `valid` is a simple derived flag —
 * true iff `issues` contains no entry with severity ERROR — provided as
 * its own field (rather than making callers scan the list themselves)
 * because "is this flow safe to publish?" is the single most common
 * question asked of this response.
 */
public class FlowValidationResponse {

    private boolean valid;
    private List<ValidationIssueDto> issues;
    private int score;

    public FlowValidationResponse() {
        this.issues = new ArrayList<>();
        this.score = 100;
    }

    public FlowValidationResponse(boolean valid, List<ValidationIssueDto> issues) {
        this.valid = valid;
        this.issues = issues != null ? issues : new ArrayList<>();
        this.score = 100;
    }

    public FlowValidationResponse(boolean valid, List<ValidationIssueDto> issues, int score) {
        this.valid = valid;
        this.issues = issues != null ? issues : new ArrayList<>();
        this.score = Math.max(0, Math.min(100, score));
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<ValidationIssueDto> getIssues() { return issues; }
    public void setIssues(List<ValidationIssueDto> issues) { this.issues = issues; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = Math.max(0, Math.min(100, score)); }

    public long getErrorCount() {
        return issues == null ? 0 : issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count();
    }

    public long getWarningCount() {
        return issues == null ? 0 : issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARNING).count();
    }

    public boolean isClean() {
        return valid && (issues == null || issues.isEmpty() || getWarningCount() == 0);
    }

    private boolean templateFallback;
    private String fallbackNotice;

    public boolean isTemplateFallback() { return templateFallback; }
    public void setTemplateFallback(boolean templateFallback) { this.templateFallback = templateFallback; }

    public String getFallbackNotice() { return fallbackNotice; }
    public void setFallbackNotice(String fallbackNotice) { this.fallbackNotice = fallbackNotice; }

    public String getStatus() {
        if (!valid || getErrorCount() > 0) {
            return "INVALID";
        }
        if (templateFallback) {
            return "FALLBACK_TEMPLATE";
        }
        if (getWarningCount() > 0) {
            return "VALID_WITH_WARNINGS";
        }
        return "VALID";
    }

    public String getStatusLabel() {
        if (!valid || getErrorCount() > 0) {
            long errs = getErrorCount();
            return String.format("Invalid (%d error%s)", errs, errs == 1 ? "" : "s");
        }
        if (templateFallback) {
            long warns = getWarningCount();
            if (warns > 0) {
                return String.format("Valid (Built-in Template Fallback, %d warning%s)", warns, warns == 1 ? "" : "s");
            }
            return "Valid (Built-in Template Fallback)";
        }
        if (getWarningCount() > 0) {
            long warns = getWarningCount();
            return String.format("Valid (%d warning%s)", warns, warns == 1 ? "" : "s");
        }
        return "Valid (Clean)";
    }

    @Override
    public String toString() {
        return "FlowValidationResponse{" +
                "status=" + getStatus() +
                ", valid=" + valid +
                ", score=" + score +
                ", errors=" + getErrorCount() +
                ", warnings=" + getWarningCount() +
                ", issues=" + issues +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowValidationResponse)) return false;
        FlowValidationResponse that = (FlowValidationResponse) o;
        return valid == that.valid && score == that.score && Objects.equals(issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, issues, score);
    }
}
