package com.nexusivr.ai.controller;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for IVR-AI-engine.
 * Responds with 200 OK and service status JSON.
 */
@WebServlet(urlPatterns = {"/health", "/api/v1/health"})
public class HealthServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(HttpServletResponse.SC_OK);

        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("status", "UP");
        statusMap.put("service", "IVR-AI-engine");
        statusMap.put("timestamp", Instant.now().toString());

        try (PrintWriter writer = resp.getWriter()) {
            writer.write(gson.toJson(statusMap));
            writer.flush();
        }
    }
}
