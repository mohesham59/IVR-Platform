package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.Notification;
import com.nexusivr.ai.service.NotificationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/notifications",
        "/api/v1/notifications/*"
})
public class NotificationServlet extends BaseAiServlet {

    private final NotificationService notificationService;

    public NotificationServlet(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public NotificationServlet() {
        this(ServiceRegistry.getNotificationService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            boolean isSuperAdmin = isSuperAdmin(req);
            UUID tenantId = isSuperAdmin ? null : extractTenantId(req);

            String unreadOnlyStr = req.getParameter("unreadOnly");
            boolean unreadOnly = "true".equalsIgnoreCase(unreadOnlyStr);

            String limitStr = req.getParameter("limit");
            int limit = limitStr != null ? Integer.parseInt(limitStr) : 20;

            List<Notification> list = notificationService.getNotifications(tenantId, isSuperAdmin, unreadOnly, limit);

            int unreadCount = (int) list.stream().filter(n -> !n.isRead()).count();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("unreadCount", unreadCount);
            result.put("data", list);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            boolean isSuperAdmin = isSuperAdmin(req);
            UUID tenantId = isSuperAdmin ? null : extractTenantId(req);
            String path = req.getRequestURI();

            if (path.contains("/read-all")) {
                boolean updated = notificationService.markAllAsRead(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", updated);
                result.put("message", "All notifications marked as read");
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.contains("/read")) {
                String idStr = extractIdFromPath(path, "/notifications/");
                if (idStr != null && idStr.contains("/read")) {
                    idStr = idStr.replace("/read", "");
                }
                if (idStr != null && !idStr.isBlank()) {
                    UUID id = UUID.fromString(idStr);
                    boolean updated = notificationService.markAsRead(tenantId, id);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", updated);
                    result.put("message", "Notification marked as read");
                    sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                    return;
                }
            }

            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid notification POST action");
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
        return superAdminHeader != null && superAdminHeader.equalsIgnoreCase("true");
    }

    private String extractIdFromPath(String path, String prefix) {
        int idx = path.indexOf(prefix);
        if (idx != -1) {
            String sub = path.substring(idx + prefix.length());
            String[] parts = sub.split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0];
            }
        }
        return null;
    }
}
