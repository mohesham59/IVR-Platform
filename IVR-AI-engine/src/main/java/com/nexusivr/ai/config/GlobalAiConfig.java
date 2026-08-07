package com.nexusivr.ai.config;

import com.nexusivr.ai.ai.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centrally managed global configuration for the Unified AI Engine.
 * Supports thread-safe request-level overrides via ThreadLocal to prevent race conditions.
 */
public class GlobalAiConfig {

    private static final Logger logger = LoggerFactory.getLogger(GlobalAiConfig.class);

    private static final GlobalAiConfig instance = new GlobalAiConfig();

    // ThreadLocal to hold request-specific overrides safely
    private static final ThreadLocal<RequestOverrides> requestOverrides = new ThreadLocal<>();

    private volatile String defaultProvider;
    private volatile String defaultModel;
    private volatile double temperature;
    private volatile int maxTokens;
    private volatile boolean streamingEnabled;
    private volatile boolean structuredOutputEnabled;
    private volatile int retryCount;
    private volatile int timeout;
    private volatile String systemPrompt;

    private GlobalAiConfig() {
        resetToDefaults();
    }

    public static GlobalAiConfig getInstance() {
        return instance;
    }

    /**
     * Resets settings to defaults loaded from LlmConfig (.env).
     */
    public synchronized void resetToDefaults() {
        this.defaultProvider = LlmConfig.getProvider();

        // Resolve the correct default model for the configured provider.
        // Previously this always fell through to getGroqModel() for non-ollama
        // providers, which caused Gemini requests to be sent with a Groq model name.
        if ("ollama".equalsIgnoreCase(this.defaultProvider)) {
            this.defaultModel = LlmConfig.getOllamaModel();
            this.timeout = LlmConfig.getOllamaTimeout();
        } else if ("gemini".equalsIgnoreCase(this.defaultProvider)) {
            this.defaultModel = LlmConfig.getGeminiModel();
            this.timeout = LlmConfig.getGeminiTimeout();
        } else if ("groq".equalsIgnoreCase(this.defaultProvider)) {
            this.defaultModel = LlmConfig.getGroqModel();
            this.timeout = LlmConfig.getGroqTimeout();
        } else if ("openrouter".equalsIgnoreCase(this.defaultProvider)) {
            this.defaultModel = LlmConfig.getOpenrouterModel();
            this.timeout = LlmConfig.getOpenrouterTimeout();
        } else {
            this.defaultModel = LlmConfig.getGroqModel();
            this.timeout = LlmConfig.getGroqTimeout();
        }

        this.temperature = 0.7;
        this.maxTokens = 2048;
        this.streamingEnabled = false;
        this.structuredOutputEnabled = true;
        this.retryCount = 3;
        this.systemPrompt = PromptBuilder.DEFAULT_SYSTEM_INSTRUCTION;

        logger.info("GlobalAiConfig: Reset to defaults. Provider: {}, Model: {}, Temperature: {}, Timeout: {}s",
                defaultProvider, defaultModel, temperature, timeout);
    }

    // Request overrides management
    public static void setOverrides(String provider, String model, double temperature, int timeout) {
        requestOverrides.set(new RequestOverrides(provider, model, temperature, timeout, null));
    }

    public static void setOverrides(String provider, String model, double temperature, int timeout, String systemPrompt) {
        requestOverrides.set(new RequestOverrides(provider, model, temperature, timeout, systemPrompt));
    }

    public static void clearOverrides() {
        requestOverrides.remove();
    }

    public static RequestOverrides getOverrides() {
        return requestOverrides.get();
    }

    // Thread-safe getters resolving overrides first, falling back to global defaults
    public String getProvider() {
        RequestOverrides overrides = requestOverrides.get();
        if (overrides != null && overrides.provider != null && !overrides.provider.isBlank()) {
            return overrides.provider;
        }
        return defaultProvider;
    }

    public String getModel() {
        RequestOverrides overrides = requestOverrides.get();
        if (overrides != null && overrides.model != null && !overrides.model.isBlank()) {
            return overrides.model;
        }
        return defaultModel;
    }

    public double getTemperature() {
        RequestOverrides overrides = requestOverrides.get();
        if (overrides != null && overrides.temperature >= 0) {
            return overrides.temperature;
        }
        return temperature;
    }

    public int getTimeout() {
        RequestOverrides overrides = requestOverrides.get();
        if (overrides != null && overrides.timeout > 0) {
            return overrides.timeout;
        }
        return timeout;
    }

    // Static variables configuration
    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean isStreamingEnabled() { return streamingEnabled; }
    public void setStreamingEnabled(boolean streamingEnabled) { this.streamingEnabled = streamingEnabled; }

    public boolean isStructuredOutputEnabled() { return structuredOutputEnabled; }
    public void setStructuredOutputEnabled(boolean structuredOutputEnabled) { this.structuredOutputEnabled = structuredOutputEnabled; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public void setTimeout(int timeout) { this.timeout = timeout; }

    public String getSystemPrompt() {
        RequestOverrides overrides = requestOverrides.get();
        if (overrides != null && overrides.systemPrompt != null && !overrides.systemPrompt.isBlank()) {
            return overrides.systemPrompt;
        }
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public static class RequestOverrides {
        public final String provider;
        public final String model;
        public final double temperature;
        public final int timeout;
        public final String systemPrompt;

        public RequestOverrides(String provider, String model, double temperature, int timeout) {
            this(provider, model, temperature, timeout, null);
        }

        public RequestOverrides(String provider, String model, double temperature, int timeout, String systemPrompt) {
            this.provider = provider;
            this.model = model;
            this.temperature = temperature;
            this.timeout = timeout;
            this.systemPrompt = systemPrompt;
        }
    }
}
