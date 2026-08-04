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
    "/api/v1/ai/flow/generate-stream",
    "/api/v1/ai/flow/improve",
    "/api/v1/ai/flow/validate",
    "/api/v1/ai/flow/publish",
    "/api/v1/ai/flow/save",
    "/api/v1/ai/flow/import-vxml"
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
            if (path.contains("/generate-stream")) {
                handleGenerateFlowStream(req, resp, tenantId, provider);
            } else if (path.contains("/generate")) {
                handleGenerateFlow(req, resp, tenantId, provider);
            } else if (path.contains("/improve")) {
                handleImproveFlow(req, resp, provider);
            } else if (path.contains("/validate")) {
                handleValidateFlow(req, resp, provider);
            } else if (path.contains("/publish")) {
                handlePublishFlow(req, resp);
            } else if (path.contains("/save")) {
                handleSaveDraft(req, resp);
            } else if (path.contains("/import-vxml")) {
                handleImportVxml(req, resp);
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

    private void handleGenerateFlowStream(HttpServletRequest req, HttpServletResponse resp, UUID tenantId, String provider) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: GENERATE_FLOW (SSE Stream)");

        FlowGenerationRequest request = parseRequestBody(req, FlowGenerationRequest.class);
        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ValidationException("Business description prompt is required for flow generation");
        }

        String model = extractModel(req);
        double temp = extractTemperature(req);
        int timeout = extractTimeout(req);
        UUID sessionId = extractSessionId(req);

        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Accel-Buffering", "no");

        java.io.PrintWriter writer = resp.getWriter();

        com.nexusivr.ai.service.ProgressListener listener = (stage, message) -> {
            if (writer.checkError()) {
                logger.info("[AiFlowServlet] Client disconnected / aborted SSE stream for session {}", sessionId);
                return;
            }
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("stage", stage);
            json.addProperty("message", message);
            writer.write("event: progress\ndata: " + json.toString() + "\n\n");
            writer.flush();
        };

        try {
            Flow flow = ServiceRegistry.getUnifiedAiEngine().generateFlow(
                    tenantId, sessionId, request.getDescription(), provider, model, temp, timeout, null, listener
            );

            if (writer.checkError()) {
                logger.info("[AiFlowServlet] Client disconnected before stream completion for session {}", sessionId);
                return;
            }

            com.google.gson.JsonObject completeJson = gson.toJsonTree(flow).getAsJsonObject();
            completeJson.addProperty("stage", "rendering");
            writer.write("event: complete\ndata: " + completeJson.toString() + "\n\n");
            writer.flush();
        } catch (Exception e) {
            if (writer.checkError()) {
                logger.info("[AiFlowServlet] Client disconnected during error handling for session {}", sessionId);
                return;
            }
            logger.error("[AiFlowServlet] Stream error during flow generation", e);
            com.google.gson.JsonObject errorJson = new com.google.gson.JsonObject();
            errorJson.addProperty("message", e.getMessage() != null ? e.getMessage() : "Flow generation failed");
            writer.write("event: error\ndata: " + errorJson.toString() + "\n\n");
            writer.flush();
        }

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

    private void handlePublishFlow(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: FLOW_PUBLISH");

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
        String flowId = null;
        String flowName = null;
        String extension = null;

        if (body != null && !body.isBlank()) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("flow")) {
                    com.google.gson.JsonElement flowEl = requestJson.get("flow");
                    flowJson = flowEl.isJsonPrimitive() ? flowEl.getAsString() : flowEl.toString();
                } else if (requestJson.has("flowJson")) {
                    flowJson = requestJson.get("flowJson").getAsString();
                } else if (requestJson.has("nodes")) {
                    flowJson = body;
                }
                if (requestJson.has("flowId")) flowId = requestJson.get("flowId").getAsString();
                if (requestJson.has("flowName")) flowName = requestJson.get("flowName").getAsString();
                if (requestJson.has("extension")) extension = requestJson.get("extension").getAsString();
            } catch (Exception e) {
                logger.error("[AiFlowServlet] Error parsing publish request JSON: {}", e.getMessage());
            }
        }

        UUID tenantId = extractTenantId(req);

        try {
            com.nexusivr.ai.service.FlowPublishService.FlowPublishResult result = ServiceRegistry.getFlowPublishService()
                    .publishFlow(tenantId != null ? tenantId.toString() : null, flowId, extension, flowName, flowJson);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", result.isExtensionRegistered() ? "published" : "partially_published");
            response.put("publishedAt", java.time.Instant.now().toString());
            response.put("filename", result.getFilename());
            response.put("filePath", result.getFilePath());
            response.put("validationScore", result.getValidationScore());
            response.put("extensionRegistered", result.isExtensionRegistered());
            response.put("extensionMessage", result.getExtensionMessage());
            if (result.getWarning() != null) {
                response.put("warning", result.getWarning());
            }

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (ValidationException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "VALIDATION_FAILED");
            error.put("message", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, error);
        } catch (com.nexusivr.ai.exception.ServiceException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "FILE_WRITE_FAILED");
            error.put("message", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
        }
    }

    private void handleSaveDraft(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: FLOW_SAVE_DRAFT");

        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        String flowJson = "";
        String flowId = null;
        String flowName = null;

        if (body != null && !body.isBlank()) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("flowJson")) {
                    flowJson = requestJson.get("flowJson").getAsString();
                } else if (requestJson.has("flow")) {
                    com.google.gson.JsonElement flowEl = requestJson.get("flow");
                    flowJson = flowEl.isJsonPrimitive() ? flowEl.getAsString() : flowEl.toString();
                } else if (requestJson.has("nodes")) {
                    flowJson = body;
                }
                if (requestJson.has("flowId")) flowId = requestJson.get("flowId").getAsString();
                if (requestJson.has("flowName")) flowName = requestJson.get("flowName").getAsString();
            } catch (Exception e) {
                logger.error("[AiFlowServlet] Error parsing save-draft request JSON: {}", e.getMessage());
            }
        }

        if (flowJson == null || flowJson.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "MISSING_FLOW");
            error.put("message", "flowJson is required");
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        UUID tenantId = extractTenantId(req);
        String tId = tenantId != null ? tenantId.toString() : null;

        try {
            String draftPath = ServiceRegistry.getFlowDraftService()
                    .saveDraft(tId, flowId, flowName, flowJson);

            // Verify the file was actually written by checking its existence and size on disk.
            java.nio.file.Path filePath = java.nio.file.Paths.get(draftPath);
            if (!java.nio.file.Files.exists(filePath) || java.nio.file.Files.size(filePath) == 0) {
                throw new com.nexusivr.ai.exception.ServiceException(
                        "Draft file was not found or empty after write — disk write may have silently failed: " + draftPath, null);
            }

            // Persist draft to database if tenantId and flowId are present
            if (tenantId != null && flowId != null && !flowId.isBlank()) {
                try {
                    UUID fUuid = null;
                    try {
                        fUuid = UUID.fromString(flowId);
                    } catch (IllegalArgumentException ignored) {}

                    if (fUuid != null) {
                        com.nexusivr.ai.model.Flow flow = new com.nexusivr.ai.model.Flow();
                        flow.setId(fUuid);
                        flow.setTenantId(tenantId);
                        flow.setName(flowName != null && !flowName.isBlank() ? flowName : "IVR Flow Draft");
                        flow.setFlowJson(flowJson);
                        flow.setStatus("DRAFT");

                        try {
                            boolean updated = ServiceRegistry.getFlowDao().update(fUuid, tenantId, flow);
                            if (!updated) {
                                ServiceRegistry.getFlowDao().create(flow);
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    logger.warn("[AiFlowServlet] DB sync notice during save draft: {}", e.getMessage());
                }
            }

            String filename = filePath.getFileName().toString();
            // Extract version number from filename pattern: <baseName>_draft_vN.vxml
            int version = 1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("_draft_v(\\d+)\\.vxml$")
                    .matcher(filename);
            if (m.find()) {
                version = Integer.parseInt(m.group(1));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "saved");
            response.put("savedAt", java.time.Instant.now().toString());
            response.put("draftPath", draftPath);
            response.put("filename", filename);
            response.put("version", version);
            logger.info("[AiFlowServlet] Draft saved and verified on disk: {} (v{})", draftPath, version);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);

        } catch (com.nexusivr.ai.exception.ValidationException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "VALIDATION_FAILED");
            error.put("message", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, error);
        } catch (com.nexusivr.ai.exception.ServiceException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "DRAFT_WRITE_FAILED");
            error.put("message", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
        }
    }

    /**
     * Converts a raw VoiceXML (VXML) file to the React Flow Builder JSON format (nodes + edges).
     * <p>
     * Accepts: {@code { "vxml": "<?xml version...>...</vxml>" }} or raw VXML as plain text body.
     * Returns: {@code { "nodes": [...], "edges": [...], "flowName": "..." }}
     */
    private void handleImportVxml(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.info("[AiFlowServlet] Intent detected: IMPORT_VXML");

        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        String body = sb.toString().trim();

        // Support both JSON wrapper { "vxml": "..." } and raw VXML text
        String vxmlContent = body;
        if (body.startsWith("{")) {
            try {
                com.google.gson.JsonObject requestJson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (requestJson.has("vxml")) {
                    vxmlContent = requestJson.get("vxml").getAsString();
                }
            } catch (Exception e) {
                // body was not valid JSON — treat the whole body as raw VXML
                logger.debug("[AiFlowServlet] IMPORT_VXML body is not JSON wrapper; treating as raw VXML");
            }
        }

        if (vxmlContent == null || vxmlContent.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "MISSING_VXML");
            error.put("message", "Request body must contain VoiceXML content (raw or in a JSON field named \"vxml\")");
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        // Convert VXML → FlowModel → React Flow JSON
        com.nexusivr.ai.model.flow.FlowModel model;
        try {
            model = com.nexusivr.ai.service.FlowContextService.convertVxmlToModel(vxmlContent);
        } catch (Exception e) {
            logger.warn("[AiFlowServlet] IMPORT_VXML conversion failed: {}", e.getMessage());
            model = null;
        }

        if (model == null || model.getNodes().isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "VXML_PARSE_FAILED");
            error.put("message", "Could not parse the provided VoiceXML. Ensure the file is a valid VoiceXML 2.1 document exported from this application.");
            sendJsonResponse(resp, 422, error);
            return;
        }

        String flowJson = new com.nexusivr.ai.service.ModelToFlowRenderer().render(model);

        // Parse the rendered JSON and return its nodes/edges alongside the flow name
        try {
            com.google.gson.JsonObject rendered = com.google.gson.JsonParser.parseString(flowJson).getAsJsonObject();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ok");
            response.put("flowName", model.getName() != null ? model.getName() : "Imported IVR Flow");
            response.put("nodes", rendered.has("nodes") ? rendered.get("nodes") : new com.google.gson.JsonArray());
            response.put("edges", rendered.has("edges") ? rendered.get("edges") : new com.google.gson.JsonArray());
            logger.info("[AiFlowServlet] IMPORT_VXML success — {} node(s), {} edge(s)",
                    model.getNodes().size(), model.getConnections().size());
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            logger.error("[AiFlowServlet] IMPORT_VXML JSON serialization failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorCode", "SERIALIZATION_FAILED");
            error.put("message", "Internal error while preparing import response: " + e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
        }
    }
}
