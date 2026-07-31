package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.FunctionCallDto;
import com.nexusivr.ai.dto.common.ProviderAttemptDto;
import com.nexusivr.ai.dto.common.SentimentScoreDto;
import com.nexusivr.ai.dto.common.TokenUsageDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Output of one AI Chat turn. sessionId is always populated — including
 * when the request's sessionId was null — so the caller learns the
 * newly created session's id and can pass it back on the next turn.
 * functionCalls is populated instead of (or alongside) assistantMessage
 * when the model chose to invoke a tool rather than reply in plain text;
 * sentiment is an optional, cheap inline reading of the user's turn so
 * callers don't have to make a second round trip to the Sentiment
 * module for the common case of "just tell me if this user is upset."
 */
public class ChatResponse {

    private UUID sessionId;
    private UUID messageId;
    private int turnNumber;
    private String assistantMessage;
    private TokenUsageDto tokenUsage;
    private List<FunctionCallDto> functionCalls;
    private SentimentScoreDto sentiment;
    private Instant createdAt;
    private List<QuotaWarning> quotaWarnings;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;
    private List<ProviderAttemptDto> providerAttempts;

    public ChatResponse() {
        this.functionCalls = new ArrayList<>();
        this.quotaWarnings = new ArrayList<>();
        this.providerAttempts = new ArrayList<>();
    }

    public ChatResponse(UUID sessionId, UUID messageId, int turnNumber, String assistantMessage,
                         TokenUsageDto tokenUsage, List<FunctionCallDto> functionCalls,
                         SentimentScoreDto sentiment, Instant createdAt) {
        this(sessionId, messageId, turnNumber, assistantMessage, tokenUsage, functionCalls, sentiment, createdAt, null, null, false, null);
    }

    public ChatResponse(UUID sessionId, UUID messageId, int turnNumber, String assistantMessage,
                         TokenUsageDto tokenUsage, List<FunctionCallDto> functionCalls,
                         SentimentScoreDto sentiment, Instant createdAt,
                         String selectedProvider, String actualProviderUsed, boolean fallbackUsed, String fallbackReason) {
        this.sessionId = sessionId;
        this.messageId = messageId;
        this.turnNumber = turnNumber;
        this.assistantMessage = assistantMessage;
        this.tokenUsage = tokenUsage;
        this.functionCalls = functionCalls != null ? functionCalls : new ArrayList<>();
        this.sentiment = sentiment;
        this.createdAt = createdAt;
        this.quotaWarnings = new ArrayList<>();
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = fallbackReason != null ? fallbackReason : "";
        this.providerAttempts = new ArrayList<>();
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    public String getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(String assistantMessage) { this.assistantMessage = assistantMessage; }

    public TokenUsageDto getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(TokenUsageDto tokenUsage) { this.tokenUsage = tokenUsage; }

    public List<FunctionCallDto> getFunctionCalls() { return functionCalls; }
    public void setFunctionCalls(List<FunctionCallDto> functionCalls) { this.functionCalls = functionCalls; }

    public SentimentScoreDto getSentiment() { return sentiment; }
    public void setSentiment(SentimentScoreDto sentiment) { this.sentiment = sentiment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

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

    @Override
    public String toString() {
        return "ChatResponse{" +
                "sessionId=" + sessionId +
                ", messageId=" + messageId +
                ", turnNumber=" + turnNumber +
                ", assistantMessage='" + assistantMessage + '\'' +
                ", tokenUsage=" + tokenUsage +
                ", functionCalls=" + functionCalls +
                ", sentiment=" + sentiment +
                ", createdAt=" + createdAt +
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
        if (!(o instanceof ChatResponse)) return false;
        ChatResponse that = (ChatResponse) o;
        return turnNumber == that.turnNumber && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(messageId, that.messageId) && Objects.equals(assistantMessage, that.assistantMessage) &&
                Objects.equals(tokenUsage, that.tokenUsage) && Objects.equals(functionCalls, that.functionCalls) &&
                Objects.equals(sentiment, that.sentiment) && Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(quotaWarnings, that.quotaWarnings) &&
                Objects.equals(selectedProvider, that.selectedProvider) &&
                Objects.equals(actualProviderUsed, that.actualProviderUsed) &&
                fallbackUsed == that.fallbackUsed &&
                Objects.equals(fallbackReason, that.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, messageId, turnNumber, assistantMessage, tokenUsage, functionCalls, sentiment, createdAt, quotaWarnings, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }
}
