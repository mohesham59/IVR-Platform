package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.ReportsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SuperAdminReportsServletTest {

    private ReportsService reportsService;
    private SuperAdminReportsServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    public void setUp() throws Exception {
        reportsService = mock(ReportsService.class);
        servlet = new SuperAdminReportsServlet(reportsService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    public void testGetTelephonyReportAsSuperAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/reports/telephony");

        Map<String, Object> row = Map.of(
                "tenantId", "11111111-1111-1111-1111-111111111111",
                "displayName", "Meridian Health",
                "totalCalls", 12,
                "aiCalls", 8,
                "publishedIvrs", 3
        );
        when(reportsService.getTenantTelephonyReport(null, null, null)).thenReturn(List.of(row));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("Meridian Health"));
    }

    @Test
    public void testGetBillingReportAsSuperAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/reports/billing");

        Map<String, Object> row = Map.of(
                "tenantId", "11111111-1111-1111-1111-111111111111",
                "displayName", "Meridian Health",
                "status", "ACTIVE",
                "inputTokens", 15000L,
                "outputTokens", 9000L,
                "estimatedBillUsd", "25.50"
        );
        when(reportsService.getTenantBillingReport(null, null, null)).thenReturn(List.of(row));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("25.50"));
    }

    @Test
    public void testExportTelephonyReportCsv() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/reports/telephony/export");
        when(reportsService.exportTelephonyReportCsv(null, null, null)).thenReturn("Tenant ID,Company Name\n\"1111\",\"Meridian Health\"");

        servlet.service(request, response);

        verify(response).setContentType("text/csv");
        String body = responseWriter.toString();
        assertTrue(body.contains("Meridian Health"));
    }

    @Test
    public void testRbacForbiddenForTenantAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/reports/telephony");
        when(request.getHeader("X-User-Role")).thenReturn("tenant_admin");

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String body = responseWriter.toString();
        assertTrue(body.contains("Forbidden: Super Admin access required"));
    }
}
