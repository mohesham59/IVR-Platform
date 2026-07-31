package com.nexusivr.ai.ai.optimization;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks token usage per provider/model/session for quota-aware routing.
 */
public class TokenUsageTracker {

    private static final TokenUsageTracker INSTANCE = new TokenUsageTracker();

    public static TokenUsageTracker getInstance() {
        return INSTANCE;
    }

    private final Map<String, UsageRecord> providerUsage = new ConcurrentHashMap<>();
    private final Map<UUID, SessionUsage> sessionUsage = new ConcurrentHashMap<>();

    private static final long DAILY_WINDOW_MS = 24 * 60 * 60 * 1000L;

    public void recordUsage(String provider, String model, int promptTokens, int completionTokens) {
        String key = provider + ":" + model;
        UsageRecord record = providerUsage.computeIfAbsent(key, k -> new UsageRecord());
        record.addUsage(promptTokens, completionTokens);
    }

    public void recordSessionUsage(UUID sessionId, String provider, int tokens) {
        SessionUsage session = sessionUsage.computeIfAbsent(sessionId, k -> new SessionUsage());
        session.recordUsage(provider, tokens);
    }

    public int getProviderDailyTokens(String provider, String model) {
        String key = provider + ":" + model;
        UsageRecord record = providerUsage.get(key);
        if (record == null) return 0;
        long cutoff = System.currentTimeMillis() - DAILY_WINDOW_MS;
        return record.getTokensSince(cutoff);
    }

    public int getSessionTokens(UUID sessionId) {
        SessionUsage session = sessionUsage.get(sessionId);
        return session != null ? session.getTotalTokens() : 0;
    }

    public Map<String, Integer> getProviderUsageSummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (Map.Entry<String, UsageRecord> entry : providerUsage.entrySet()) {
            summary.put(entry.getKey(), entry.getValue().getTotalTokens());
        }
        return summary;
    }

    public void reset() {
        providerUsage.clear();
        sessionUsage.clear();
    }

    private static class UsageRecord {
        private final List<TokenUsage> usages = new ArrayList<>();

        synchronized void addUsage(int promptTokens, int completionTokens) {
            usages.add(new TokenUsage(System.currentTimeMillis(), promptTokens, completionTokens));
        }

        synchronized int getTokensSince(long cutoff) {
            return usages.stream()
                    .filter(u -> u.timestamp >= cutoff)
                    .mapToInt(u -> u.promptTokens + u.completionTokens)
                    .sum();
        }

        synchronized int getTotalTokens() {
            return usages.stream().mapToInt(u -> u.promptTokens + u.completionTokens).sum();
        }
    }

    private static class SessionUsage {
        private final Map<String, Integer> providerTokens = new HashMap<>();
        private int totalTokens;

        synchronized void recordUsage(String provider, int tokens) {
            providerTokens.merge(provider, tokens, Integer::sum);
            totalTokens += tokens;
        }

        synchronized int getTotalTokens() {
            return totalTokens;
        }
    }

    private record TokenUsage(long timestamp, int promptTokens, int completionTokens) {
    }
}
