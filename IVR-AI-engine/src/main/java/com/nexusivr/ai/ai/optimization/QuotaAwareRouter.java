package com.nexusivr.ai.ai.optimization;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quota-aware provider routing with cost optimization.
 * <p>
 * Tracks daily token budgets per provider and routes requests
 * to the most cost-effective available provider.
 * </p>
 */
public class QuotaAwareRouter {

    private static final QuotaAwareRouter INSTANCE = new QuotaAwareRouter();

    public static QuotaAwareRouter getInstance() {
        return INSTANCE;
    }

    private final Map<String, DailyQuota> dailyQuotas = new ConcurrentHashMap<>();
    private final Map<String, Long> dailyTokenBudgets = new ConcurrentHashMap<>();

    private static final long DAILY_WINDOW_MS = 24 * 60 * 60 * 1000L;

    public void setDailyBudget(String provider, long tokens) {
        dailyTokenBudgets.put(provider.toLowerCase(), tokens);
    }

    public boolean isWithinQuota(String provider, int tokens) {
        String key = provider.toLowerCase();
        DailyQuota quota = dailyQuotas.computeIfAbsent(key, k -> new DailyQuota());
        return quota.isWithinBudget(tokens);
    }

    public void recordUsage(String provider, int tokens) {
        String key = provider.toLowerCase();
        DailyQuota quota = dailyQuotas.computeIfAbsent(key, k -> new DailyQuota());
        quota.addUsage(tokens);
    }

    public String selectProvider(List<String> availableProviders, int estimatedTokens, boolean preferCheap) {
        if (availableProviders == null || availableProviders.isEmpty()) {
            return null;
        }

        if (preferCheap) {
            return availableProviders.stream()
                    .filter(p -> isWithinQuota(p, estimatedTokens))
                    .min(Comparator.comparingDouble(p -> ProviderCostCatalog.getTierMultiplier(getModelForProvider(p))))
                    .orElse(availableProviders.get(0));
        }

        return availableProviders.stream()
                .filter(p -> isWithinQuota(p, estimatedTokens))
                .findFirst()
                .orElse(availableProviders.get(0));
    }

    public Map<String, Long> getDailyUsage() {
        Map<String, Long> usage = new LinkedHashMap<>();
        for (Map.Entry<String, DailyQuota> entry : dailyQuotas.entrySet()) {
            usage.put(entry.getKey(), entry.getValue().getTodayTokens());
        }
        return usage;
    }

    public void resetDaily() {
        dailyQuotas.clear();
    }

    private String getModelForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "groq" -> "llama-3.3-70b-versatile";
            case "gemini" -> "gemini-2.0-flash";
            default -> "default";
        };
    }

    private static class DailyQuota {
        private long tokensToday;
        private long windowStart;

        synchronized boolean isWithinBudget(int tokens) {
            long now = System.currentTimeMillis();
            if (now - windowStart > DAILY_WINDOW_MS) {
                tokensToday = 0;
                windowStart = now;
            }
            return tokensToday + tokens <= getBudget();
        }

        synchronized void addUsage(int tokens) {
            long now = System.currentTimeMillis();
            if (now - windowStart > DAILY_WINDOW_MS) {
                tokensToday = 0;
                windowStart = now;
            }
            tokensToday += tokens;
        }

        synchronized long getTodayTokens() {
            long now = System.currentTimeMillis();
            if (now - windowStart > DAILY_WINDOW_MS) {
                tokensToday = 0;
                windowStart = now;
            }
            return tokensToday;
        }

        private long getBudget() {
            return 1_000_000L;
        }
    }
}
