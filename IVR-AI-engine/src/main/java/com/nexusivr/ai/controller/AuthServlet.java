package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.ai.dao.UserDao;
import com.nexusivr.ai.model.User;
import com.nexusivr.ai.security.JwtUtil;
import com.nexusivr.ai.security.PasswordUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {
        "/api/v1/auth/login",
        "/api/v1/auth/me",
        "/api/v1/auth/logout"
})
public class AuthServlet extends BaseAiServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        if ("/api/v1/auth/login".equals(path)) {
            handleLogin(req, resp);
        } else if ("/api/v1/auth/logout".equals(path)) {
            handleLogout(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        if ("/api/v1/auth/me".equals(path)) {
            handleMe(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> failedAttemptsMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String requestBody = new BufferedReader(new InputStreamReader(req.getInputStream())).lines().collect(Collectors.joining("\n"));
            JsonObject json = new Gson().fromJson(requestBody, JsonObject.class);

            if (json == null || !json.has("email") || !json.has("password")) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing email or password");
                return;
            }

            String identifier = json.get("email").getAsString().trim();
            String password = json.get("password").getAsString();

            User user = userDao.findByEmailOrUsername(identifier);
            if (user == null) {
                int attempts = failedAttemptsMap.merge(identifier, 1, Integer::sum);
                if (attempts >= 5) {
                    ServiceRegistry.getNotificationService().notify(
                            null, null, "LOGIN_FAILURE_THRESHOLD",
                            "Warning: " + attempts + " consecutive failed login attempts detected for email " + identifier,
                            "/super-admin/audit-logs"
                    );
                }
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
                return;
            }

            if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                String statusStr = user.getStatus().toUpperCase();
                if ("INACTIVE".equals(statusStr)) {
                    sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Your account has been deactivated. Please contact an administrator.");
                } else if ("SUSPENDED".equals(statusStr)) {
                    sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Your account has been suspended. Please contact support.");
                } else {
                    sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Your account is not active.");
                }
                return;
            }

            boolean passwordMatches = PasswordUtil.checkPassword(password, user.getPasswordHash());
            if (!passwordMatches) {
                int attempts = failedAttemptsMap.merge(identifier, 1, Integer::sum);
                if (attempts >= 5) {
                    ServiceRegistry.getNotificationService().notify(
                            null, null, "LOGIN_FAILURE_THRESHOLD",
                            "Warning: " + attempts + " consecutive failed login attempts detected for email " + identifier,
                            "/super-admin/audit-logs"
                    );
                }
                sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
                return;
            }

            failedAttemptsMap.remove(identifier);

            // Update user last login timestamp in PostgreSQL
            userDao.updateLastLogin(user.getId());

            // Generate JWT Token
            String token = JwtUtil.generateToken(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.isSuperadmin(),
                    user.getActiveTenantId()
            );

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("email", user.getEmail());
            userData.put("username", user.getUsername());
            userData.put("isSuperadmin", user.isSuperadmin());
            userData.put("activeTenantId", user.getActiveTenantId());
            userData.put("status", user.getStatus());

            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("success", true);
            responseData.put("token", token);
            responseData.put("user", userData);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authorization header");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        String userId = claims.getSubject();
        User user = userDao.findById(userId);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "User not found or inactive");
            return;
        }

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("username", user.getUsername());
        userData.put("isSuperadmin", user.isSuperadmin());
        userData.put("activeTenantId", user.getActiveTenantId());
        userData.put("status", user.getStatus());

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("success", true);
        responseData.put("user", userData);

        sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Logged out successfully");

        sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);
    }
}
