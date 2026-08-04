package com.nexusivr.ai.ai;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the runtime health of a single LLM provider so the
 * {@link ProviderManager} can apply circuit-breaker / backoff behaviour.
 * <p>
 * Each provider maintains:
 * <ul>
 *   <li>status: healthy / unhealthy</li>
 *   <li>last failure timestamp and reason</li>
 *   <li>failure count (rolling window)</li>
 *   <li>cooldown expiration</li>
 *   <li>last success timestamp</li>
 *   <li>consecutive failures</li>
 * </ul>
 * <p>
 * When a provider is unhealthy, it is skipped immediately until its cooldown expires.
 */
public class ProviderHealth {

    enum Status {
        HEALTHY,
        UNHEALTHY
    }

    private volatile Status status = Status.HEALTHY;
    private volatile String lastFailureReason;
    private volatile long lastFailureMs;
    private volatile int failureCount;
    private volatile long cooldownUntilMs;
    private volatile long lastSuccessMs;
    private volatile int consecutiveFailures;

    private static final long DEFAULT_COOLDOWN_MS = 60_000;
    private static final long AUTH_COOLDOWN_MS = 600_000;
    private static final int CONSECUTIVE_FAILURES_THRESHOLD = 5;

    public synchronized boolean isHealthy() {
        if (status == Status.UNHEALTHY) {
            if (System.currentTimeMillis() < cooldownUntilMs) {
                return false;
            }
            status = Status.HEALTHY;
            cooldownUntilMs = 0;
        }
        return true;
    }

    public synchronized void markHealthy() {
        status = Status.HEALTHY;
        cooldownUntilMs = 0;
        consecutiveFailures = 0;
        failureCount = 0;
        lastFailureReason = null;
        lastFailureMs = 0;
        lastSuccessMs = System.currentTimeMillis();
    }

    public synchronized void markUnhealthy(String reason, long cooldownMs) {
        status = Status.UNHEALTHY;
        lastFailureReason = reason;
        lastFailureMs = System.currentTimeMillis();
        failureCount++;
        consecutiveFailures++;
        cooldownUntilMs = System.currentTimeMillis() + cooldownMs;
    }

    public synchronized void markAuthFailure(String reason) {
        markUnhealthy(reason, AUTH_COOLDOWN_MS);
    }

    public synchronized void markRateLimited(String reason) {
        markUnhealthy(reason, DEFAULT_COOLDOWN_MS);
    }

    public synchronized void markServerFailure(String reason) {
        long cooldownMs = computeExponentialBackoff(consecutiveFailures);
        markUnhealthy(reason, cooldownMs);
    }

    public synchronized void recordSuccess() {
        status = Status.HEALTHY;
        cooldownUntilMs = 0;
        consecutiveFailures = 0;
        lastSuccessMs = System.currentTimeMillis();
    }

    public synchronized boolean isRateLimited() {
        return status == Status.UNHEALTHY && lastFailureReason != null
                && (lastFailureReason.toLowerCase().contains("quota")
                || lastFailureReason.toLowerCase().contains("rate limit")
                || lastFailureReason.toLowerCase().contains("429"));
    }

    public synchronized boolean isAuthFailure() {
        return status == Status.UNHEALTHY && lastFailureReason != null
                && (lastFailureReason.toLowerCase().contains("auth")
                || lastFailureReason.toLowerCase().contains("401")
                || lastFailureReason.toLowerCase().contains("403"));
    }

    public synchronized boolean isUnavailable() {
        return status == Status.UNHEALTHY && System.currentTimeMillis() < cooldownUntilMs;
    }

    public String getStatus() {
        return status.name();
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public long getLastFailureMs() {
        return lastFailureMs;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getCooldownRemainingMs() {
        if (status == Status.UNHEALTHY) {
            long remaining = cooldownUntilMs - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
        return 0;
    }

    public long getLastSuccessMs() {
        return lastSuccessMs;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    private static long computeExponentialBackoff(int attempt) {
        long base = 5_000L;
        long max = 120_000L;
        long computed = base * (1L << Math.min(attempt - 1, 6));
        return Math.min(computed, max);
    }
}