package com.nexusivr.ai.controller;

import com.nexusivr.ai.ai.AiProviderFactory;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.config.LlmConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller servlet for querying and updating active LLM Provider (Ollama vs Groq).
 * Endpoint: /api/v1/ai/provider
 */
@WebServlet(urlPatterns = {"/api/v1/ai/provider"})
public class AiProviderServlet extends BaseAiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String currentProvider = extractProvider(req);
        LlmClient client = AiProviderFactory.getProvider(currentProvider);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider", client.getProviderName());
        response.put("model", client.getModelName());
        response.put("available", client.isAvailable());

        sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Map<?, ?> body = parseRequestBody(req, Map.class);
            String provider = body != null && body.get("provider") != null ? body.get("provider").toString() : null;

            if (provider == null || provider.isBlank()) {
                provider = extractProvider(req);
            }

            LlmClient client = AiProviderFactory.getProvider(provider);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("provider", client.getProviderName());
            response.put("model", client.getModelName());
            response.put("available", client.isAvailable());

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
