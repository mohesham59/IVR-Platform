package com.nexusivr.ai.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.AgentStateRecord;
import com.nexusivr.ai.model.Queue;
import com.nexusivr.ai.model.QueueMember;
import com.nexusivr.ai.service.QueueService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/queues",
        "/api/v1/queues/*",
        "/api/v1/agents",
        "/api/v1/agents/*"
})
public class TenantQueueServlet extends BaseAiServlet {

    private final QueueService queueService;

    public TenantQueueServlet(QueueService queueService) {
        this.queueService = queueService;
    }

    public TenantQueueServlet() {
        this(ServiceRegistry.getQueueService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            if (path.contains("/agents")) {
                List<AgentStateRecord> agents = queueService.getTenantAgents(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", agents);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            // Path: /api/v1/queues or /api/v1/queues/{id}
            String queueIdStr = extractIdFromPath(path, "/queues/");

            if (queueIdStr != null && !queueIdStr.isBlank()) {
                UUID queueId = UUID.fromString(queueIdStr);
                Map<String, Object> detail = queueService.getQueueDetail(tenantId, queueId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", detail);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            List<Queue> queues = queueService.getQueues(tenantId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("data", queues);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            String body = readBody(req);
            JsonObject json = (body != null && !body.isBlank()) ? JsonParser.parseString(body).getAsJsonObject() : new JsonObject();

            if (path.contains("/members")) {
                // Endpoint: POST /api/v1/queues/{id}/members
                String queueIdStr = extractIdFromPath(path, "/queues/");
                if (queueIdStr == null) throw new ValidationException("Queue ID is required");

                UUID queueId = UUID.fromString(queueIdStr);
                String agentIdStr = json.has("agentId") ? json.get("agentId").getAsString() : null;
                int penalty = json.has("penalty") ? json.get("penalty").getAsInt() : 0;

                if (agentIdStr == null || agentIdStr.isBlank()) throw new ValidationException("agentId parameter is required");

                QueueMember member = queueService.addQueueMember(tenantId, queueId, UUID.fromString(agentIdStr), penalty);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "Member added to queue successfully");
                result.put("data", member);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            // Endpoint: POST /api/v1/queues (Create Queue)
            Queue q = new Queue();
            if (json.has("name")) q.setName(json.get("name").getAsString());
            if (json.has("strategy")) q.setStrategy(json.get("strategy").getAsString());
            if (json.has("wrapUpTimeSeconds")) q.setWrapUpTimeSeconds(json.get("wrapUpTimeSeconds").getAsInt());
            if (json.has("maxWaitSeconds")) q.setMaxWaitSeconds(json.get("maxWaitSeconds").getAsInt());
            if (json.has("musicOnHold")) q.setMusicOnHold(json.get("musicOnHold").getAsString());
            if (json.has("overflowAction")) q.setOverflowAction(json.get("overflowAction").getAsString());

            Queue created = queueService.createQueue(tenantId, q);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Queue created and provisioned in Asterisk successfully");
            result.put("data", created);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            String queueIdStr = extractIdFromPath(path, "/queues/");
            if (queueIdStr == null) throw new ValidationException("Queue ID is required");

            UUID queueId = UUID.fromString(queueIdStr);
            String body = readBody(req);
            JsonObject json = (body != null && !body.isBlank()) ? JsonParser.parseString(body).getAsJsonObject() : new JsonObject();

            Queue q = new Queue();
            if (json.has("name")) q.setName(json.get("name").getAsString());
            if (json.has("strategy")) q.setStrategy(json.get("strategy").getAsString());
            if (json.has("wrapUpTimeSeconds")) q.setWrapUpTimeSeconds(json.get("wrapUpTimeSeconds").getAsInt());
            if (json.has("maxWaitSeconds")) q.setMaxWaitSeconds(json.get("maxWaitSeconds").getAsInt());
            if (json.has("musicOnHold")) q.setMusicOnHold(json.get("musicOnHold").getAsString());
            if (json.has("overflowAction")) q.setOverflowAction(json.get("overflowAction").getAsString());
            if (json.has("status")) q.setStatus(json.get("status").getAsString());

            Queue updated = queueService.updateQueue(tenantId, queueId, q);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Queue updated successfully");
            result.put("data", updated);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            if (path.contains("/members/")) {
                // Expected: /api/v1/queues/{queueId}/members/{agentId}
                String[] parts = path.substring(path.indexOf("/queues/") + "/queues/".length()).split("/members/");
                if (parts.length < 2) throw new ValidationException("Invalid queue member URL path");

                UUID queueId = UUID.fromString(parts[0]);
                UUID agentId = UUID.fromString(parts[1]);

                boolean removed = queueService.removeQueueMember(tenantId, queueId, agentId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", removed);
                result.put("message", removed ? "Member removed from queue" : "Member not found");
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            // Endpoint: DELETE /api/v1/queues/{id}
            String queueIdStr = extractIdFromPath(path, "/queues/");
            if (queueIdStr == null) throw new ValidationException("Queue ID is required");

            UUID queueId = UUID.fromString(queueIdStr);
            boolean deleted = queueService.deleteQueue(tenantId, queueId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", deleted);
            result.put("message", deleted ? "Queue deleted successfully" : "Queue not found");
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws jakarta.servlet.ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handlePatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    private void handlePatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            // Expected: PATCH /api/v1/agents/{agentId}/state
            String agentIdStr = extractIdFromPath(path, "/agents/");
            if (agentIdStr != null && agentIdStr.contains("/state")) {
                agentIdStr = agentIdStr.replace("/state", "");
            }
            if (agentIdStr == null || agentIdStr.isBlank()) throw new ValidationException("Agent ID is required");

            UUID agentId = UUID.fromString(agentIdStr);
            String body = readBody(req);
            JsonObject json = (body != null && !body.isBlank()) ? JsonParser.parseString(body).getAsJsonObject() : new JsonObject();

            String newState = json.has("state") ? json.get("state").getAsString() : (json.has("currentState") ? json.get("currentState").getAsString() : "available");

            AgentStateRecord updated = queueService.updateAgentState(tenantId, agentId, newState);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Agent state updated and AMI QueuePause synchronized");
            result.put("data", updated);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String extractIdFromPath(String path, String prefix) {
        int idx = path.indexOf(prefix);
        if (idx != -1) {
            String sub = path.substring(idx + prefix.length());
            String[] parts = sub.split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0];
            }
        }
        return null;
    }
}
