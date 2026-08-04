package com.nexusivr.ai.ai;

import java.net.http.HttpClient;

/**
 * Concrete implementation of {@link AiProvider} for Groq Cloud API LLM.
 */
public class GroqAiProvider extends GroqClient {

    public GroqAiProvider() {
        super();
    }

    public GroqAiProvider(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        super(apiKey, baseUrl, model, timeoutSeconds);
    }

    public GroqAiProvider(String apiKey, String baseUrl, String model, int timeoutSeconds, HttpClient httpClient) {
        super(apiKey, baseUrl, model, timeoutSeconds, httpClient);
    }
}
