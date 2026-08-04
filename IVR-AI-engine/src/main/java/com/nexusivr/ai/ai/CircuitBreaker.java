package com.nexusivr.ai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Circuit breaker for LLM provider calls.
 * <p>
 * States:
 * <ul>
 *   <li>CLOSED — normal operation, calls pass through</li>
 *   <li>OPEN — provider is unhealthy, calls are short-circuited immediately</li>
 *   <li>HALF_OPEN — after cooldown, a single probe call is allowed to test recovery</li>
 * </ul>
 * <p>
 * Transition rules:
 * <ul>
 *   <li>CLOSED → OPEN: on authentication failure (401/403) or quota exceeded (429)</li>
 *   <li>CLOSED → OPEN: on consecutive server errors (5xx) exceeding threshold</li>
 *   <li>OPEN → HALF_OPEN: after cooldown period expires</li>
 *   <li>HALF_OPEN → CLOSED: on successful probe call</li>
 *   <li>HALF_OPEN → OPEN: on failed probe call</li>
 * </ul>
 */
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN;

        public String getName() {
            return name();
        }
    }

    public enum ReasonType {
        AUTH_FAILURE,
        QUOTA_EXCEEDED,
        SERVER_ERROR,
        UNKNOWN
    }

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String provider;
    private volatile State state = State.CLOSED;
    private volatile long openedAtMs;
    private volatile long cooldownMs;
    private volatile String lastFailureReason;
    private volatile int consecutiveFailures;
    private volatile int consecutiveSuccesses;

    private static final int HALF_OPEN_MAX_CALLS = 1;
    private static final int SUCCESS_THRESHOLD_FOR_CLOSE = 1;

    private volatile int openCount = 0;
    private volatile int halfOpenCallsInFlight = 0;

    public CircuitBreaker() {
        this(null);
    }

    public CircuitBreaker(String provider) {
        this.provider = provider;
    }

    public synchronized boolean isCallPermitted() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (System.currentTimeMillis() >= openedAtMs + cooldownMs) {
                    transitionToHalfOpen();
                    halfOpenCallsInFlight = 1;
                    return true;
                }
                logger.debug("[CircuitBreaker] OPEN — call blocked. Cooldown remaining: {}ms",
                        getCooldownRemainingMs());
                return false;
            case HALF_OPEN:
                if (halfOpenCallsInFlight < HALF_OPEN_MAX_CALLS) {
                    halfOpenCallsInFlight++;
                    return true;
                }
                logger.debug("[CircuitBreaker] HALF_OPEN — probe call already in flight; blocking concurrent request.");
                return false;
            default:
                return true;
        }
    }

    public synchronized void recordSuccess() {
        halfOpenCallsInFlight = 0;
        switch (state) {
            case HALF_OPEN:
                consecutiveSuccesses++;
                if (consecutiveSuccesses >= SUCCESS_THRESHOLD_FOR_CLOSE) {
                    transitionToClosed();
                }
                break;
            case CLOSED:
                consecutiveFailures = 0;
                break;
            case OPEN:
                // Provider recovered while circuit was open — close it immediately
                transitionToClosed();
                break;
        }
    }

    public synchronized void recordFailure(String reason, int statusCode) {
        halfOpenCallsInFlight = 0;
        consecutiveFailures++;
        lastFailureReason = reason;

        switch (state) {
            case CLOSED:
                if (isPermanentFailure(statusCode, reason)) {
                    transitionToOpen(reason, computeCooldown(statusCode, reason));
                } else if (consecutiveFailures >= com.nexusivr.ai.config.LlmConfig.getCircuitBreakerFailureThreshold()) {
                    transitionToOpen(reason, computeCooldown(statusCode, reason));
                }
                break;
            case HALF_OPEN:
                transitionToOpen(reason, computeCooldown(statusCode, reason));
                break;
            case OPEN:
                break;
        }
    }

    public synchronized State getState() {
        return state;
    }

    /**
     * Returns the current circuit breaker state as a human-readable string.
     */
    public synchronized String getStateName() {
        return state.name();
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public long getCooldownRemainingMs() {
        if (state == State.OPEN) {
            long remaining = openedAtMs + cooldownMs - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
        return 0;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    private void transitionToOpen(String reason, long computedCooldownMs) {
        this.state = State.OPEN;
        this.openedAtMs = System.currentTimeMillis();
        this.cooldownMs = computedCooldownMs;
        this.lastFailureReason = reason;
        this.consecutiveSuccesses = 0;
        this.openCount++;
        logger.warn("[CircuitBreaker] State transition: CLOSED/HALF_OPEN → OPEN. Reason: '{}'. Cooldown: {}ms (openCount: {})",
                reason, computedCooldownMs, openCount);
    }

    void forceHalfOpenForTesting() {
        transitionToHalfOpen();
    }

    private void transitionToHalfOpen() {
        this.state = State.HALF_OPEN;
        this.consecutiveSuccesses = 0;
        logger.info("[CircuitBreaker] State transition: OPEN → HALF_OPEN. Probing provider recovery.");
    }

    private void transitionToClosed() {
        this.state = State.CLOSED;
        this.consecutiveFailures = 0;
        this.lastFailureReason = null;
        this.openedAtMs = 0;
        this.cooldownMs = 0;
        this.halfOpenCallsInFlight = 0;
        this.openCount = 0;
        logger.info("[CircuitBreaker] State transition: HALF_OPEN/OPEN → CLOSED. Provider recovered.");
    }

    public synchronized void reset() {
        transitionToClosed();
        this.consecutiveFailures = 0;
        this.consecutiveSuccesses = 0;
        this.lastFailureReason = null;
        this.openedAtMs = 0;
        this.cooldownMs = 0;
        this.halfOpenCallsInFlight = 0;
        this.openCount = 0;
        logger.info("[CircuitBreaker] Circuit breaker manually reset to CLOSED.");
    }

    public boolean isAuthFailure() {
        if (lastFailureReason != null && (lastFailureReason.toLowerCase().contains("auth") || lastFailureReason.contains("401") || lastFailureReason.contains("403"))) {
            return true;
        }
        return false;
    }

    public ReasonType getOpenReasonType() {
        if (isAuthFailure()) return ReasonType.AUTH_FAILURE;
        if (lastFailureReason != null && (lastFailureReason.toLowerCase().contains("quota") || lastFailureReason.contains("429"))) return ReasonType.QUOTA_EXCEEDED;
        if (lastFailureReason != null && lastFailureReason.toLowerCase().contains("server")) return ReasonType.SERVER_ERROR;
        return ReasonType.UNKNOWN;
    }

    private boolean isPermanentFailure(int statusCode, String reason) {
        if (statusCode == 401 || statusCode == 403) return true;
        if (reason != null && (reason.toLowerCase().contains("auth")
                || reason.toLowerCase().contains("quota exceeded"))) return true;
        return false;
    }

    private long computeCooldown(int statusCode, String reason) {
        long baseCooldown;
        if (statusCode == 401 || statusCode == 403) {
            baseCooldown = com.nexusivr.ai.config.LlmConfig.getCircuitBreakerAuthCooldownMs();
        } else if (reason != null && reason.toLowerCase().contains("quota exceeded")) {
            baseCooldown = com.nexusivr.ai.config.LlmConfig.getCircuitBreakerQuotaCooldownMs();
        } else if (statusCode >= 500) {
            baseCooldown = com.nexusivr.ai.config.LlmConfig.getCircuitBreakerServerErrorCooldownMs();
        } else {
            baseCooldown = 60_000L;
        }

        if (provider != null && "openrouter".equalsIgnoreCase(provider)) {
            // Keep CircuitBreaker cooldown short (60-120s) for experimental provider openrouter
            return Math.min(120_000L, Math.max(60_000L, baseCooldown));
        }

        // Apply exponential backoff multiplier based on openCount (1st open = 1x, 2nd open = 2x, 3rd open = 4x, etc.)
        int factor = Math.min(16, (int) Math.pow(2, Math.max(0, openCount)));
        return Math.min(600_000L, baseCooldown * factor);
    }
}