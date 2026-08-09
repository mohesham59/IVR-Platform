package com.nexusivr.ai.controller;

import com.nexusivr.ai.dao.NotificationDao;
import com.nexusivr.ai.dao.UserDao;
import com.nexusivr.ai.model.Notification;
import com.nexusivr.ai.model.User;
import com.nexusivr.ai.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
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

    private final NotificationDao notificationDao = new NotificationDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Claims claims = validateAuth(req, resp);
            if (claims == null) return;

            String userIdStr = claims.getSubject();
            String activeTenantIdStr = (String) claims.get("activeTenantId");

            UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;

            // Prefer the fresh active_tenant_id from the DB (in case user re-assigned a tenant
            // without re-logging in and the JWT claim is stale).
            if (userIdStr != null) {
                User user = userDao.findById(userIdStr);
                if (user != null && user.getActiveTenantId() != null && !user.getActiveTenantId().isBlank()) {
                    activeTenantIdStr = user.getActiveTenantId();
                }
            }

            UUID activeTenantId = (activeTenantIdStr != null && !activeTenantIdStr.isBlank())
                    ? UUID.fromString(activeTenantIdStr)
                    : null;

            logger.info("GET /api/v1/notifications: userId={}, activeTenantId={}", userId, activeTenantId);

            List<Notification> notifications = notificationDao.getNotificationsForUser(activeTenantId, userId);
            
            // Format response list
            List<Map<String, Object>> responseList = new ArrayList<>();
            for (Notification n : notifications) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", n.getId().toString());
                map.put("tenantId", n.getTenantId() != null ? n.getTenantId().toString() : null);
                map.put("userId", n.getUserId() != null ? n.getUserId().toString() : null);
                map.put("message", n.getMessage());
                map.put("linkUrl", n.getLinkUrl());
                map.put("isRead", n.isRead());
                map.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
                map.put("type", n.getType());
                responseList.add(map);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("notifications", responseList);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Claims claims = validateAuth(req, resp);
            if (claims == null) return;

            String pathInfo = req.getPathInfo(); // e.g. "/{id}/read"
            if (pathInfo != null) {
                String[] parts = pathInfo.split("/");
                // Expecting parts to be: ["", "{id}", "read"]
                if (parts.length >= 3 && "read".equalsIgnoreCase(parts[2])) {
                    String idStr = parts[1];
                    UUID id = UUID.fromString(idStr);
                    boolean marked = notificationDao.markAsRead(id);
                    
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", marked);
                    result.put("message", marked ? "Notification marked as read" : "Failed to mark notification as read");
                    sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                    return;
                }
            }
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid action path");
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private Claims validateAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Missing or invalid authorization header");
            sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
            return null;
        }
        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Invalid or expired token");
            sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
            return null;
        }
        return claims;
    }
}
