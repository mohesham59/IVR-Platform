package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.SuperAdminDashboardService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/admin/platform-stats",
        "/api/v1/admin/company-growth",
        "/api/v1/admin/ai-requests-today",
        "/api/v1/admin/calls-per-day",
        "/api/v1/admin/latest-companies",
        "/api/v1/admin/recent-activity",
        "/api/v1/super-admin/dashboard/*"
})
public class SuperAdminDashboardServlet extends BaseAiServlet {

    private final SuperAdminDashboardService service;

    public SuperAdminDashboardServlet(SuperAdminDashboardService service) {
        this.service = service;
    }

    public SuperAdminDashboardServlet() {
        this(ServiceRegistry.getSuperAdminDashboardService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // RBAC Access Control Check
            if (!isSuperAdmin(req)) {
                sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, Map.of(
                        "success", false,
                        "error", "Forbidden: Super Admin access required"
                ));
                return;
            }

            String path = req.getRequestURI();

            if (path.contains("/platform-stats") || path.contains("/dashboard/stats")) {
                Map<String, Object> stats = service.getPlatformStats();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", stats));
                return;
            }

            if (path.contains("/company-growth") || path.contains("/dashboard/company-growth")) {
                List<Map<String, Object>> growth = service.getMonthlyCompanyGrowth();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", growth));
                return;
            }

            if (path.contains("/ai-requests-today") || path.contains("/dashboard/ai-requests-today")) {
                List<Map<String, Object>> aiChart = service.getAiRequestsTodayChart();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", aiChart));
                return;
            }

            if (path.contains("/calls-per-day") || path.contains("/dashboard/calls-per-day")) {
                List<Map<String, Object>> callsChart = service.getCallsPerDayChart();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", callsChart));
                return;
            }

            if (path.contains("/latest-companies") || path.contains("/dashboard/latest-companies")) {
                List<Map<String, Object>> companies = service.getLatestCompanies();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", companies));
                return;
            }

            if (path.contains("/recent-activity") || path.contains("/dashboard/recent-activity")) {
                List<Map<String, Object>> activity = service.getRecentActivityFeed();
                List<Map<String, Object>> latestUsers = service.getLatestUsers();
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of(
                        "success", true,
                        "data", activity,
                        "users", latestUsers
                ));
                return;
            }

            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown Super Admin endpoint");
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private boolean isSuperAdmin(HttpServletRequest req) {
        String roleHeader = req.getHeader("X-User-Role");
        if (roleHeader != null && roleHeader.equalsIgnoreCase("tenant_admin")) {
            return false;
        }
        String superAdminHeader = req.getHeader("X-Is-SuperAdmin");
        if (superAdminHeader != null && superAdminHeader.equalsIgnoreCase("false")) {
            return false;
        }
        return true;
    }
}
