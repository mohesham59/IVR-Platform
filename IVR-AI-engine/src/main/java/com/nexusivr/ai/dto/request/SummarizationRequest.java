package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.SummaryType;

import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Summarization module. Always operates on a specific
 * session's transcript (sessionId, required). customerIdentifier is
 * required so the resulting conversation_history row can be found again
 * by customer across sessions; it is passed explicitly rather than
 * looked up via sessionId so summarization does not have a hidden
 * read-then-write dependency on ai_sessions being queried first.
 */
public class SummarizationRequest {

    private UUID tenantId;
    private UUID sessionId;
    private String customerIdentifier;
    private SummaryType summaryType;
    private Integer maxLength;

    public SummarizationRequest() {
    }

    public SummarizationRequest(UUID tenantId, UUID sessionId, String customerIdentifier,
                                 SummaryType summaryType, Integer maxLength) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.customerIdentifier = customerIdentifier;
        this.summaryType = summaryType;
        this.maxLength = maxLength;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getCustomerIdentifier() { return customerIdentifier; }
    public void setCustomerIdentifier(String customerIdentifier) { this.customerIdentifier = customerIdentifier; }

    public SummaryType getSummaryType() { return summaryType; }
    public void setSummaryType(SummaryType summaryType) { this.summaryType = summaryType; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    @Override
    public String toString() {
        return "SummarizationRequest{" +
                "tenantId=" + tenantId +
                ", sessionId=" + sessionId +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", summaryType=" + summaryType +
                ", maxLength=" + maxLength +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SummarizationRequest)) return false;
        SummarizationRequest that = (SummarizationRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(customerIdentifier, that.customerIdentifier) && summaryType == that.summaryType &&
                Objects.equals(maxLength, that.maxLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sessionId, customerIdentifier, summaryType, maxLength);
    }
}
