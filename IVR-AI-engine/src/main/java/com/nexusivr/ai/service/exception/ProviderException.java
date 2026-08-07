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
    private final String actualProviderUsed;
    private final boolean fallbackUsed;
    private final java.util.List<String> providersAttempted;

    public ProviderException(String providerName, String message, Throwable cause, FailureReason reason) {
        this(providerName, message, cause, reason, Collections.emptyMap());
    }

    public ProviderException(String providerName, String message, FailureReason reason) {
        this(providerName, message, null, reason, Collections.emptyMap());
    }

    public ProviderException(String providerName, String message, Throwable cause) {
        this(providerName, message, cause, FailureReason.PROVIDER_ERROR, Collections.emptyMap());
    }

    public ProviderException(String providerName, String message, int statusCode, FailureReason reason) {
        super("PROVIDER_ERROR", "Provider [" + providerName + "] failed: " + message, null);
        this.reason = reason != null ? reason : FailureReason.PROVIDER_ERROR;
        this.providerName = providerName;
        this.statusCode = statusCode;
        this.providerStatuses = Collections.emptyMap();
        this.actualProviderUsed = "none";
        this.fallbackUsed = false;
        this.providersAttempted = Collections.emptyList();
    }

    public ProviderException(String providerName, String message, Throwable cause, FailureReason reason, Map<String, String> providerStatuses) {
        super("PROVIDER_ERROR", "Provider [" + providerName + "] failed: " + message, cause);
        this.reason = reason != null ? reason : FailureReason.PROVIDER_ERROR;
        this.providerName = providerName;
        this.statusCode = 0;
        this.providerStatuses = providerStatuses != null ? new HashMap<>(providerStatuses) : new HashMap<>();
        this.actualProviderUsed = "none";
        this.fallbackUsed = false;
        this.providersAttempted = Collections.emptyList();
    }

    public ProviderException(String providerName, String message, FailureReason reason, Map<String, String> providerStatuses) {
        super("PROVIDER_ERROR", "Provider [" + providerName + "] failed: " + message, null);
        this.reason = reason != null ? reason : FailureReason.PROVIDER_ERROR;
        this.providerName = providerName;
        this.statusCode = 0;
        this.providerStatuses = providerStatuses != null ? new HashMap<>(providerStatuses) : new HashMap<>();
        this.actualProviderUsed = "none";
        this.fallbackUsed = false;
        this.providersAttempted = Collections.emptyList();
    }

    public ProviderException(String providerName, String message, FailureReason reason, 
                             Map<String, String> providerStatuses, String actualProviderUsed, 
                             boolean fallbackUsed, java.util.List<String> providersAttempted) {
        super("PROVIDER_ERROR", "Provider [" + providerName + "] failed: " + message, null);
        this.reason = reason != null ? reason : FailureReason.PROVIDER_ERROR;
        this.providerName = providerName;
        this.statusCode = 0;
        this.providerStatuses = providerStatuses != null ? new HashMap<>(providerStatuses) : new HashMap<>();
        this.actualProviderUsed = actualProviderUsed;
        this.fallbackUsed = fallbackUsed;
        this.providersAttempted = providersAttempted != null ? new java.util.ArrayList<>(providersAttempted) : Collections.emptyList();
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

    public String getActualProviderUsed() {
        return actualProviderUsed;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public java.util.List<String> getProvidersAttempted() {
        return Collections.unmodifiableList(providersAttempted);
    }
}