package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.common.ProviderAttemptDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Output of the Flow Generator module. generatedFlow.flowId will be null
 * (see FlowDto's note) since nothing is persisted yet — this response is
 * meant to be reviewed/edited and then explicitly saved through whatever
 * v2 persistence layer eventually backs flow_generations. warnings
 * covers soft issues the generator noticed but didn't treat as fatal
 * (e.g. "no explicit HANGUP path for angry-customer intent") — distinct
 * from Validation's ValidationIssueDto set, which is a stricter, rule-
 * based pass a caller can run separately/later.
 */
public class FlowGenerationResponse {

    private FlowDto generatedFlow;
    private List<String> warnings;
    private Map<String, Object> generationMetadata;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;
    private List<ProviderAttemptDto> providerAttempts;

    public FlowGenerationResponse() {
        this.warnings = new ArrayList<>();
        this.generationMetadata = new HashMap<>();
        this.providerAttempts = new ArrayList<>();
    }

    public FlowGenerationResponse(FlowDto generatedFlow, List<String> warnings, Map<String, Object> generationMetadata) {
        this(generatedFlow, warnings, generationMetadata, null, null, false, null);
    }

    public FlowGenerationResponse(FlowDto generatedFlow, List<String> warnings, Map<String, Object> generationMetadata,
                                  String selectedProvider, String actualProviderUsed, boolean fallbackUsed, String fallbackReason) {
        this.generatedFlow = generatedFlow;
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.generationMetadata = generationMetadata != null ? generationMetadata : new HashMap<>();
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = fallbackReason != null ? fallbackReason : "";
        this.providerAttempts = new ArrayList<>();
    }

    public FlowDto getGeneratedFlow() { return generatedFlow; }
    public void setGeneratedFlow(FlowDto generatedFlow) { this.generatedFlow = generatedFlow; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public Map<String, Object> getGenerationMetadata() { return generationMetadata; }
    public void setGenerationMetadata(Map<String, Object> generationMetadata) { this.generationMetadata = generationMetadata; }

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

    @Override
    public String toString() {
        return "FlowGenerationResponse{" +
                "generatedFlow=" + generatedFlow +
                ", warnings=" + warnings +
                ", generationMetadata=" + generationMetadata +
                ", selectedProvider='" + selectedProvider + '\'' +
                ", actualProviderUsed='" + actualProviderUsed + '\'' +
                ", fallbackUsed=" + fallbackUsed +
                ", fallbackReason='" + fallbackReason + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowGenerationResponse)) return false;
        FlowGenerationResponse that = (FlowGenerationResponse) o;
        return Objects.equals(generatedFlow, that.generatedFlow) && Objects.equals(warnings, that.warnings) &&
                Objects.equals(generationMetadata, that.generationMetadata) &&
                Objects.equals(selectedProvider, that.selectedProvider) &&
                Objects.equals(actualProviderUsed, that.actualProviderUsed) &&
                fallbackUsed == that.fallbackUsed &&
                Objects.equals(fallbackReason, that.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generatedFlow, warnings, generationMetadata, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }
}
