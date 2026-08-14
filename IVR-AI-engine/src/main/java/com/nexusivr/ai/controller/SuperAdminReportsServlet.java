package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.ReportsService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/admin/reports/*",
        "/api/v1/super-admin/reports/*"
})
public class SuperAdminReportsServlet extends BaseAiServlet {

    private final ReportsService reportsService;

    public SuperAdminReportsServlet(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    public SuperAdminReportsServlet() {
        this(ServiceRegistry.getReportsService());
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

            String path = req.getRequestURI();
            String dateFromStr = req.getParameter("dateFrom");
            String dateToStr = req.getParameter("dateTo");
            String tenantIdStr = req.getParameter("tenantId");

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

            if (path.contains("/telephony/export")) {
                String csv = reportsService.exportTelephonyReportCsv(dateFrom, dateTo, tenantId);
                sendCsvResponse(resp, "telephony_usage_report.csv", csv);
                return;
            }

            if (path.contains("/billing/export")) {
                String csv = reportsService.exportBillingReportCsv(dateFrom, dateTo, tenantId);
                sendCsvResponse(resp, "billing_token_report.csv", csv);
                return;
            }

            if (path.contains("/telephony")) {
                List<Map<String, Object>> rows = reportsService.getTenantTelephonyReport(dateFrom, dateTo, tenantId);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", rows));
                return;
            }

            if (path.contains("/billing")) {
                List<Map<String, Object>> rows = reportsService.getTenantBillingReport(dateFrom, dateTo, tenantId);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "data", rows));
                return;
            }

            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown Reports endpoint");
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void sendCsvResponse(HttpServletResponse resp, String filename, String csvContent) throws IOException {
        resp.setContentType("text/csv");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.getWriter().write(csvContent);
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
