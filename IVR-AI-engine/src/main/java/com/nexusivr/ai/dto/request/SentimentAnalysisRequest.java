package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.AnalysisScope;

import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Sentiment module. When scope is MESSAGE, `text` is
 * required and sessionId is ignored/optional (ad-hoc scoring of any
 * string, e.g. a draft response before it's sent). When scope is
 * SESSION, sessionId is required and `text` is ignored — the service
 * reads the full transcript from ai_messages instead.
 */
public class SentimentAnalysisRequest {

    private UUID tenantId;
    private AnalysisScope scope;
    private UUID sessionId;
    private String text;
    private String content;

    public SentimentAnalysisRequest() {
    }

    public SentimentAnalysisRequest(UUID tenantId, AnalysisScope scope, UUID sessionId, String text) {
        this.tenantId = tenantId;
        this.scope = scope;
        this.sessionId = sessionId;
        this.text = text;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public AnalysisScope getScope() { return scope; }
    public void setScope(AnalysisScope scope) { this.scope = scope; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getText() {
        return text != null ? text : content;
    }
    public void setText(String text) { this.text = text; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public String toString() {
        return "SentimentAnalysisRequest{" +
                "tenantId=" + tenantId +
                ", scope=" + scope +
                ", sessionId=" + sessionId +
                ", text='" + text + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SentimentAnalysisRequest)) return false;
        SentimentAnalysisRequest that = (SentimentAnalysisRequest) o;
        return Objects.equals(tenantId, that.tenantId) && scope == that.scope &&
                Objects.equals(sessionId, that.sessionId) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, scope, sessionId, text);
    }
}
