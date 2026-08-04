package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.GlobalAiConfig;

/**
 * Factory creating and caching {@link LlmClient} instances based on configuration or runtime parameter.
 * Delegates to the unified {@link ProviderManager} for client resolution.
 */
public class LlmProviderFactory {

    private static final ProviderManager manager = new ProviderManager();

    public static LlmClient getLlmClient() {
        return getLlmClient(GlobalAiConfig.getInstance().getProvider());
    }

    public static LlmClient getLlmClient(String providerName) {
        String model = GlobalAiConfig.getInstance().getModel();
        double temp = GlobalAiConfig.getInstance().getTemperature();
        int timeout = GlobalAiConfig.getInstance().getTimeout();
        return manager.getLlmClient(providerName, model, temp, timeout);
    }

    public static LlmClient createProvider(String providerName) {
        return getLlmClient(providerName);
    }

    public static void setOverrideClient(LlmClient client) {
        manager.setOverrideClient(client);
    }

    public static void clearCache() {
        manager.clearCache();
    }
}
