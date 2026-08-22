package com.nexusivr.ai.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.PhoneNumber;
import com.nexusivr.ai.service.PhoneNumberService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {
        "/api/v1/tenant/phone-numbers",
        "/api/v1/tenant/phone-numbers/*",
        "/api/v1/tenant/published-flows"
})
public class TenantPhoneNumberServlet extends BaseAiServlet {

    private final PhoneNumberService phoneNumberService;

    public TenantPhoneNumberServlet(PhoneNumberService phoneNumberService) {
        this.phoneNumberService = phoneNumberService;
    }

    public TenantPhoneNumberServlet() {
        this(ServiceRegistry.getPhoneNumberService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            if (path.contains("/published-flows")) {
                List<Map<String, String>> publishedFlows = phoneNumberService.getPublishedFlows(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", publishedFlows);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            if (path.endsWith("/stats")) {
                Map<String, Object> stats = phoneNumberService.getPhoneNumberStats(tenantId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("data", stats);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
                return;
            }

            List<PhoneNumber> list = phoneNumberService.getPhoneNumbers(tenantId);
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

            String number = null;
            String country = "US";
            String provider = "Twilio";

            if (body != null && !body.isBlank()) {
                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("phoneNumber")) number = json.get("phoneNumber").getAsString();
                    if (json.has("number") && (number == null || number.isBlank())) number = json.get("number").getAsString();
                    if (json.has("country")) country = json.get("country").getAsString();
                    if (json.has("provider")) provider = json.get("provider").getAsString();
                } catch (Exception e) {
                    logger.error("[TenantPhoneNumberServlet] Error parsing JSON body: {}", e.getMessage());
                }
            }

            if (number == null || number.isBlank()) {
                throw new ValidationException("Phone number parameter is required");
            }

            PhoneNumber created = phoneNumberService.addPhoneNumber(tenantId, number, country, provider);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("data", created);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            String path = req.getRequestURI();

            // Expected path: /api/v1/tenant/phone-numbers/{id}/assign-ivr
            String phoneIdStr = null;
            int idx = path.indexOf("/phone-numbers/");
            if (idx != -1) {
                String sub = path.substring(idx + "/phone-numbers/".length());
                String[] parts = sub.split("/");
                if (parts.length > 0) {
                    phoneIdStr = parts[0];
                }
            }

            if (phoneIdStr == null || phoneIdStr.isBlank()) {
                throw new ValidationException("Phone number ID is required in URL path");
            }

            UUID phoneId;
            try {
                phoneId = UUID.fromString(phoneIdStr);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid Phone number ID format");
            }

            req.setCharacterEncoding("UTF-8");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String body = sb.toString();

            String flowId = null;
            String flowName = null;

            if (body != null && !body.isBlank()) {
                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("flowId")) flowId = json.get("flowId").getAsString();
                    if (json.has("flowName")) flowName = json.get("flowName").getAsString();
                } catch (Exception e) {
                    logger.error("[TenantPhoneNumberServlet] Error parsing assign-ivr JSON body: {}", e.getMessage());
                }
            }

            if (flowId == null || flowId.isBlank()) {
                throw new ValidationException("flowId is required for IVR assignment");
            }

            PhoneNumber updated = phoneNumberService.assignIvrFlow(tenantId, phoneId, flowId, flowName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "IVR flow assigned and Asterisk dialplan provisioned successfully");
            result.put("data", updated);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, result);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
