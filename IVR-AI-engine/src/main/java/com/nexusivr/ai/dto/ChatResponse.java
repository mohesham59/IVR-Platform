package com.nexusivr.ai.dto;

import com.nexusivr.ai.dto.common.ProviderAttemptDto;
import com.nexusivr.ai.dto.response.QuotaWarning;
import com.nexusivr.ai.model.MessageRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for chat response payload.
 */
public class ChatResponse {

    private UUID sessionId;
    private UUID tenantId;
    private String replyMessage;
    private MessageRole role;
    private int turnNumber;
    private Integer tokensUsed;
    private String flowJson;
    private List<QuotaWarning> quotaWarnings;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;
    private List<ProviderAttemptDto> providerAttempts;
    private String refinedPrompt;
    private List<String> droppedFeatures;
    private com.nexusivr.ai.dto.response.FlowValidationResponse validationResult;
    private boolean templateFallback;
    private String fallbackNotice;

    public ChatResponse() {
        this.role = MessageRole.ASSISTANT;
        this.quotaWarnings = new ArrayList<>();
        this.providerAttempts = new ArrayList<>();
        this.droppedFeatures = new ArrayList<>();
    }

    public ChatResponse(UUID sessionId, UUID tenantId, String replyMessage, MessageRole role, int turnNumber, Integer tokensUsed) {
        this(sessionId, tenantId, replyMessage, role, turnNumber, tokensUsed, null, null, false, null);
    }

    public ChatResponse(UUID sessionId, UUID tenantId, String replyMessage, MessageRole role, int turnNumber, Integer tokensUsed,
                        String selectedProvider, String actualProviderUsed, boolean fallbackUsed, String fallbackReason) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.replyMessage = replyMessage;
        this.role = role != null ? role : MessageRole.ASSISTANT;
        this.turnNumber = turnNumber;
        this.tokensUsed = tokensUsed;
        this.quotaWarnings = new ArrayList<>();
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = fallbackReason != null ? fallbackReason : "";
        this.providerAttempts = new ArrayList<>();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getReplyMessage() {
        return replyMessage;
    }

    public void setReplyMessage(String replyMessage) {
        this.replyMessage = replyMessage;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getFlowJson() {
        return flowJson;
    }

    public void setFlowJson(String flowJson) {
        this.flowJson = flowJson;
    }

    public List<QuotaWarning> getQuotaWarnings() {
        return quotaWarnings;
    }

    public void setQuotaWarnings(List<QuotaWarning> quotaWarnings) {
        this.quotaWarnings = quotaWarnings;
    }

    public String getSelectedProvider() {
        return selectedProvider;
    }

    public void setSelectedProvider(String selectedProvider) {
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
    }

    public String getActualProviderUsed() {
        return actualProviderUsed;
    }

    public void setActualProviderUsed(String actualProviderUsed) {
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason != null ? fallbackReason : "";
    }

    public List<ProviderAttemptDto> getProviderAttempts() {
        return providerAttempts;
    }

    public void setProviderAttempts(List<ProviderAttemptDto> providerAttempts) {
        this.providerAttempts = providerAttempts != null ? providerAttempts : new ArrayList<>();
    }

    public String getRefinedPrompt() {
        return refinedPrompt;
    }

    public void setRefinedPrompt(String refinedPrompt) {
        this.refinedPrompt = refinedPrompt;
    }

    public List<String> getDroppedFeatures() {
        return droppedFeatures != null ? droppedFeatures : List.of();
    }

    public void setDroppedFeatures(List<String> droppedFeatures) {
        this.droppedFeatures = droppedFeatures != null ? droppedFeatures : new ArrayList<>();
    }

    public com.nexusivr.ai.dto.response.FlowValidationResponse getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(com.nexusivr.ai.dto.response.FlowValidationResponse validationResult) {
        this.validationResult = validationResult;
    }

    public boolean isTemplateFallback() { return templateFallback; }
    public void setTemplateFallback(boolean templateFallback) { this.templateFallback = templateFallback; }

    public String getFallbackNotice() { return fallbackNotice; }
    public void setFallbackNotice(String fallbackNotice) { this.fallbackNotice = fallbackNotice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatResponse)) return false;
        ChatResponse that = (ChatResponse) o;
        return turnNumber == that.turnNumber && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(tenantId, that.tenantId) && Objects.equals(replyMessage, that.replyMessage) &&
                Objects.equals(role, that.role) && Objects.equals(tokensUsed, that.tokensUsed) &&
                Objects.equals(quotaWarnings, that.quotaWarnings) &&
                Objects.equals(selectedProvider, that.selectedProvider) &&
                Objects.equals(actualProviderUsed, that.actualProviderUsed) &&
                fallbackUsed == that.fallbackUsed &&
                Objects.equals(fallbackReason, that.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, tenantId, replyMessage, role, turnNumber, tokensUsed, quotaWarnings, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }
}
