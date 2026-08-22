package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.AuditLogService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@WebServlet(urlPatterns = {
        "/api/v1/admin/audit-logs",
        "/api/v1/super-admin/audit-logs"
})
public class AuditLogsServlet extends BaseAiServlet {

    private final AuditLogService service;

    public AuditLogsServlet(AuditLogService service) {
        this.service = service;
    }

    public AuditLogsServlet() {
        this(ServiceRegistry.getAuditLogService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // RBAC Security Check
            if (!isSuperAdmin(req)) {
                sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, Map.of(
                        "success", false,
                        "error", "Forbidden: Super Admin access required"
                ));
                return;
            }

            String tenantIdStr = req.getParameter("tenantId");
            String actionType = req.getParameter("actionType");
            String dateFromStr = req.getParameter("dateFrom");
            String dateToStr = req.getParameter("dateTo");
            String pageStr = req.getParameter("page");
            String pageSizeStr = req.getParameter("pageSize");

            UUID tenantId = null;
            if (tenantIdStr != null && !tenantIdStr.isBlank() && !"ALL".equalsIgnoreCase(tenantIdStr)) {
                try { tenantId = UUID.fromString(tenantIdStr); } catch (Exception ignored) {}
            }

            Timestamp dateFrom = null;
            if (dateFromStr != null && !dateFromStr.isBlank()) {
                try { dateFrom = Timestamp.valueOf(dateFromStr.contains(" ") ? dateFromStr : dateFromStr + " 00:00:00"); } catch (Exception ignored) {}
            }

            Timestamp dateTo = null;
            if (dateToStr != null && !dateToStr.isBlank()) {
                try { dateTo = Timestamp.valueOf(dateToStr.contains(" ") ? dateToStr : dateToStr + " 23:59:59"); } catch (Exception ignored) {}
            }

            int page = 1;
            if (pageStr != null && !pageStr.isBlank()) {
                try { page = Integer.parseInt(pageStr); } catch (Exception ignored) {}
            }

            int pageSize = 10;
            if (pageSizeStr != null && !pageSizeStr.isBlank()) {
                try { pageSize = Integer.parseInt(pageSizeStr); } catch (Exception ignored) {}
            }

            Map<String, Object> data = service.getPaginatedAuditLogs(tenantId, actionType, dateFrom, dateTo, page, pageSize);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of(
                    "success", true,
                    "data", data
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
