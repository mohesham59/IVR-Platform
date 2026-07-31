package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.AiAgentRegistry;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Controller servlet to fetch registered AI Agents dynamically.
 */
@WebServlet(name = "AiAgentServlet", urlPatterns = {"/api/v1/ai/agents"})
public class AiAgentServlet extends BaseAiServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, AiAgentRegistry.getAllAgents());
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
