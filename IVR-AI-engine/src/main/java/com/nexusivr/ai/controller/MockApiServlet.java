package com.nexusivr.ai.controller;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class MockApiServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        
        String type = req.getParameter("type");
        if ("error".equals(type)) {
            resp.setStatus(400);
            out.print("{\"status\":\"error\",\"message\":\"This is a mock error response.\"}");
        } else {
            resp.setStatus(200);
            out.print("{\"status\":\"success\",\"message\":\"Mock GET request successful!\"}");
        }
        out.flush();
    }
}
