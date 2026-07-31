package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageTrackerTest {

    @Test
    void testRecordUsage_increasesTokenCount() {
        TokenUsageTracker tracker = TokenUsageTracker.getInstance();
        tracker.reset();
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 100, 50);
        assertEquals(150, tracker.getProviderDailyTokens("groq", "llama-3.3-70b-versatile"));
        tracker.reset();
    }

    @Test
    void testRecordUsage_multipleProviders() {
        TokenUsageTracker tracker = TokenUsageTracker.getInstance();
        tracker.reset();
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 100, 50);
        tracker.recordUsage("gemini", "gemini-2.0-flash", 200, 100);
        assertEquals(150, tracker.getProviderDailyTokens("groq", "llama-3.3-70b-versatile"));
        assertEquals(300, tracker.getProviderDailyTokens("gemini", "gemini-2.0-flash"));
        tracker.reset();
    }

    @Test
    void testRecordUsage_accumulatesForSameProvider() {
        TokenUsageTracker tracker = TokenUsageTracker.getInstance();
        tracker.reset();
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 100, 50);
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 50, 25);
        assertEquals(225, tracker.getProviderDailyTokens("groq", "llama-3.3-70b-versatile"));
        tracker.reset();
    }

    @Test
    void testGetProviderUsageSummary_returnsSummary() {
        TokenUsageTracker tracker = TokenUsageTracker.getInstance();
        tracker.reset();
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 100, 50);
        Map<String, Integer> summary = tracker.getProviderUsageSummary();
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        tracker.reset();
    }

    @Test
    void testReset_clearsUsage() {
        TokenUsageTracker tracker = TokenUsageTracker.getInstance();
        tracker.reset();
        tracker.recordUsage("groq", "llama-3.3-70b-versatile", 100, 50);
        assertFalse(tracker.getProviderUsageSummary().isEmpty());
        tracker.reset();
        assertTrue(tracker.getProviderUsageSummary().isEmpty());
    }

    @Test
    void testSingletonInstance() {
        TokenUsageTracker instance1 = TokenUsageTracker.getInstance();
        TokenUsageTracker instance2 = TokenUsageTracker.getInstance();
        assertSame(instance1, instance2);
    }
}