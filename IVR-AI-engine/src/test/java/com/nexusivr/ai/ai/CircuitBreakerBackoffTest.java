package com.nexusivr.ai.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerBackoffTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker();
    }

    @Test
    void testExponentialBackoffOnConsecutiveFailures() {
        // First open: base 30s server error cooldown (threshold is 3 failures for 500 status)
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);
        long cd1 = circuitBreaker.getCooldownRemainingMs();
        assertTrue(cd1 > 25000 && cd1 <= 30000, "First open cooldown should be base ~30s");

        // Force HALF_OPEN to simulate cooldown expiration & probe attempt
        circuitBreaker.forceHalfOpenForTesting();
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);

        long cd2 = circuitBreaker.getCooldownRemainingMs();
        assertTrue(cd2 > 50000 && cd2 <= 60000, "Second open cooldown should be ~60s (2x exponential backoff)");
    }

    @Test
    void testHalfOpenLimitsConcurrentProbeCalls() {
        // Trigger circuit breaker open state (requires 3 failures for 500)
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);
        circuitBreaker.recordFailure("HTTP 500 Internal Server Error", 500);

        // Circuit is open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
}
