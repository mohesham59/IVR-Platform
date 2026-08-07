package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.ai.dao.TenantDao;
import com.nexusivr.ai.dao.UserDao;
import com.nexusivr.ai.model.User;
import com.nexusivr.ai.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {
        "/api/v1/tenant/companies"
})
public class TenantCompaniesServlet extends BaseAiServlet {

    private final TenantDao tenantDao = new TenantDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Missing or invalid authorization header");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);
            if (claims == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Invalid or expired token");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }

            String userId = claims.getSubject();
            User currentUser = userDao.findById(userId);
            if (currentUser == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "User not found");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }

            List<TenantDao.Tenant> tenants = tenantDao.findTenantsByUserId(currentUser.getId());

            String activeTenantId = currentUser.getActiveTenantId();

            List<Map<String, Object>> responseList = new ArrayList<>();
            for (TenantDao.Tenant t : tenants) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", t.getId());
                map.put("displayName", t.getDisplayName());
                map.put("ownerUserId", t.getOwnerUserId());
                map.put("ownerUsername", t.getOwnerUsername() != null ? t.getOwnerUsername() : "—");
                map.put("ownerEmail", t.getOwnerEmail() != null ? t.getOwnerEmail() : "—");
                map.put("status", t.getStatus());
                map.put("isActive", t.getId() != null && t.getId().equalsIgnoreCase(activeTenantId));
                map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
                map.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "");
                responseList.add(map);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("activeTenantId", activeTenantId != null ? activeTenantId : "");
            result.put("tenants", responseList);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Missing or invalid authorization header");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);
            if (claims == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Invalid or expired token");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }

            String userId = claims.getSubject();
            User currentUser = userDao.findById(userId);
            if (currentUser == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "User not found");
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, err);
                return;
            }

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()))) {
                body = reader.lines().collect(Collectors.joining("\n"));
            }

            JsonObject json = new Gson().fromJson(body, JsonObject.class);
            if (json == null || !json.has("tenantId")) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Missing tenantId in request body");
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, err);
                return;
            }

            String targetTenantId = json.get("tenantId").getAsString();

            boolean success = tenantDao.updateActiveTenant(currentUser.getId(), targetTenantId);
            if (success) {
                // Update session user object
                currentUser.setActiveTenantId(targetTenantId);
                req.getSession().setAttribute("user", currentUser);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("activeTenantId", targetTenantId);
                result.put("message", "Active tenant updated successfully");
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
            } else {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "Failed to update active tenant in database");
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, err);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
