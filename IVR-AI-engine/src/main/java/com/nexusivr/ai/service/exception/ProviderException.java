package com.nexusivr.ai.service.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps any failure surfaced by a Provider (LLM call, rule engine,
 * scoring model, aggregation query). A service never lets a Provider's
 * native exception/checked-exception type escape its boundary; it is
 * always translated into this type first.
 */
public class ProviderException extends ServiceException {

    public enum FailureReason {
        RATE_LIMITED,
        QUOTA_EXCEEDED,
        PROVIDER_ERROR,
        TIMEOUT,
        AUTH_FAILURE,
        PROVIDER_UNAVAILABLE
    }

    private final FailureReason reason;
    private final String providerName;
    private final int statusCode;
    private final Map<String, String> providerStatuses;
    private final java.util.List<com.nexusivr.ai.dto.common.ProviderAttemptDto> providerAttempts;

    public ProviderException(String providerName, String message, Throwable cause, FailureReason reason) {
        this(providerName, message, cause, reason, Collections.emptyMap(), Collections.emptyList());
    }

    public ProviderException(String providerName, String message, FailureReason reason) {
        this(providerName, message, null, reason, Collections.emptyMap(), Collections.emptyList());
    }

    public ProviderException(String providerName, String message, Throwable cause) {
        this(providerName, message, cause, FailureReason.PROVIDER_ERROR, Collections.emptyMap(), Collections.emptyList());
    }

    public ProviderException(String providerName, String message, int statusCode, FailureReason reason) {
        this(providerName, message, null, reason, Collections.emptyMap(), Collections.emptyList());
    }

    public ProviderException(String providerName, String message, FailureReason reason, Map<String, String> providerStatuses) {
        this(providerName, message, null, reason, providerStatuses, Collections.emptyList());
    }

    public ProviderException(String providerName, String message, Throwable cause, FailureReason reason, Map<String, String> providerStatuses) {
        this(providerName, message, cause, reason, providerStatuses, Collections.emptyList());
    }

    public ProviderException(String providerName, String message, FailureReason reason, Map<String, String> providerStatuses, java.util.List<com.nexusivr.ai.dto.common.ProviderAttemptDto> providerAttempts) {
        this(providerName, message, null, reason, providerStatuses, providerAttempts);
    }

    public ProviderException(String providerName, String message, Throwable cause, FailureReason reason, Map<String, String> providerStatuses, java.util.List<com.nexusivr.ai.dto.common.ProviderAttemptDto> providerAttempts) {
        super("PROVIDER_ERROR", "Provider [" + providerName + "] failed: " + message, cause);
        this.reason = reason != null ? reason : FailureReason.PROVIDER_ERROR;
        this.providerName = providerName;
        this.statusCode = 0;
        this.providerStatuses = providerStatuses != null ? new HashMap<>(providerStatuses) : new HashMap<>();
        this.providerAttempts = providerAttempts != null ? new java.util.ArrayList<>(providerAttempts) : Collections.emptyList();
    }

    public FailureReason getReason() {
        return reason;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getProviderStatuses() {
        return Collections.unmodifiableMap(providerStatuses);
    }

    public java.util.List<com.nexusivr.ai.dto.common.ProviderAttemptDto> getProviderAttempts() {
        return Collections.unmodifiableList(providerAttempts);
    }
}