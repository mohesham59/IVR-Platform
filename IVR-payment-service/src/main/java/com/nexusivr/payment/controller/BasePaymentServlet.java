package com.nexusivr.payment.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexusivr.payment.PaymobApiException;
import com.nexusivr.payment.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Base servlet providing CORS, authentication validation, JSON handling, and structured error responses.
 */
public abstract class BasePaymentServlet extends HttpServlet {

    protected static final Logger logger = LoggerFactory.getLogger(BasePaymentServlet.class);
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Set CORS headers
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Tenant-ID, Tenant-ID, X-Superadmin, X-User-Role");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        super.service(req, resp);
    }

    protected String verifyTenantAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 1. Check explicit tenant headers (most reliable — frontend sends real current tenant)
        String tenantHeader = req.getHeader("X-Tenant-ID");
        if (tenantHeader == null || tenantHeader.isBlank()) tenantHeader = req.getHeader("Tenant-ID");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader.trim();
        }

        // 2. Check Authorization: Bearer <JWT> — may be stale if user hasn't re-logged in
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);
            if (claims != null && claims.get("activeTenantId") != null) {
                return claims.get("activeTenantId").toString();
            }
        }

        // 3. Fallback to query parameter
        String tenantParam = req.getParameter("tenantId");
        if (tenantParam != null && !tenantParam.isBlank()) {
            return tenantParam.trim();
        }

        sendJsonResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("error", "Tenant authentication required"));
        return null;
    }

    protected boolean isSuperAdmin(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = JwtUtil.validateToken(token);
                if (claims != null) {
                    Boolean isSuper = claims.get("isSuperadmin", Boolean.class);
                    if (Boolean.TRUE.equals(isSuper)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // ignore and check fallback
            }
        }
        String isSuperHeader = req.getHeader("X-Superadmin");
        String roleHeader = req.getHeader("X-User-Role");
        return "true".equalsIgnoreCase(isSuperHeader) || "SUPERADMIN".equalsIgnoreCase(roleHeader);
    }

    protected boolean verifySuperAdminAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 1. Check Authorization: Bearer <JWT>
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);
            if (claims != null) {
                Boolean isSuper = claims.get("isSuperadmin", Boolean.class);
                if (Boolean.TRUE.equals(isSuper)) {
                    return true;
                }
            }
        }

        // 2. Fallback to platform headers for development / direct proxying
        String isSuperHeader = req.getHeader("X-Superadmin");
        String roleHeader = req.getHeader("X-User-Role");
        if ("true".equalsIgnoreCase(isSuperHeader) || "SUPERADMIN".equalsIgnoreCase(roleHeader)) {
            return true;
        }

        sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("error", "Access denied: Super Admin authorization required"));
        return false;
    }

    protected <T> T parseRequestBody(HttpServletRequest req, Class<T> clazz) throws IOException {
        req.setCharacterEncoding("UTF-8");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();
        if (body.isBlank()) return null;
        return gson.fromJson(body, clazz);
    }

    protected void sendJsonResponse(HttpServletResponse resp, int statusCode, Object data) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(data));
    }

    protected void handleError(HttpServletResponse resp, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException) {
            logger.warn("Validation error: {}", e.getMessage());
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", e.getMessage()));
        } else if (e instanceof PaymobApiException paymobEx) {
            logger.error("Paymob API error: status={}, body={}", paymobEx.getStatusCode(), paymobEx.getResponseBody());
            sendJsonResponse(resp, paymobEx.getStatusCode() > 0 ? paymobEx.getStatusCode() : 502, 
                    Map.of("error", "Payment provider error", "details", paymobEx.getMessage()));
        } else {
            logger.error("Internal service error: {}", e.getMessage(), e);
            sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("error", "An internal error occurred"));
        }
    }
}
