package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.LlmConfig;
import java.net.http.HttpClient;

/**
 * Concrete implementation of {@link AiProvider} for Ollama Local LLM.
 */
public class OllamaAiProvider extends OllamaClient {

    public OllamaAiProvider() {
        super();
    }

    public OllamaAiProvider(String baseUrl, String model, int timeoutSeconds) {
        super(baseUrl, model, timeoutSeconds);
    }

    public OllamaAiProvider(String baseUrl, String model, int timeoutSeconds, HttpClient httpClient) {
        super(baseUrl, model, timeoutSeconds, httpClient);
    }
}
