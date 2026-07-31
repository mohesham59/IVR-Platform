package com.nexusivr.ai.ai.optimization;

import java.util.Map;

/**
 * Cost catalog for LLM providers/models.
 * Costs are per 1M tokens (USD).
 */
public class ProviderCostCatalog {

    public static final Map<String, Double> PROMPT_COST_PER_1M;
    public static final Map<String, Double> COMPLETION_COST_PER_1M;
    public static final Map<String, Double> TIER_MULTIPLIER;

    static {
        Map<String, Double> prompt = new java.util.LinkedHashMap<>();
        Map<String, Double> completion = new java.util.LinkedHashMap<>();
        Map<String, Double> tier = new java.util.LinkedHashMap<>();

        prompt.put("groq:llama-3.3-70b-versatile", 0.59);
        completion.put("groq:llama-3.3-70b-versatile", 0.79);
        prompt.put("groq:llama-3.1-8b-instant", 0.05);
        completion.put("groq:llama-3.1-8b-instant", 0.08);
        prompt.put("groq:llama-3.1-70b", 0.59);
        completion.put("groq:llama-3.1-70b", 0.79);
        prompt.put("gemini:gemini-2.0-flash", 0.075);
        completion.put("gemini:gemini-2.0-flash", 0.30);
        prompt.put("gemini:gemini-1.5-flash", 0.075);
        completion.put("gemini:gemini-1.5-flash", 0.30);

        tier.put("llama-3.1-8b-instant", 1.0);
        tier.put("llama-3.3-70b-versatile", 3.0);
        tier.put("llama-3.1-70b", 3.0);
        tier.put("gemini-1.5-flash", 2.0);
        tier.put("gemini-2.0-flash", 2.5);

        PROMPT_COST_PER_1M = java.util.Collections.unmodifiableMap(prompt);
        COMPLETION_COST_PER_1M = java.util.Collections.unmodifiableMap(completion);
        TIER_MULTIPLIER = java.util.Collections.unmodifiableMap(tier);
    }

    private ProviderCostCatalog() {
    }

    public static double estimateCost(String provider, String model, int promptTokens, int completionTokens) {
        String promptKey = provider + ":" + model;
        String completionKey = provider + ":" + model;

        double promptCost = PROMPT_COST_PER_1M.getOrDefault(promptKey, 0.1) * promptTokens / 1_000_000.0;
        double completionCost = COMPLETION_COST_PER_1M.getOrDefault(completionKey, 0.3) * completionTokens / 1_000_000.0;

        return promptCost + completionCost;
    }

    public static double getTierMultiplier(String model) {
        if (model == null) return 1.0;
        return TIER_MULTIPLIER.getOrDefault(model.toLowerCase(), 1.0);
    }

    public static String getCheapestModel(String provider, boolean highQuality) {
        return switch (provider) {
            case "groq" -> highQuality ? "llama-3.3-70b-versatile" : "llama-3.1-8b-instant";
            case "gemini" -> highQuality ? "gemini-2.0-flash" : "gemini-1.5-flash";
            default -> "default";
        };
    }
}
