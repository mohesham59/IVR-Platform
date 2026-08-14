package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.CallLog;
import com.nexusivr.ai.service.DashboardService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/dashboard/*",
        "/api/v1/calls/*"
})
public class DashboardServlet extends BaseAiServlet {

    private final DashboardService dashboardService;

    public DashboardServlet(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public DashboardServlet() {
        this(ServiceRegistry.getDashboardService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            if (path.contains("/calls/active-count")) {
                int activeCalls = dashboardService.getActiveCallsCount(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("activeCalls", activeCalls);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/stats")) {
                Map<String, Object> stats = dashboardService.getDashboardStats(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", stats);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/call-volume")) {
                List<Map<String, Object>> list = dashboardService.getCallVolume(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", list);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/call-distribution")) {
                List<Map<String, Object>> list = dashboardService.getCallDistribution(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", list);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/agent-performance")) {
                List<Map<String, Object>> list = dashboardService.getAgentPerformance(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", list);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/queue-performance")) {
                List<Map<String, Object>> list = dashboardService.getQueuePerformance(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", list);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/dashboard/recent-calls/export")) {
                String csv = dashboardService.exportRecentCallsCsv(tenantId);
                resp.setContentType("text/csv");
                resp.setHeader("Content-Disposition", "attachment; filename=\"recent_calls.csv\"");
                try (PrintWriter writer = resp.getWriter()) {
                    writer.write(csv);
                }
                return;
            }

            if (path.contains("/dashboard/recent-calls")) {
                String limitStr = req.getParameter("limit");
                int limit = limitStr != null ? Integer.parseInt(limitStr) : 7;
                List<CallLog> calls = dashboardService.getRecentCalls(tenantId, limit);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", calls);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown dashboard endpoint");
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
