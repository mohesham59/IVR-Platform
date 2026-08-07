package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.nexusivr.ai.dto.common.ErrorResponse;
import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.exception.ResourceNotFoundException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.service.exception.ProviderException;
import com.nexusivr.ai.ai.ProviderManager;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base HttpServlet providing common utility methods for JSON serialization,
 * tenant ID extraction, and centralized REST error handling.
 */
public abstract class BaseAiServlet extends HttpServlet {

    protected static final Logger logger = LoggerFactory.getLogger(BaseAiServlet.class);
    protected static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>) (json, typeOfT, context) ->
                    Instant.parse(json.getAsString()))
            .setPrettyPrinting()
            .create();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws jakarta.servlet.ServletException, IOException {
        String provider = extractProvider(req);
        String model = extractModel(req);
        double temp = extractTemperature(req);
        int timeout = extractTimeout(req);

        String agentHeader = req.getHeader("X-AI-Agent");
        String systemPrompt = null;
        if (agentHeader != null && !agentHeader.isBlank()) {
            com.nexusivr.ai.model.AiAgent agent = com.nexusivr.ai.service.AiAgentRegistry.getAgent(agentHeader);
            if (agent != null) {
                systemPrompt = agent.getSystemPrompt();
            }
        }

        UUID sessionId = extractSessionId(req);

        com.nexusivr.ai.config.GlobalAiConfig.setOverrides(provider, model, temp, timeout, systemPrompt);
        if (sessionId != null) {
            com.nexusivr.ai.service.GenerationCancellationRegistry.clear(sessionId);
            com.nexusivr.ai.service.GenerationCancellationRegistry.setCurrentSessionId(sessionId);
        }
        try {
            super.service(req, resp);
        } finally {
            com.nexusivr.ai.config.GlobalAiConfig.clearOverrides();
            com.nexusivr.ai.service.GenerationCancellationRegistry.clearCurrentSessionId();
        }
    }

    protected UUID extractTenantId(HttpServletRequest req) {
        String tenantHeader = req.getHeader("X-Tenant-ID");
        if (tenantHeader == null || tenantHeader.isBlank()) {
            tenantHeader = req.getHeader("Tenant-ID");
        }
        if (tenantHeader == null || tenantHeader.isBlank()) {
            tenantHeader = req.getParameter("tenantId");
        }
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                return UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid Tenant ID header format '{}', falling back to default tenant ID", tenantHeader);
            }
        }
        return DEFAULT_TENANT_ID;
    }

    protected String extractProvider(HttpServletRequest req) {
        String providerHeader = req.getHeader("X-AI-Provider");
        if (providerHeader != null && !providerHeader.isBlank()) {
            return providerHeader.trim();
        }
        String param = req.getParameter("provider");
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return com.nexusivr.ai.config.GlobalAiConfig.getInstance().getDefaultProvider();
    }

    protected String extractModel(HttpServletRequest req) {
        String provider = extractProvider(req);
        String modelHeader = req.getHeader("X-AI-Model");
        if (modelHeader != null && !modelHeader.isBlank()) {
            return modelHeader.trim();
        }

        if (provider.equalsIgnoreCase(com.nexusivr.ai.config.GlobalAiConfig.getInstance().getDefaultProvider())) {
            return com.nexusivr.ai.config.GlobalAiConfig.getInstance().getDefaultModel();
        }

        if ("gemini".equalsIgnoreCase(provider)) return com.nexusivr.ai.config.LlmConfig.getGeminiModel();
        if ("groq".equalsIgnoreCase(provider)) return com.nexusivr.ai.config.LlmConfig.getGroqModel();
        if ("ollama".equalsIgnoreCase(provider)) return com.nexusivr.ai.config.LlmConfig.getOllamaModel();
        if ("openrouter".equalsIgnoreCase(provider)) return com.nexusivr.ai.config.LlmConfig.getOpenrouterModel();
        // openai and claude providers are no longer supported

        return com.nexusivr.ai.config.GlobalAiConfig.getInstance().getDefaultModel();
    }

    protected double extractTemperature(HttpServletRequest req) {
        String tempHeader = req.getHeader("X-AI-Temperature");
        if (tempHeader != null && !tempHeader.isBlank()) {
            try {
                return Double.parseDouble(tempHeader.trim());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1.0;
    }

    protected int extractTimeout(HttpServletRequest req) {
        String timeoutHeader = req.getHeader("X-AI-Timeout");
        if (timeoutHeader != null && !timeoutHeader.isBlank()) {
            try {
                return Integer.parseInt(timeoutHeader.trim());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1;
    }

    protected UUID extractSessionId(HttpServletRequest req) {
        String sessionHeader = req.getHeader("X-Session-ID");
        if (sessionHeader != null && !sessionHeader.isBlank()) {
            try {
                return UUID.fromString(sessionHeader.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid Session ID header format '{}'", sessionHeader);
            }
        }
        return null;
    }

    protected UUID extractSnapshotId(HttpServletRequest req) {
        String snapshotHeader = req.getHeader("X-AI-Snapshot-ID");
        if (snapshotHeader != null && !snapshotHeader.isBlank()) {
            try {
                return UUID.fromString(snapshotHeader.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid Snapshot ID header format '{}'", snapshotHeader);
            }
        }
        return null;
    }

    protected <T> T parseRequestBody(HttpServletRequest req, Class<T> clazz) throws IOException {
        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();
        if (body.isBlank()) {
            return null;
        }
        return gson.fromJson(body, clazz);
    }

    protected void sendJsonResponse(HttpServletResponse resp, int statusCode, Object data) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String json = data instanceof String ? (String) data : gson.toJson(data);
        resp.getWriter().write(json);
    }

    protected void handleError(HttpServletResponse resp, Exception e) throws IOException {
        Instant now = Instant.now();
        if (e instanceof ValidationException) {
            logger.warn("Validation error processing API request: {}", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    new ErrorResponse("VALIDATION_ERROR", e.getMessage(), Map.of(), now));
        } else if (e instanceof com.nexusivr.ai.service.exception.GenerationCancelledException) {
            logger.info("Generation request cancelled: {}", e.getMessage());
            sendJsonResponse(resp, 499,
                    new ErrorResponse("GENERATION_CANCELLED", e.getMessage(), Map.of(), now));
        } else if (e instanceof ResourceNotFoundException) {
            logger.warn("Resource not found processing API request: {}", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND,
                    new ErrorResponse("NOT_FOUND", e.getMessage(), Map.of(), now));
        } else if (e instanceof DataAccessException) {
            logger.warn("Database unavailable: {}", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_OK,
                    new ErrorResponse("SERVICE_UNAVAILABLE", "Database is currently unavailable. Operating in transient mode.", Map.of(), now));
        } else if (e instanceof ProviderException providerException) {
            ProviderException.FailureReason reason = providerException.getReason();
            int statusCode;
            String errorCode;
            String failureReason;
            Map<String, String> providerStatuses = new HashMap<>();
            int retryAfterSeconds = 0;

            if (reason == ProviderException.FailureReason.RATE_LIMITED) {
                statusCode = 429;
                errorCode = "RATE_LIMITED";
                failureReason = "RATE_LIMITED";
            } else if (reason == ProviderException.FailureReason.QUOTA_EXCEEDED) {
                statusCode = 429;
                errorCode = "QUOTA_EXCEEDED";
                failureReason = "QUOTA_EXCEEDED";
            } else if (reason == ProviderException.FailureReason.AUTH_FAILURE) {
                statusCode = 401;
                errorCode = "PROVIDER_AUTH_FAILURE";
                failureReason = "AUTH_FAILURE";
                providerStatuses.put(providerException.getProviderName(), "Authentication failed");
            } else if (reason == ProviderException.FailureReason.PROVIDER_UNAVAILABLE) {
                statusCode = 503;
                errorCode = "PROVIDER_UNAVAILABLE";
                failureReason = "PROVIDER_UNAVAILABLE";
                providerStatuses.put(providerException.getProviderName(), providerException.getMessage());
            } else if (reason == ProviderException.FailureReason.TIMEOUT) {
                statusCode = 504;
                errorCode = "PROVIDER_TIMEOUT";
                failureReason = "TIMEOUT";
            } else {
                statusCode = 502;
                errorCode = "PROVIDER_ERROR";
                failureReason = "PROVIDER_ERROR";
            }

            String failedProvider = providerException.getProviderName();
            long providerCooldownMs = ServiceRegistry.getProviderManager().getCooldownRemainingMs(failedProvider);
            int maxCooldownSecs = ServiceRegistry.getProviderManager().getMaximumCooldownSeconds();

            if (providerCooldownMs > 0) {
                retryAfterSeconds = (int) Math.max(1, (providerCooldownMs + 999) / 1000);
            } else if (maxCooldownSecs > 0) {
                retryAfterSeconds = maxCooldownSecs;
            } else {
                retryAfterSeconds = switch (reason) {
                    case RATE_LIMITED, QUOTA_EXCEEDED -> 300;
                    case AUTH_FAILURE -> 600;
                    case PROVIDER_UNAVAILABLE -> 120;
                    case TIMEOUT -> 60;
                    default -> 30;
                };
            }

            logger.warn("Provider error processing API request: {} - {}. retryAfterSeconds={}", errorCode, e.getMessage(), retryAfterSeconds);
            ErrorResponse errorResponse = new ErrorResponse(
                    errorCode,
                    e.getMessage() != null ? e.getMessage() : "AI provider failed.",
                    Map.of("provider", providerException.getProviderName(),
                           "statusCode", providerException.getStatusCode()),
                    now,
                    failureReason
            );
            errorResponse.setRetryAfterSeconds(retryAfterSeconds);
            errorResponse.setProviderStatuses(providerStatuses.isEmpty() ? providerException.getProviderStatuses() : providerStatuses);
            com.nexusivr.ai.config.GlobalAiConfig.RequestOverrides overrides = com.nexusivr.ai.config.GlobalAiConfig.getOverrides();
            String requestedProvider = overrides != null && overrides.provider != null && !overrides.provider.isBlank()
                    ? overrides.provider : providerException.getProviderName();
            if (requestedProvider == null || requestedProvider.isBlank() || "all".equalsIgnoreCase(requestedProvider)) {
                requestedProvider = com.nexusivr.ai.config.LlmConfig.getProvider();
            }
            errorResponse.setSelectedProvider(requestedProvider);
            errorResponse.setActualProviderUsed(providerException.getActualProviderUsed() != null ? providerException.getActualProviderUsed() : "none");
            errorResponse.setFallbackUsed(providerException.isFallbackUsed());
            errorResponse.setProvidersAttempted(providerException.getProvidersAttempted());
            errorResponse.setFallbackReason(null);
            sendJsonResponse(resp, statusCode, errorResponse);
        } else {
            logger.error("Internal error processing API request: {}", e.getMessage(), e);
            sendJsonResponse(resp, HttpServletResponse.SC_OK,
                    new ErrorResponse("SERVER_ERROR", e.getMessage() != null ? e.getMessage() : "An unexpected server error occurred.", Map.of(), now));
        }
    }
}
