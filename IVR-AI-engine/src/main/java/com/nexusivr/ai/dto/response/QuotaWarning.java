package com.nexusivr.ai.dto.response;

import java.util.Objects;

/**
 * Lightweight record of a single provider rate-limit (429) event
 * encountered while processing one API request.
 */
public class QuotaWarning {
    private String provider;
    private String model;
    private int attempt;

    public QuotaWarning() {
    }

    public QuotaWarning(String provider, String model, int attempt) {
        this.provider = provider;
        this.model = model;
        this.attempt = attempt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    @Override
    public String toString() {
        return "QuotaWarning{" +
                "provider='" + provider + '\'' +
                ", model='" + model + '\'' +
                ", attempt=" + attempt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuotaWarning)) return false;
        QuotaWarning that = (QuotaWarning) o;
        return attempt == that.attempt && Objects.equals(provider, that.provider) && Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, model, attempt);
    }
}
