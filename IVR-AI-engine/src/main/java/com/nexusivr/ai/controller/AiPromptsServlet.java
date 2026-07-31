package com.nexusivr.ai.controller;

import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.service.AiService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller servlet retrieving prompt templates and quick AI prompts.
 * Endpoint: GET /api/v1/ai/prompts
 */
@WebServlet(urlPatterns = {"/api/v1/ai/prompts"})
public class AiPromptsServlet extends BaseAiServlet {

    private final AiService aiService;

    public AiPromptsServlet(AiService aiService) {
        this.aiService = aiService;
    }

    public AiPromptsServlet() {
        this(null);
    }

    private AiService getAiService() {
        return aiService != null ? aiService : ServiceRegistry.getAiService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            PromptBuilder pb = getAiService().getPromptBuilder();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("systemInstruction", pb.getSystemInstruction());
            data.put("flowPromptTemplate", pb.getFlowPromptTemplate());
            data.put("summarizationPromptTemplate", pb.getSummarizationPromptTemplate());

            List<String> quickPrompts = List.of(
                    "Build an IVR for a Hospital",
                    "Generate a Bank IVR",
                    "Restaurant Reservation Flow",
                    "Insurance Support Flow",
                    "Hotel Concierge IVR"
            );
            data.put("quickPrompts", quickPrompts);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, data);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
