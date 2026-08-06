package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.ai.dao.TenantDao;
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
        "/api/v1/super-admin/companies"
})
public class SuperAdminTenantsServlet extends BaseAiServlet {

    private final TenantDao tenantDao = new TenantDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            List<TenantDao.Tenant> tenants = tenantDao.findAllTenants();
            List<Map<String, Object>> responseList = new ArrayList<>();
            for (TenantDao.Tenant t : tenants) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", t.getId());
                map.put("name", t.getName());
                map.put("displayName", t.getDisplayName() != null ? t.getDisplayName() : t.getName());
                map.put("ownerUserId", t.getOwnerUserId());
                map.put("ownerUsername", t.getOwnerUsername() != null ? t.getOwnerUsername() : "—");
                map.put("ownerEmail", t.getOwnerEmail() != null ? t.getOwnerEmail() : "—");
                map.put("status", t.getStatus());
                map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
                map.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "");
                responseList.add(map);
            }

            // Also send available system users for selecting company owner
            List<User> availableUsers = userDao.findAllUsers();
            List<Map<String, Object>> userOptions = new ArrayList<>();
            for (User u : availableUsers) {
                Map<String, Object> um = new LinkedHashMap<>();
                um.put("id", u.getId());
                um.put("username", u.getUsername());
                um.put("email", u.getEmail());
                userOptions.add(um);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tenants", responseList);
            result.put("userOptions", userOptions);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String requestBody = new BufferedReader(new InputStreamReader(req.getInputStream())).lines().collect(Collectors.joining("\n"));
            JsonObject json = new Gson().fromJson(requestBody, JsonObject.class);

            if (json == null || !json.has("name") || json.get("name").getAsString().isBlank()) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Company name is required");
                return;
            }

            String name = json.get("name").getAsString().trim();
            String displayName = json.has("displayName") ? json.get("displayName").getAsString().trim() : name;
            String ownerUserId = json.has("ownerUserId") ? json.get("ownerUserId").getAsString() : null;
            String status = json.has("status") ? json.get("status").getAsString() : "ACTIVE";

            TenantDao.Tenant created = tenantDao.createTenant(name, displayName, ownerUserId, status);
            if (created != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "Company created successfully");
                result.put("tenant", created);
                sendJsonResponse(resp, HttpServletResponse.SC_CREATED, result);
            } else {
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create company");
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
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Company id is required");
                return;
            }

            String tenantId = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString().trim() : null;
            String displayName = json.has("displayName") ? json.get("displayName").getAsString().trim() : name;
            String ownerUserId = json.has("ownerUserId") ? json.get("ownerUserId").getAsString() : null;
            String status = json.has("status") ? json.get("status").getAsString() : "ACTIVE";

            boolean updated = tenantDao.updateTenant(tenantId, name, displayName, ownerUserId, status);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", updated);
            result.put("message", updated ? "Company updated successfully" : "Failed to update company");

            sendJsonResponse(resp, updated ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String tenantId = req.getParameter("id");
        if (tenantId == null || tenantId.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Company id parameter is required");
            return;
        }

        try {
            boolean success = tenantDao.deleteTenant(tenantId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("message", success ? "Company deleted successfully" : "Failed to delete company");

            sendJsonResponse(resp, success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
