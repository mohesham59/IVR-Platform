package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.SystemHealthService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(urlPatterns = {
        "/api/v1/admin/system-health",
        "/api/v1/super-admin/system-health"
})
public class SystemHealthServlet extends BaseAiServlet {

    private final SystemHealthService healthService;

    public SystemHealthServlet(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    public SystemHealthServlet() {
        this(ServiceRegistry.getSystemHealthService());
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

            Map<String, Object> health = healthService.getSystemHealth();
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of(
                    "success", true,
                    "data", health
            ));
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
