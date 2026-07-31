package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.request.SentimentAnalysisRequest;
import com.nexusivr.ai.dto.response.SentimentAnalysisResponse;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.service.AiService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller servlet handling sentiment analysis requests.
 * Endpoint: POST /api/v1/ai/sentiment
 */
@WebServlet(urlPatterns = {"/api/v1/ai/sentiment"})
public class AiSentimentServlet extends BaseAiServlet {

    private final AiService aiService;

    public AiSentimentServlet(AiService aiService) {
        this.aiService = aiService;
    }

    public AiSentimentServlet() {
        this(null);
    }

    private AiService getAiService() {
        return aiService != null ? aiService : ServiceRegistry.getAiService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            SentimentAnalysisRequest request = parseRequestBody(req, SentimentAnalysisRequest.class);
            String textToAnalyze = request != null ? request.getText() : null;
            if (textToAnalyze == null || textToAnalyze.isBlank()) {
                throw new ValidationException("Text or content parameter is required for sentiment analysis");
            }

            SentimentAnalysisResponse result = getAiService().analyzeSentiment(textToAnalyze);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sentiment", result.getOverall().getLabel().name().toLowerCase());
            response.put("score", result.getOverall().getScore());
            response.put("confidence", result.getOverall().getConfidence());
            response.put("escalationRisk", deriveEscalationRisk(result.getOverall().getScore()));

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private String deriveEscalationRisk(double score) {
        if (score <= -0.6) return "high";
        if (score <= -0.2) return "medium";
        return "low";
    }
}
