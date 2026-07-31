package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.request.FlowGenerationRequest;
import com.nexusivr.ai.dto.request.FlowImprovementRequest;
import com.nexusivr.ai.dto.request.FlowValidationRequest;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.service.AiService;
import com.nexusivr.ai.service.FlowService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller servlet handling AI IVR flow generation, improvement, and validation endpoints.
 */
@WebServlet(urlPatterns = {
    "/api/v1/ai/flow/generate",
    "/api/v1/ai/flow/improve",
    "/api/v1/ai/flow/validate",
    "/api/v1/ai/flow/suggestions"
})
public class AiFlowServlet extends BaseAiServlet {

    private final FlowService flowService;
    private final AiService aiService;

    public AiFlowServlet(FlowService flowService, AiService aiService) {
        this.flowService = flowService;
        this.aiService = aiService;
    }

    public AiFlowServlet() {
        this(null, null);
    }

    private FlowService getFlowService(String provider) {
        return flowService != null ? flowService : ServiceRegistry.getFlowService(provider);
    }

    private AiService getAiService(String provider) {
        return aiService != null ? aiService : ServiceRegistry.getAiService(provider);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String provider = extractProvider(req);
            String path = req.getRequestURI();
            if (path.contains("/generate")) {
                handleGenerateFlow(req, resp, tenantId, provider);
            } else if (path.contains("/improve")) {
                handleImproveFlow(req, resp, provider);
            } else if (path.contains("/validate")) {
                handleValidateFlow(req, resp, provider);
            } else if (path.contains("/suggestions")) {
                handleGetSuggestions(req, resp, provider);
            } else {
                sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void handleGenerateFlow(HttpServletRequest req, HttpServletResponse resp, UUID tenantId, String provider) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: GENERATE_FLOW, FlowGenerator invoked: true");
        FlowGenerationRequest request = parseRequestBody(req, FlowGenerationRequest.class);
        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ValidationException("Business description prompt is required for flow generation");
        }

        String model = extractModel(req);
        double temp = extractTemperature(req);
        int timeout = extractTimeout(req);
        UUID sessionId = extractSessionId(req);

        Flow flow;
        if (flowService != null) {
            flow = flowService.generateAndSaveFlow(tenantId, "Generated IVR Flow", request.getDescription());
        } else {
            flow = (Flow) ServiceRegistry.getAiOperationRouter().route(
                    com.nexusivr.ai.service.AiOperation.GENERATE_FLOW, sessionId, tenantId, request.getDescription(), null,
                    null, provider, model, temp, timeout
            );
        }
        sendJsonResponse(resp, HttpServletResponse.SC_OK, flow);
    }

    private void handleImproveFlow(HttpServletRequest req, HttpServletResponse resp, String provider) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: FLOW_ANALYSIS, FlowGenerator invoked: false");
        
        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        String inputFlowJson = "";
        List<String> goals = new java.util.ArrayList<>();
        if (body != null && !body.isBlank()) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("existingFlow")) {
                    inputFlowJson = requestJson.get("existingFlow").toString();
                }
                if (requestJson.has("improvementGoals")) {
                    requestJson.getAsJsonArray("improvementGoals").forEach(item -> goals.add(item.getAsString()));
                }
            } catch (Exception e) {
                logger.error("[AiFlowServlet] Error parsing raw request JSON: {}", e.getMessage());
            }
        }
        String instructions = !goals.isEmpty() ? String.join(", ", goals) : "Optimize flow";

        String model = extractModel(req);
        double temp = extractTemperature(req);
        int timeout = extractTimeout(req);
        UUID sessionId = extractSessionId(req);
        UUID tenantId = extractTenantId(req);

        FlowImprovementResponse result;
        try {
            if (aiService != null) {
                result = aiService.improveFlow(inputFlowJson, instructions, provider, model, temp, timeout);
            } else {
                result = (FlowImprovementResponse) ServiceRegistry.getAiOperationRouter().route(
                        com.nexusivr.ai.service.AiOperation.IMPROVE_FLOW, sessionId, tenantId, instructions, inputFlowJson,
                        null, provider, model, temp, timeout
                );
            }
        } catch (RuntimeException e) {
            logger.error("[AiFlowServlet] Flow improvement failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "AI_PROVIDER_ERROR");
            error.put("message", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, error);
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        if (result.getImprovedFlowJson() != null && !result.getImprovedFlowJson().isEmpty()) {
            response.put("suggestedFlowJson", result.getImprovedFlowJson());
        } else {
            response.put("suggestedFlowJson", gson.toJson(result.getImprovedFlow()));
        }
        response.put("improvementSummary", result.getChangeLog().stream().collect(Collectors.joining("; ")));
        response.put("rationale", result.getRationale());
        response.put("changeLog", result.getChangeLog());
        response.put("containmentScoreEstimate", 0.85);
        response.put("selectedProvider", result.getSelectedProvider());
        response.put("actualProviderUsed", result.getActualProviderUsed());
        response.put("fallbackUsed", result.isFallbackUsed());
        response.put("fallbackReason", result.getFallbackReason());
        if (result.getQuotaWarnings() != null && !result.getQuotaWarnings().isEmpty()) {
            response.put("quotaWarnings", result.getQuotaWarnings());
        }
        if (result.getProviderAttempts() != null && !result.getProviderAttempts().isEmpty()) {
            response.put("providerAttempts", result.getProviderAttempts());
        }

        sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
    }

    private void handleValidateFlow(HttpServletRequest req, HttpServletResponse resp, String provider) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: FLOW_ANALYSIS, FlowGenerator invoked: false");
        
        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        String flowJson = "";
        if (body != null && !body.isBlank()) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("flow")) {
                    flowJson = requestJson.get("flow").toString();
                }
            } catch (Exception e) {
                logger.error("[AiFlowServlet] Error parsing raw validate request JSON: {}", e.getMessage());
            }
        }

        UUID tenantId = extractTenantId(req);
        UUID sessionId = extractSessionId(req);

        FlowValidationResponse result;
        if (aiService != null) {
            result = aiService.validateFlow(flowJson);
        } else {
            result = (FlowValidationResponse) ServiceRegistry.getAiOperationRouter().route(
                    com.nexusivr.ai.service.AiOperation.VALIDATE_FLOW, sessionId, tenantId, null, flowJson
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", result.isValid());
        response.put("score", result.getScore());
        response.put("issues", result.getIssues().stream().map(issue -> {
            Map<String, Object> issueMap = new LinkedHashMap<>();
            issueMap.put("severity", issue.getSeverity().name().toLowerCase());
            issueMap.put("message", issue.getMessage());
            if (issue.getNodeId() != null) issueMap.put("nodeId", issue.getNodeId());
            return issueMap;
        }).collect(Collectors.toList()));

        sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
    }

    private void handleGetSuggestions(HttpServletRequest req, HttpServletResponse resp, String provider) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: FLOW_SUGGESTIONS, LLM Agent invoked: true");

        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        String flowJson = "";
        List<com.nexusivr.ai.dto.common.ValidationIssueDto> issues = new java.util.ArrayList<>();
        if (body != null && !body.isBlank()) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("flow")) {
                    com.google.gson.JsonElement flowEl = requestJson.get("flow");
                    if (flowEl.isJsonPrimitive()) {
                        flowJson = flowEl.getAsString();
                    } else {
                        flowJson = flowEl.toString();
                    }
                }
                if (requestJson.has("issues") && requestJson.get("issues").isJsonArray()) {
                    com.google.gson.JsonArray arr = requestJson.getAsJsonArray("issues");
                    for (int i = 0; i < arr.size(); i++) {
                        issues.add(gson.fromJson(arr.get(i), com.nexusivr.ai.dto.common.ValidationIssueDto.class));
                    }
                }
            } catch (Exception e) {
                logger.error("[AiFlowServlet] Error parsing raw suggestions request JSON: {}", e.getMessage());
            }
        }

        String model = extractModel(req);
        double temp = extractTemperature(req);
        int timeout = extractTimeout(req);
        UUID sessionId = extractSessionId(req);
        UUID tenantId = extractTenantId(req);

        List<com.nexusivr.ai.dto.response.AiSuggestionDto> suggestions = ServiceRegistry.getUnifiedAiEngine().generateAiSuggestions(
                sessionId, tenantId, flowJson, issues, provider, model, temp, timeout
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("suggestions", suggestions);
        response.put("count", suggestions.size());

        sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
    }
}
