package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.ai.dao.UserDao;
import com.nexusivr.ai.model.User;
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
        "/api/v1/super-admin/users",
        "/api/v1/super-admin/users/reset-password"
})
public class SuperAdminUsersServlet extends BaseAiServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            List<User> users = userDao.findAllUsers();
            List<Map<String, Object>> responseList = new ArrayList<>();
            for (User u : users) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", u.getId());
                map.put("activeTenantId", u.getActiveTenantId());
                map.put("email", u.getEmail());
                map.put("username", u.getUsername());
                map.put("isSuperadmin", u.isSuperadmin());
                map.put("role", u.isSuperadmin() ? "Super Admin" : "Tenant Admin");
                map.put("status", u.getStatus());
                map.put("lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : "Never");
                map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
                responseList.add(map);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("users", responseList);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        if ("/api/v1/super-admin/users/reset-password".equals(path)) {
            handleResetPassword(req, resp);
            return;
        }

        try {
            String requestBody = new BufferedReader(new InputStreamReader(req.getInputStream())).lines().collect(Collectors.joining("\n"));
            JsonObject json = new Gson().fromJson(requestBody, JsonObject.class);

            if (json == null || !json.has("email") || !json.has("username")) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Email and username are required");
                return;
            }

            String email = json.get("email").getAsString().trim();
            String username = json.get("username").getAsString().trim();
            String password = json.has("password") ? json.get("password").getAsString() : "password";
            boolean isSuperAdmin = json.has("isSuperadmin") && json.get("isSuperadmin").getAsBoolean();
            String status = json.has("status") ? json.get("status").getAsString() : "ACTIVE";

            if (userDao.findByEmailOrUsername(email) != null) {
                sendJsonResponse(resp, HttpServletResponse.SC_CONFLICT, "User with this email already exists");
                return;
            }

            User user = new User();
            user.setEmail(email);
            user.setUsername(username);
            user.setPasswordHash(password);
            user.setSuperadmin(isSuperAdmin);
            user.setStatus(status);

            User created = userDao.createUser(user);
            if (created != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "User created successfully");
                sendJsonResponse(resp, HttpServletResponse.SC_CREATED, result);
            } else {
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create user");
            }

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String requestBody = new BufferedReader(new InputStreamReader(req.getInputStream())).lines().collect(Collectors.joining("\n"));
            JsonObject json = new Gson().fromJson(requestBody, JsonObject.class);

            if (json == null || !json.has("id")) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "User id is required");
                return;
            }

            String userId = json.get("id").getAsString();
            User existing = userDao.findById(userId);
            if (existing == null) {
                sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }

            if (json.has("email")) existing.setEmail(json.get("email").getAsString().trim());
            if (json.has("username")) existing.setUsername(json.get("username").getAsString().trim());
            if (json.has("isSuperadmin")) existing.setSuperadmin(json.get("isSuperadmin").getAsBoolean());
            if (json.has("status")) existing.setStatus(json.get("status").getAsString());

            boolean success = userDao.updateUser(existing);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("message", success ? "User updated successfully" : "Failed to update user");

            sendJsonResponse(resp, success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String userId = req.getParameter("id");
        if (userId == null || userId.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "User id parameter is required");
            return;
        }

        try {
            boolean success = userDao.deleteUser(userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("message", success ? "User deleted successfully" : "Failed to delete user");

            sendJsonResponse(resp, success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void handleResetPassword(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String requestBody = new BufferedReader(new InputStreamReader(req.getInputStream())).lines().collect(Collectors.joining("\n"));
            JsonObject json = new Gson().fromJson(requestBody, JsonObject.class);

            if (json == null || !json.has("id") || !json.has("newPassword")) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "User id and newPassword are required");
                return;
            }

            String userId = json.get("id").getAsString();
            String newPassword = json.get("newPassword").getAsString();

            boolean success = userDao.updatePassword(userId, newPassword);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("message", success ? "Password reset successfully" : "Failed to reset password");

            sendJsonResponse(resp, success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
