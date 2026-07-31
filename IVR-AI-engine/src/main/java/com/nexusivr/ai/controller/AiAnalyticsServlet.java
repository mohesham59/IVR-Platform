package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.AnalyticsResponse;
import com.nexusivr.ai.service.AnalyticsService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller servlet handling AI Engine analytics and usage metrics reporting.
 * Endpoint: GET /api/v1/ai/analytics
 */
@WebServlet(urlPatterns = {"/api/v1/ai/analytics"})
public class AiAnalyticsServlet extends BaseAiServlet {

    private final AnalyticsService analyticsService;

    public AiAnalyticsServlet(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public AiAnalyticsServlet() {
        this(null);
    }

    private AnalyticsService getAnalyticsService() {
        return analyticsService != null ? analyticsService : ServiceRegistry.getAnalyticsService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            AnalyticsResponse analytics = getAnalyticsService().getTenantAnalytics(tenantId);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, analytics);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
