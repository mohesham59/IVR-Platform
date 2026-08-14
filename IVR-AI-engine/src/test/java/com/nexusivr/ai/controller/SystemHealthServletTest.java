package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.SystemHealthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SystemHealthServletTest {

    private SystemHealthService service;
    private SystemHealthServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    public void setUp() throws Exception {
        service = mock(SystemHealthService.class);
        servlet = new SystemHealthServlet(service);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    public void testGetSystemHealthAsSuperAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/system-health");

        Map<String, Object> jvm = Map.of("status", "HEALTHY", "usedMemoryMb", 256, "activeThreads", 14);
        Map<String, Object> db = Map.of("status", "HEALTHY", "activeConnections", 2, "idleConnections", 4);
        Map<String, Map<String, Object>> ai = Map.of("groq", Map.of("status", "HEALTHY", "circuitState", "CLOSED"));
        Map<String, Object> asterisk = Map.of("status", "HEALTHY", "host", "localhost", "connected", true);

        Map<String, Object> health = Map.of(
                "overallStatus", "HEALTHY",
                "jvm", jvm,
                "database", db,
                "aiProviders", ai,
                "asterisk", asterisk
        );
        when(service.getSystemHealth()).thenReturn(health);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("overallStatus"));
        assertTrue(body.contains("aiProviders"));
    }

    @Test
    public void testRbacForbiddenForTenantAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/system-health");
        when(request.getHeader("X-User-Role")).thenReturn("tenant_admin");

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String body = responseWriter.toString();
        assertTrue(body.contains("Forbidden: Super Admin access required"));
    }
}
