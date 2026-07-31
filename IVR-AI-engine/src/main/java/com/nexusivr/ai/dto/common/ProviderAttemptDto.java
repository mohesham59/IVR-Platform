package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * Details of a provider execution attempt during AI generation or improvement.
 */
public class ProviderAttemptDto {

    private String provider;
    private int status;
    private String reason;
    private int cooldownSeconds;

    public ProviderAttemptDto() {
        this.provider = "";
        this.reason = "";
    }

    public ProviderAttemptDto(String provider, int status, String reason, int cooldownSeconds) {
        this.provider = provider != null ? provider : "";
        this.status = status;
        this.reason = reason != null ? reason : "";
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider != null ? provider : ""; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason != null ? reason : ""; }

    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = Math.max(0, cooldownSeconds); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderAttemptDto that)) return false;
        return status == that.status && cooldownSeconds == that.cooldownSeconds &&
                Objects.equals(provider, that.provider) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, status, reason, cooldownSeconds);
    }

    @Override
    public String toString() {
        return "ProviderAttemptDto{" +
                "provider='" + provider + '\'' +
                ", status=" + status +
                ", reason='" + reason + '\'' +
                ", cooldownSeconds=" + cooldownSeconds +
                '}';
    }
}
