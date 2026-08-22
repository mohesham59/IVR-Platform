package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.AuditLog;
import com.nexusivr.ai.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditLogsServletTest {

    private AuditLogService service;
    private AuditLogsServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    public void setUp() throws Exception {
        service = mock(AuditLogService.class);
        servlet = new AuditLogsServlet(service);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    public void testGetAuditLogsAsSuperAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/audit-logs");
        when(request.getParameter("page")).thenReturn("1");
        when(request.getParameter("pageSize")).thenReturn("10");

        AuditLog logItem = new AuditLog();
        logItem.setId(UUID.randomUUID());
        logItem.setActionType("COMPANY_CREATED");
        logItem.setActorEmail("admin@nexusivr.com");
        logItem.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        Map<String, Object> serviceResult = Map.of(
                "items", List.of(logItem),
                "total", 1,
                "page", 1,
                "pageSize", 10,
                "totalPages", 1
        );
        when(service.getPaginatedAuditLogs(null, null, null, null, 1, 10)).thenReturn(serviceResult);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("COMPANY_CREATED"));
    }

    @Test
    public void testRbacForbiddenForTenantAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/audit-logs");
        when(request.getHeader("X-User-Role")).thenReturn("tenant_admin");

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String body = responseWriter.toString();
        assertTrue(body.contains("Forbidden: Super Admin access required"));
    }
}
