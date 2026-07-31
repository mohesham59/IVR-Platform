package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.ConversationResponse;
import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.service.ChatService;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.model.SessionStatus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller servlet handling conversation history transcripts, active session listings,
 * session creation, title updates, and deletion.
 * Endpoints:
 * - GET /api/v1/ai/history
 * - GET /api/v1/ai/history?sessionId=...
 * - GET /api/v1/ai/history/{id}
 * - GET /api/v1/ai/conversation/{sessionId}
 * - POST /api/v1/ai/new-chat
 * - DELETE /api/v1/ai/history/{id}
 * - DELETE /api/v1/ai/conversation/{id}
 * - PATCH /api/v1/ai/history/{id}
 * - PATCH /api/v1/ai/conversation/{id}
 */
@WebServlet(urlPatterns = {
    "/api/v1/ai/conversation/*",
    "/api/v1/ai/history",
    "/api/v1/ai/history/*",
    "/api/v1/ai/new-chat"
})
public class AiConversationHistoryServlet extends BaseAiServlet {

    private final ChatService chatService;

    public AiConversationHistoryServlet(ChatService chatService) {
        this.chatService = chatService;
    }

    public AiConversationHistoryServlet() {
        this(null);
    }

    private ChatService getChatService() {
        return chatService != null ? chatService : ServiceRegistry.getChatService();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID tenantId = extractTenantId(req);
        String path = req.getRequestURI() != null ? req.getRequestURI() : "";
        String sessionIdParam = req.getParameter("sessionId");

        logger.info("[AiConversationHistoryServlet] GET Request -> URI: {}, QuerySessionId: {}, Tenant: {}", path, sessionIdParam, tenantId);

        try {
            String sessionIdStr = extractSessionIdFromPathOrParam(path, sessionIdParam);
            if (sessionIdStr != null && !sessionIdStr.isBlank()) {
                UUID sessionId = parseUUID(sessionIdStr);
                if (sessionId == null) {
                    logger.warn("Invalid UUID format for sessionId '{}', returning empty history.", sessionIdStr);
                    sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", List.of()));
                    return;
                }

                ConversationResponse response = getChatService().getConversationHistory(sessionId, tenantId);
                List<Map<String, Object>> messageList = response.getMessages().stream().map(msg -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", msg.getId() != null ? msg.getId().toString() : null);
                    map.put("role", msg.getRole().name().toLowerCase());
                    map.put("content", msg.getContent());
                    map.put("timestamp", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
                    map.put("metadata", msg.getMetadata() != null ? msg.getMetadata().toString() : null);
                    return map;
                }).toList();
                logger.info("Fetched history for session {}: {} messages returned.", sessionId, messageList.size());
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", messageList));
                return;
            }

            List<AiSession> allSessions = getChatService().getAllSessions(tenantId);
            List<Map<String, Object>> sessionList = allSessions.stream().map(session -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("sessionId", session.getId() != null ? session.getId().toString() : null);
                map.put("title", session.getCustomerIdentifier() != null ? session.getCustomerIdentifier() : "New Chat");
                map.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
                map.put("messageCount", 0);
                return map;
            }).toList();
            logger.info("Fetched all sessions for tenant {}: {} sessions returned.", tenantId, sessionList.size());
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", sessionList));

        } catch (Exception e) {
            logger.error("Unexpected error in GET history/conversation, returning empty response", e);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", List.of()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID tenantId = extractTenantId(req);
        logger.info("[AiConversationHistoryServlet] POST Request -> Tenant: {}", tenantId);

        try {
            Map<?, ?> body = parseRequestBody(req, Map.class);
            
            // Check if this is a save conversation request
            if (body != null && body.containsKey("messages") && body.get("sessionId") != null) {
                String sessionIdStr = body.get("sessionId").toString();
                UUID sessionId = UUID.fromString(sessionIdStr.trim());
                
                // 1. Ensure the session exists in the database
                AiSessionDao sessionDao = new AiSessionDao();
                String bodyTitle = body.get("title") != null ? body.get("title").toString().trim() : null;
                if (sessionDao.findById(sessionId, tenantId).isEmpty()) {
                    AiSession session = new AiSession();
                    session.setId(sessionId);
                    session.setTenantId(tenantId);
                    session.setChannel(Channel.CHAT);
                    session.setCustomerIdentifier(bodyTitle != null && !bodyTitle.isBlank() ? bodyTitle : "New IVR Flow Session");
                    session.setStatus(SessionStatus.ACTIVE);
                    session.setCreatedAt(java.time.Instant.now());
                    session.setUpdatedAt(java.time.Instant.now());
                    sessionDao.create(session);
                } else if (bodyTitle != null && !bodyTitle.isBlank() && !"New Chat".equalsIgnoreCase(bodyTitle) && !"New IVR Flow Session".equalsIgnoreCase(bodyTitle)) {
                    sessionDao.updateTitle(sessionId, tenantId, bodyTitle);
                }
                
                // Fix 10a: serialize delete+insert for the same session to prevent the race condition
                // where two concurrent POST requests both compute turn_number=1 and the second
                // fails with PSQLException: duplicate key value violates unique constraint "uq_ai_messages_session_turn".
                // String.intern() ensures threads sharing the same session ID value share the same monitor.
                synchronized (sessionId.toString().intern()) {
                    // 2. Clear old messages for this session
                    MessageDao messageDao = new MessageDao();
                    messageDao.deleteBySessionId(sessionId, tenantId);

                    // 3. Save new messages list
                    List<?> messagesList = (List<?>) body.get("messages");
                    int turn = 1;
                    for (Object msgObj : messagesList) {
                        Map<?, ?> msgMap = (Map<?, ?>) msgObj;
                        Message message = new Message();
                        message.setId(UUID.randomUUID());
                        message.setSessionId(sessionId);
                        message.setTenantId(tenantId);
                        message.setTurnNumber(turn++);

                        String roleStr = msgMap.get("role") != null ? msgMap.get("role").toString() : "user";
                        MessageRole role = "ai".equalsIgnoreCase(roleStr) || "assistant".equalsIgnoreCase(roleStr)
                                ? MessageRole.ASSISTANT
                                : MessageRole.USER;
                        message.setRole(role);

                        String content = msgMap.get("text") != null ? msgMap.get("text").toString() : "";
                        if (content.isBlank() && msgMap.get("content") != null) {
                            content = msgMap.get("content").toString();
                        }
                        message.setContent(content);
                        message.setModelUsed("nexusivr-ai-v1");

                        JsonObject metaJson = new JsonObject();
                        if (msgMap.get("flowId") != null) {
                            metaJson.addProperty("flowId", msgMap.get("flowId").toString());
                        }
                        if (msgMap.get("snapshotId") != null) {
                            metaJson.addProperty("snapshotId", msgMap.get("snapshotId").toString());
                        }
                        if (msgMap.get("version") != null) {
                            try {
                                double verVal = Double.parseDouble(msgMap.get("version").toString());
                                metaJson.addProperty("version", (int) verVal);
                            } catch (Exception ignored) {}
                        }

                        Object extraObj = msgMap.get("extra");
                        if (extraObj != null) {
                            try {
                                JsonObject extraJson = JsonParser.parseString(gson.toJson(extraObj)).getAsJsonObject();
                                extraJson.entrySet().forEach(entry -> metaJson.add(entry.getKey(), entry.getValue()));
                            } catch (Exception ignored) {}
                        }

                        message.setMetadata(metaJson.toString());

                        messageDao.save(message);
                    }
                } // end synchronized

                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "sessionId", sessionIdStr));
                return;
            }

            String title = body != null && body.get("title") != null ? body.get("title").toString() : "New Chat";
            if (body != null && body.get("customerIdentifier") != null) {
                title = body.get("customerIdentifier").toString();
            }

            AiSession session = getChatService().startSession(tenantId, Channel.CHAT, title);
            logger.info("New chat session created successfully: id={}, tenant={}", session.getId(), tenantId);

            Map<String, Object> responseData = Map.of(
                "sessionId", session.getId().toString(),
                "tenantId", tenantId.toString(),
                "status", session.getStatus().name(),
                "customerIdentifier", title,
                "createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : java.time.Instant.now().toString()
            );
            sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);
        } catch (Exception e) {
            logger.error("Error creating or saving chat session", e);
            UUID newId = UUID.randomUUID();
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("sessionId", newId.toString(), "tenantId", tenantId.toString(), "status", "ACTIVE"));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID tenantId = extractTenantId(req);
        String path = req.getRequestURI() != null ? req.getRequestURI() : "";
        String sessionIdParam = req.getParameter("sessionId");

        String sessionIdStr = extractSessionIdFromPathOrParam(path, sessionIdParam);
        logger.info("[AiConversationHistoryServlet] DELETE Request -> URI: {}, SessionStr: {}, Tenant: {}", path, sessionIdStr, tenantId);

        if (sessionIdStr == null || sessionIdStr.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "message", "sessionId required"));
            return;
        }

        UUID sessionId = parseUUID(sessionIdStr);
        if (sessionId == null) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "message", "invalid sessionId format"));
            return;
        }

        try {
            com.nexusivr.ai.service.ChatService chatService = getChatService();
            if (chatService == null) {
                logger.warn("ChatService unavailable, cannot delete session {}", sessionId);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "sessionId", sessionId.toString(), "message", "Chat service unavailable"));
                return;
            }
            boolean deleted = chatService.deleteSession(sessionId, tenantId);
            logger.info("Deleted session {}: {}", sessionId, deleted);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "sessionId", sessionId.toString()));
        } catch (Exception e) {
            logger.error("Error deleting session {}", sessionId, e);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "sessionId", sessionId.toString()));
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID tenantId = extractTenantId(req);
        String path = req.getRequestURI() != null ? req.getRequestURI() : "";
        String sessionIdParam = req.getParameter("sessionId");

        String sessionIdStr = extractSessionIdFromPathOrParam(path, sessionIdParam);
        logger.info("[AiConversationHistoryServlet] PATCH Request -> URI: {}, SessionStr: {}, Tenant: {}", path, sessionIdStr, tenantId);

        if (sessionIdStr == null || sessionIdStr.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "message", "sessionId required"));
            return;
        }

        UUID sessionId = parseUUID(sessionIdStr);
        if (sessionId == null) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "message", "invalid sessionId format"));
            return;
        }

        try {
            Map<?, ?> body = parseRequestBody(req, Map.class);
            String title = body != null && body.get("title") != null ? body.get("title").toString() : null;
            if (title == null && body != null && body.get("customerIdentifier") != null) {
                title = body.get("customerIdentifier").toString();
            }

            if (title == null || title.isBlank()) {
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "message", "title is required"));
                return;
            }

            boolean updated = getChatService().updateSessionTitle(sessionId, tenantId, title);
            logger.info("Updated title for session {} to '{}': {}", sessionId, title, updated);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", updated, "sessionId", sessionId.toString(), "title", title));
        } catch (Exception e) {
            logger.error("Error updating session title {}", sessionId, e);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", false, "sessionId", sessionId.toString()));
        }
    }

    private String extractSessionIdFromPathOrParam(String path, String param) {
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        if (path.contains("/conversation/")) {
            return path.substring(path.indexOf("/conversation/") + "/conversation/".length()).trim();
        }
        if (path.contains("/history/")) {
            return path.substring(path.indexOf("/history/") + "/history/".length()).trim();
        }
        return null;
    }

    private UUID parseUUID(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
