package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.TokenUsageDto;

import java.util.Objects;
import java.util.UUID;

/**
 * Output of the Summarization module. conversationHistoryId is nullable:
 * the summarization step (calling the LLM to produce summaryText) and
 * the persistence step (writing a conversation_history row) are treated
 * as separable — a caller previewing a summary before deciding to save
 * it gets a populated summaryText with a null id, while a caller that
 * asked the service to persist immediately gets the row's real id back.
 */
public class SummarizationResponse {

    private UUID conversationHistoryId;
    private String summaryText;
    private int sourceMessageCount;
    private TokenUsageDto tokenUsage;

    public SummarizationResponse() {
    }

    public SummarizationResponse(UUID conversationHistoryId, String summaryText, int sourceMessageCount,
                                  TokenUsageDto tokenUsage) {
        this.conversationHistoryId = conversationHistoryId;
        this.summaryText = summaryText;
        this.sourceMessageCount = sourceMessageCount;
        this.tokenUsage = tokenUsage;
    }

    public UUID getConversationHistoryId() { return conversationHistoryId; }
    public void setConversationHistoryId(UUID conversationHistoryId) { this.conversationHistoryId = conversationHistoryId; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public int getSourceMessageCount() { return sourceMessageCount; }
    public void setSourceMessageCount(int sourceMessageCount) { this.sourceMessageCount = sourceMessageCount; }

    public TokenUsageDto getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(TokenUsageDto tokenUsage) { this.tokenUsage = tokenUsage; }

    @Override
    public String toString() {
        return "SummarizationResponse{" +
                "conversationHistoryId=" + conversationHistoryId +
                ", summaryText='" + summaryText + '\'' +
                ", sourceMessageCount=" + sourceMessageCount +
                ", tokenUsage=" + tokenUsage +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SummarizationResponse)) return false;
        SummarizationResponse that = (SummarizationResponse) o;
        return sourceMessageCount == that.sourceMessageCount &&
                Objects.equals(conversationHistoryId, that.conversationHistoryId) &&
                Objects.equals(summaryText, that.summaryText) && Objects.equals(tokenUsage, that.tokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationHistoryId, summaryText, sourceMessageCount, tokenUsage);
    }
}
