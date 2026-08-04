package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.GlobalAiConfig;

/**
 * Factory creating and caching {@link AiProvider} instances.
 * Delegates to the unified {@link ProviderManager} for client resolution.
 */
public class AiProviderFactory {

    private static final ProviderManager manager = new ProviderManager();

    public static LlmClient getProvider() {
        return getProvider(GlobalAiConfig.getInstance().getProvider());
    }

    public static LlmClient getProvider(String providerName) {
        String model = GlobalAiConfig.getInstance().getModel();
        double temp = GlobalAiConfig.getInstance().getTemperature();
        int timeout = GlobalAiConfig.getInstance().getTimeout();
        return manager.getLlmClient(providerName, model, temp, timeout);
    }

    public static LlmClient createProvider(String providerName) {
        return getProvider(providerName);
    }

    public static void setOverrideClient(LlmClient client) {
        manager.setOverrideClient(client);
    }

    public static void clearCache() {
        manager.clearCache();
    }
}
