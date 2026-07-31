package com.nexusivr.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structured specification of an IVR call flow configuration extracted by the Planning Engine.
 */
public class IvrPlan {
    private String businessDomain;
    private String ivrObjective;
    private Object requiredDepartments;
    private Object greetingRequirements;
    private Object businessHours;
    private Object requiredMenuOptions;
    private Object queueRequirements;
    private Object transferRequirements;
    private Object apiRequirements;
    private Object databaseLookupRequirements;
    private Object authenticationRequirements;
    private String expectedCallEnding;
    private String errorHandling;
    private String timeoutHandling;
    private String retryPolicy;
    private String fallbackRouting;

    public IvrPlan() {}

    @SuppressWarnings("unchecked")
    private List<String> normalizeToStringList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (Object v : map.values()) {
                if (v instanceof List) {
                    List<?> list = (List<?>) v;
                    List<String> result = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null) result.add(item.toString());
                    }
                    return result;
                }
            }
            return new ArrayList<>();
        }
        if (value instanceof String) return List.of((String) value);
        return new ArrayList<>();
    }

    private boolean normalizeToBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (Object v : map.values()) {
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof String) return Boolean.parseBoolean((String) v);
            }
            return true;
        }
        if (value instanceof List) return !((List<?>) value).isEmpty();
        return false;
    }

    // Getters and Setters
    public String getBusinessDomain() { return businessDomain; }
    public void setBusinessDomain(String businessDomain) { this.businessDomain = businessDomain; }

    public String getIvrObjective() { return ivrObjective; }
    public void setIvrObjective(String ivrObjective) { this.ivrObjective = ivrObjective; }

    public List<String> getRequiredDepartments() {
        return normalizeToStringList(requiredDepartments);
    }

    public void setRequiredDepartments(Object requiredDepartments) {
        this.requiredDepartments = requiredDepartments;
    }

    public boolean isGreetingRequirements() {
        return normalizeToBoolean(greetingRequirements);
    }

    public void setGreetingRequirements(Object greetingRequirements) {
        this.greetingRequirements = greetingRequirements;
    }

    public boolean isBusinessHours() {
        return normalizeToBoolean(businessHours);
    }

    public void setBusinessHours(Object businessHours) {
        this.businessHours = businessHours;
    }

    public List<String> getRequiredMenuOptions() {
        return normalizeToStringList(requiredMenuOptions);
    }

    public void setRequiredMenuOptions(Object requiredMenuOptions) {
        this.requiredMenuOptions = requiredMenuOptions;
    }

    public boolean isQueueRequirements() {
        return normalizeToBoolean(queueRequirements);
    }

    public void setQueueRequirements(Object queueRequirements) {
        this.queueRequirements = queueRequirements;
    }

    public List<String> getTransferRequirements() {
        return normalizeToStringList(transferRequirements);
    }

    public void setTransferRequirements(Object transferRequirements) {
        this.transferRequirements = transferRequirements;
    }

    public List<String> getApiRequirements() {
        return normalizeToStringList(apiRequirements);
    }

    public void setApiRequirements(Object apiRequirements) {
        this.apiRequirements = apiRequirements;
    }

    public List<String> getDatabaseLookupRequirements() {
        return normalizeToStringList(databaseLookupRequirements);
    }

    public void setDatabaseLookupRequirements(Object databaseLookupRequirements) {
        this.databaseLookupRequirements = databaseLookupRequirements;
    }

    public List<String> getAuthenticationRequirements() {
        return normalizeToStringList(authenticationRequirements);
    }

    public void setAuthenticationRequirements(Object authenticationRequirements) {
        this.authenticationRequirements = authenticationRequirements;
    }

    public String getExpectedCallEnding() { return expectedCallEnding; }
    public void setExpectedCallEnding(String expectedCallEnding) { this.expectedCallEnding = expectedCallEnding; }

    public String getErrorHandling() { return errorHandling; }
    public void setErrorHandling(String errorHandling) { this.errorHandling = errorHandling; }

    public String getTimeoutHandling() { return timeoutHandling; }
    public void setTimeoutHandling(String timeoutHandling) { this.timeoutHandling = timeoutHandling; }

    public String getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(String retryPolicy) { this.retryPolicy = retryPolicy; }

    public String getFallbackRouting() { return fallbackRouting; }
    public void setFallbackRouting(String fallbackRouting) { this.fallbackRouting = fallbackRouting; }
}
