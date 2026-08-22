package com.nexusivr.ai.controller;

import com.nexusivr.ai.dao.CallAnalyticsDao;
import com.nexusivr.ai.model.CallAnalyticsRecord;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = {"/api/v1/ai/analytics/calls"})
public class CallAnalyticsServlet extends BaseAiServlet {
    private final CallAnalyticsDao callAnalyticsDao;

    public CallAnalyticsServlet() {
        this(ServiceRegistry.getCallAnalyticsDao());
    }

    public CallAnalyticsServlet(CallAnalyticsDao callAnalyticsDao) {
        this.callAnalyticsDao = callAnalyticsDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // We can optionally filter by tenantId if added to the DAO
            List<CallAnalyticsRecord> calls = callAnalyticsDao.getAllCalls();
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("data", calls);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
