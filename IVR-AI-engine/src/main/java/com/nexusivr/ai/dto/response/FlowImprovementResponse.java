package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.common.ProviderAttemptDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Output of the Flow Improvement module. changeLog is a human-readable
 * list ("Added a repeat-menu edge on no-input timeout at node
 * 'billing_menu'", ...) rather than a structured diff — useful for a
 * change-review UI — while `rationale` gives the overall reasoning tying
 * the changes back to the request's improvementGoals/analyticsContext.
 * improvedFlow reuses the same node/edge ids as the input flow wherever
 * a node was kept, so a caller can diff old vs. new FlowDto structurally
 * if a machine-readable diff is what they actually need.
 */
public class FlowImprovementResponse {

    private FlowDto improvedFlow;
    private List<String> changeLog;
    private String rationale;
    private String improvedFlowJson;
    private List<QuotaWarning> quotaWarnings;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;
    private List<ProviderAttemptDto> providerAttempts;
    private boolean improved = true;
    private boolean regressed = false;
    private boolean rolledBack = false;
    private FlowValidationResponse finalValidation;

    public FlowImprovementResponse() {
        this.changeLog = new ArrayList<>();
        this.quotaWarnings = new ArrayList<>();
        this.providerAttempts = new ArrayList<>();
    }

    public FlowImprovementResponse(FlowDto improvedFlow, List<String> changeLog, String rationale) {
        this(improvedFlow, changeLog, rationale, null, null, null, false, null);
    }

    public FlowImprovementResponse(FlowDto improvedFlow, List<String> changeLog, String rationale, String improvedFlowJson) {
        this(improvedFlow, changeLog, rationale, improvedFlowJson, null, null, false, null);
    }

    public FlowImprovementResponse(FlowDto improvedFlow, List<String> changeLog, String rationale,
                                   String improvedFlowJson, String selectedProvider, String actualProviderUsed,
                                   boolean fallbackUsed, String fallbackReason) {
        this.improvedFlow = improvedFlow;
        this.changeLog = changeLog != null ? changeLog : new ArrayList<>();
        this.rationale = rationale;
        this.improvedFlowJson = improvedFlowJson;
        this.quotaWarnings = new ArrayList<>();
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = fallbackReason != null ? fallbackReason : "";
        this.providerAttempts = new ArrayList<>();
    }

    public FlowDto getImprovedFlow() { return improvedFlow; }
    public void setImprovedFlow(FlowDto improvedFlow) { this.improvedFlow = improvedFlow; }

    public List<String> getChangeLog() { return changeLog; }
    public void setChangeLog(List<String> changeLog) { this.changeLog = changeLog; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getImprovedFlowJson() { return improvedFlowJson; }
    public void setImprovedFlowJson(String improvedFlowJson) { this.improvedFlowJson = improvedFlowJson; }

    public List<QuotaWarning> getQuotaWarnings() { return quotaWarnings; }
    public void setQuotaWarnings(List<QuotaWarning> quotaWarnings) { this.quotaWarnings = quotaWarnings; }

    public String getSelectedProvider() { return selectedProvider; }
    public void setSelectedProvider(String selectedProvider) { this.selectedProvider = selectedProvider != null ? selectedProvider : ""; }

    public String getActualProviderUsed() { return actualProviderUsed; }
    public void setActualProviderUsed(String actualProviderUsed) { this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : ""; }

    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }

    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason != null ? fallbackReason : ""; }

    public List<ProviderAttemptDto> getProviderAttempts() { return providerAttempts; }
    public void setProviderAttempts(List<ProviderAttemptDto> providerAttempts) { this.providerAttempts = providerAttempts != null ? providerAttempts : new ArrayList<>(); }

    public boolean isImproved() { return improved; }
    public void setImproved(boolean improved) { this.improved = improved; }

    public boolean isRegressed() { return regressed; }
    public void setRegressed(boolean regressed) { this.regressed = regressed; }

    public boolean isRolledBack() { return rolledBack; }
    public void setRolledBack(boolean rolledBack) { this.rolledBack = rolledBack; }

    public FlowValidationResponse getFinalValidation() { return finalValidation; }
    public void setFinalValidation(FlowValidationResponse finalValidation) { this.finalValidation = finalValidation; }

    @Override
    public String toString() {
        return "FlowImprovementResponse{" +
                "improvedFlow=" + improvedFlow +
                ", changeLog=" + changeLog +
                ", rationale='" + rationale + '\'' +
                ", improvedFlowJson='" + improvedFlowJson + '\'' +
                ", quotaWarnings=" + quotaWarnings +
                ", selectedProvider='" + selectedProvider + '\'' +
                ", actualProviderUsed='" + actualProviderUsed + '\'' +
                ", fallbackUsed=" + fallbackUsed +
                ", fallbackReason='" + fallbackReason + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowImprovementResponse)) return false;
        FlowImprovementResponse that = (FlowImprovementResponse) o;
        return Objects.equals(improvedFlow, that.improvedFlow) && Objects.equals(changeLog, that.changeLog) &&
                Objects.equals(rationale, that.rationale) && Objects.equals(improvedFlowJson, that.improvedFlowJson) &&
                Objects.equals(quotaWarnings, that.quotaWarnings) &&
                Objects.equals(selectedProvider, that.selectedProvider) &&
                Objects.equals(actualProviderUsed, that.actualProviderUsed) &&
                fallbackUsed == that.fallbackUsed &&
                Objects.equals(fallbackReason, that.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(improvedFlow, changeLog, rationale, improvedFlowJson, quotaWarnings, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }
}
