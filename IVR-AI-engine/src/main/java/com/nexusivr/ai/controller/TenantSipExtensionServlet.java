package com.nexusivr.ai.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.SipExtension;
import com.nexusivr.ai.service.SipExtensionService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/tenant/sip-extensions",
        "/api/v1/tenant/sip-extensions/*"
})
public class TenantSipExtensionServlet extends BaseAiServlet {

    private final SipExtensionService sipExtensionService;

    public TenantSipExtensionServlet(SipExtensionService sipExtensionService) {
        this.sipExtensionService = sipExtensionService;
    }

    public TenantSipExtensionServlet() {
        this(ServiceRegistry.getSipExtensionService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            List<SipExtension> list = sipExtensionService.getSipExtensions(tenantId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("data", list);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);

            req.setCharacterEncoding("UTF-8");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String body = sb.toString();

            String extensionNumber = null;
            String displayName = null;
            String sipPassword = null;
            boolean tlsEnabled = false;

            if (body != null && !body.isBlank()) {
                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("extensionNumber")) extensionNumber = json.get("extensionNumber").getAsString();
                    if (json.has("number") && (extensionNumber == null || extensionNumber.isBlank())) {
                        extensionNumber = json.get("number").getAsString();
                    }
                    if (json.has("displayName")) displayName = json.get("displayName").getAsString();
                    if (json.has("name") && (displayName == null || displayName.isBlank())) {
                        displayName = json.get("name").getAsString();
                    }
                    if (json.has("sipPassword")) sipPassword = json.get("sipPassword").getAsString();
                    if (json.has("password") && (sipPassword == null || sipPassword.isBlank())) {
                        sipPassword = json.get("password").getAsString();
                    }
                    if (json.has("tlsEnabled")) tlsEnabled = json.get("tlsEnabled").getAsBoolean();
                } catch (Exception e) {
                    logger.error("[TenantSipExtensionServlet] Error parsing JSON body: {}", e.getMessage());
                }
            }

            if (extensionNumber == null || extensionNumber.isBlank()) {
                throw new ValidationException("extensionNumber parameter is required");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new ValidationException("displayName parameter is required");
            }
            if (sipPassword == null || sipPassword.isBlank()) {
                sipPassword = "1234"; // Default fallback password if none specified
            }

            SipExtension created = sipExtensionService.createSipExtension(tenantId, extensionNumber, displayName, sipPassword, tlsEnabled);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "SIP Extension created and Asterisk PJSIP provisioned successfully");
            result.put("data", created);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            String idStr = null;
            int idx = path.indexOf("/sip-extensions/");
            if (idx != -1) {
                idStr = path.substring(idx + "/sip-extensions/".length()).trim();
            }

            if (idStr == null || idStr.isBlank()) {
                throw new ValidationException("SIP Extension ID is required in URL path");
            }

            UUID extId;
            try {
                extId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid SIP Extension ID format");
            }

            boolean deleted = sipExtensionService.deleteSipExtension(tenantId, extId);
            if (!deleted) {
                throw new ValidationException("Extension not found or failed to delete");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "SIP Extension deleted and Asterisk configuration removed successfully");
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
