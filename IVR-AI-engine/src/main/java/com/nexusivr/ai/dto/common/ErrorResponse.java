package com.nexusivr.ai.dto.common;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Standard error envelope returned by every endpoint across all ten
 * modules on failure (validation errors, downstream LLM failures, not
 * found, etc.), so error handling on the client side is uniform instead
 * of bespoke per-feature.
 */
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, Object> details;
    private Instant timestamp;
    private String failureReason;
    private int retryAfterSeconds;
    private Map<String, String> providerStatuses;
    private String selectedProvider;
    private String actualProviderUsed;
    private boolean fallbackUsed;
    private String fallbackReason;

    public ErrorResponse() {
        this.details = new HashMap<>();
        this.timestamp = Instant.now();
        this.providerStatuses = new HashMap<>();
    }

    public ErrorResponse(String code, String message, Map<String, Object> details, Instant timestamp) {
        this(code, message, details, timestamp, null);
    }

    public ErrorResponse(String code, String message, Map<String, Object> details, Instant timestamp, String failureReason) {
        this.code = code;
        this.message = message;
        this.details = details != null ? details : new HashMap<>();
        this.timestamp = timestamp;
        this.failureReason = failureReason;
        this.providerStatuses = new HashMap<>();
    }

    public static ErrorResponse providerUnavailable(String message, Map<String, String> providerStatuses, int retryAfterSeconds) {
        ErrorResponse error = new ErrorResponse(
                "PROVIDER_UNAVAILABLE",
                message,
                new HashMap<>(),
                Instant.now(),
                "No healthy AI provider available"
        );
        error.setRetryAfterSeconds(retryAfterSeconds);
        error.setProviderStatuses(providerStatuses);
        return error;
    }

    public static ErrorResponse providerAuthFailure(String providerName, String message) {
        Map<String, String> statuses = new HashMap<>();
        statuses.put(providerName, "Authentication failed");
        ErrorResponse error = new ErrorResponse(
                "PROVIDER_AUTH_FAILURE",
                message,
                new HashMap<>(),
                Instant.now(),
                "Provider authentication failed"
        );
        error.setProviderStatuses(statuses);
        error.setRetryAfterSeconds(600);
        return error;
    }

    public static ErrorResponse providerQuotaExceeded(String providerName, String message) {
        Map<String, String> statuses = new HashMap<>();
        statuses.put(providerName, "Quota exceeded");
        ErrorResponse error = new ErrorResponse(
                "PROVIDER_QUOTA_EXCEEDED",
                message,
                new HashMap<>(),
                Instant.now(),
                "Provider quota exceeded"
        );
        error.setProviderStatuses(statuses);
        error.setRetryAfterSeconds(300);
        return error;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
    public void setRetryAfterSeconds(int retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }

    public Map<String, String> getProviderStatuses() { return providerStatuses; }
    public void setProviderStatuses(Map<String, String> providerStatuses) { this.providerStatuses = providerStatuses; }

    public String getSelectedProvider() { return selectedProvider; }
    public void setSelectedProvider(String selectedProvider) { this.selectedProvider = selectedProvider; }

    public String getActualProviderUsed() { return actualProviderUsed; }
    public void setActualProviderUsed(String actualProviderUsed) { this.actualProviderUsed = actualProviderUsed; }

    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }

    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", details=" + details +
                ", timestamp=" + timestamp +
                ", failureReason='" + failureReason + '\'' +
                ", retryAfterSeconds=" + retryAfterSeconds +
                ", providerStatuses=" + providerStatuses +
                ", selectedProvider='" + selectedProvider + '\'' +
                ", actualProviderUsed='" + actualProviderUsed + '\'' +
                ", fallbackUsed=" + fallbackUsed +
                ", fallbackReason='" + fallbackReason + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ErrorResponse)) return false;
        ErrorResponse that = (ErrorResponse) o;
        return Objects.equals(code, that.code) && Objects.equals(message, that.message) &&
                Objects.equals(details, that.details) && Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(failureReason, that.failureReason) &&
                retryAfterSeconds == that.retryAfterSeconds &&
                Objects.equals(providerStatuses, that.providerStatuses) &&
                Objects.equals(selectedProvider, that.selectedProvider) &&
                Objects.equals(actualProviderUsed, that.actualProviderUsed) &&
                fallbackUsed == that.fallbackUsed &&
                Objects.equals(fallbackReason, that.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, details, timestamp, failureReason, retryAfterSeconds, providerStatuses, selectedProvider, actualProviderUsed, fallbackUsed, fallbackReason);
    }
}