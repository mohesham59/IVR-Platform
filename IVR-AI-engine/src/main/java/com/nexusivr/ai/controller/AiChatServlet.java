package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.ChatRequest;
import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.service.ChatService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller servlet handling AI chat turns and session initialization.
 * Endpoint: POST /api/v1/ai/chat
 */
@WebServlet(urlPatterns = {"/api/v1/ai/chat"})
public class AiChatServlet extends BaseAiServlet {

    private final ChatService chatService;

    public AiChatServlet(ChatService chatService) {
        this.chatService = chatService;
    }

    public AiChatServlet() {
        this(null);
    }

    private ChatService getChatService() {
        return chatService != null ? chatService : ServiceRegistry.getChatService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long startTime = System.currentTimeMillis();
        try {
            UUID tenantId = extractTenantId(req);
            ChatRequest request = parseRequestBody(req, ChatRequest.class);

            if (request == null) {
                throw new ValidationException("Request body cannot be null or empty");
            }

            String messageText = request.getUserMessage();
            if (messageText == null || messageText.isBlank()) {
                throw new ValidationException("User message is required");
            }

            UUID sessionId = request.getSessionId();
            String provider = extractProvider(req);
            ChatService service = chatService != null ? chatService : ServiceRegistry.getChatService(provider);

            if (sessionId == null) {
                // Auto-create a session if not supplied
                AiSession session = service.startSession(tenantId, Channel.CHAT, "web-user");
                sessionId = session.getId();
            }

            UUID selectedSnapshotId = extractSnapshotId(req);
            ChatResponse response = service.sendMessage(sessionId, tenantId, messageText, request.getFlowContext(), selectedSnapshotId);
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.info("[AiChatServlet] Chat Turn Complete -> Session: {}, Latency: {}ms", sessionId, latencyMs);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
