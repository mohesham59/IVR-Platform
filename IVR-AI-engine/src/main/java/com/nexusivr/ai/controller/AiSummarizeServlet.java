package com.nexusivr.ai.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.service.AiService;
import com.nexusivr.ai.service.ChatService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller servlet handling conversation transcript summarization requests.
 * Endpoint: POST /api/v1/ai/summarize
 */
@WebServlet(urlPatterns = {"/api/v1/ai/summarize"})
public class AiSummarizeServlet extends BaseAiServlet {

    private final AiService aiService;
    private final ChatService chatService;

    public AiSummarizeServlet(AiService aiService, ChatService chatService) {
        this.aiService = aiService;
        this.chatService = chatService;
    }

    public AiSummarizeServlet() {
        this(null, null);
    }

    private AiService getAiService() {
        return aiService != null ? aiService : ServiceRegistry.getAiService();
    }

    private ChatService getChatService() {
        return chatService != null ? chatService : ServiceRegistry.getChatService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String body = sb.toString();

            List<Message> messages = null;
            if (!body.isBlank()) {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (json.has("messages") && json.get("messages").isJsonArray()) {
                    messages = parseMessagesArray(json.getAsJsonArray("messages"));
                } else if (json.has("sessionId") && !json.get("sessionId").isJsonNull()) {
                    UUID sessionId = UUID.fromString(json.get("sessionId").getAsString());
                    messages = getChatService().getConversationHistory(sessionId, tenantId).getMessages();
                }
            }

            String summaryText = getAiService().summarizeConversation(messages != null ? messages : List.of());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("summary", summaryText);
            response.put("keyPoints", List.of());
            response.put("sentimentLabel", "neutral");
            response.put("sourceMessageCount", messages != null ? messages.size() : 0);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private List<Message> parseMessagesArray(JsonArray array) {
        List<Message> messages = new ArrayList<>();
        for (JsonElement el : array) {
            if (el.isJsonObject()) {
                JsonObject msgObj = el.getAsJsonObject();
                Message msg = new Message();
                if (msgObj.has("role")) {
                    String role = msgObj.get("role").getAsString().toUpperCase();
                    try {
                        msg.setRole(MessageRole.valueOf(role));
                    } catch (IllegalArgumentException e) {
                        msg.setRole(MessageRole.USER);
                    }
                }
                if (msgObj.has("text")) {
                    msg.setContent(msgObj.get("text").getAsString());
                } else if (msgObj.has("content")) {
                    msg.setContent(msgObj.get("content").getAsString());
                }
                messages.add(msg);
            }
        }
        return messages;
    }
}
