package com.nexusivr.ai.model;

import com.nexusivr.ai.dto.common.ProviderAttemptDto;
import com.nexusivr.ai.dto.response.QuotaWarning;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code flows}.
 * Represents an IVR flow graph definition for a tenant.
 */
public class Flow {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private String flowJson;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<QuotaWarning> quotaWarnings;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;
    private List<ProviderAttemptDto> providerAttempts;
    private String refinedPrompt;
    private List<String> droppedFeatures;
    private boolean templateFallback;
    private String fallbackNotice;
    private int validationScore = 100;

    public Flow() {
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.quotaWarnings = new ArrayList<>();
        this.providerAttempts = new ArrayList<>();
        this.droppedFeatures = new ArrayList<>();
    }

    public Flow(UUID id, UUID tenantId, String name, String description, String flowJson,
                String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.flowJson = flowJson;
        this.status = status != null ? status : "DRAFT";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.quotaWarnings = new ArrayList<>();
        this.providerAttempts = new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public String getFlowJson() {
        return flowJson;
    }

    public void setFlowJson(String flowJson) {
        this.flowJson = flowJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<QuotaWarning> getQuotaWarnings() { return quotaWarnings; }
    public void setQuotaWarnings(List<QuotaWarning> quotaWarnings) { this.quotaWarnings = quotaWarnings; }

    public String getSelectedProvider() { return selectedProvider; }
    public void setSelectedProvider(String selectedProvider) { this.selectedProvider = selectedProvider; }

    public String getActualProviderUsed() { return actualProviderUsed; }
    public void setActualProviderUsed(String actualProviderUsed) { this.actualProviderUsed = actualProviderUsed; }

    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }

    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }

    public List<ProviderAttemptDto> getProviderAttempts() { return providerAttempts; }
    public void setProviderAttempts(List<ProviderAttemptDto> providerAttempts) { this.providerAttempts = providerAttempts != null ? providerAttempts : new ArrayList<>(); }

    public String getRefinedPrompt() { return refinedPrompt; }
    public void setRefinedPrompt(String refinedPrompt) { this.refinedPrompt = refinedPrompt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Flow flow)) return false;
        return Objects.equals(id, flow.id) &&
                Objects.equals(tenantId, flow.tenantId) &&
                Objects.equals(name, flow.name) &&
                Objects.equals(status, flow.status) &&
                Objects.equals(selectedProvider, flow.selectedProvider) &&
                Objects.equals(actualProviderUsed, flow.actualProviderUsed) &&
                fallbackUsed == flow.fallbackUsed &&
                Objects.equals(fallbackReason, flow.fallbackReason);
    }
    public List<String> getDroppedFeatures() {
        return droppedFeatures != null ? droppedFeatures : List.of();
    }

    public void setDroppedFeatures(List<String> droppedFeatures) {
        this.droppedFeatures = droppedFeatures != null ? droppedFeatures : new ArrayList<>();
    }

    public void addDroppedFeature(String feature) {
        if (feature != null && !feature.isBlank()) {
            if (this.droppedFeatures == null) {
                this.droppedFeatures = new ArrayList<>();
            }
            if (!this.droppedFeatures.contains(feature.trim())) {
                this.droppedFeatures.add(feature.trim());
            }
        }
    }

    public boolean isTemplateFallback() { return templateFallback; }
    public void setTemplateFallback(boolean templateFallback) { this.templateFallback = templateFallback; }

    public String getFallbackNotice() { return fallbackNotice; }
    public void setFallbackNotice(String fallbackNotice) { this.fallbackNotice = fallbackNotice; }

    public int getValidationScore() { return validationScore; }
    public void setValidationScore(int validationScore) { this.validationScore = validationScore; }
    public int getScore() { return validationScore; }
    public void setScore(int score) { this.validationScore = score; }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name, status, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }

    @Override
    public String toString() {
        return "Flow{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", selectedProvider='" + selectedProvider + '\'' +
                ", actualProviderUsed='" + actualProviderUsed + '\'' +
                ", fallbackUsed=" + fallbackUsed +
                ", fallbackReason='" + fallbackReason + '\'' +
                ", quotaWarnings=" + quotaWarnings +
                '}';
    }
}
