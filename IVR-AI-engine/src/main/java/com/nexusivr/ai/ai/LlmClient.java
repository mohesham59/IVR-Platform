package com.nexusivr.ai.ai;

/**
 * Provider-agnostic interface for Large Language Model integrations
 * (Gemini, Groq, Ollama).
 * Extends {@link AiProvider} for full backward compatibility with domain services.
 */
public interface LlmClient extends AiProvider {

    /**
     * Returns the human-readable identifier of this LLM provider (e.g. "gemini", "groq", "ollama").
     */
    String getProviderName();

    /**
     * Returns the configured model name currently being used by this provider.
     */
    String getModelName();

    /**
     * Checks if this LLM provider is available for live execution (e.g., non-mock, endpoint reachable).
     */
    boolean isAvailable();
}
