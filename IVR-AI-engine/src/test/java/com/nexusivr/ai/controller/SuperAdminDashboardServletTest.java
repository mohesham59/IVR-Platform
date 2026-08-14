package com.nexusivr.ai.controller;

import com.nexusivr.ai.service.SuperAdminDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SuperAdminDashboardServletTest {

    private SuperAdminDashboardService service;
    private SuperAdminDashboardServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    public void setUp() throws Exception {
        service = mock(SuperAdminDashboardService.class);
        servlet = new SuperAdminDashboardServlet(service);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    public void testGetPlatformStatsAsSuperAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/platform-stats");

        Map<String, Object> stats = Map.of(
                "totalCompanies", 5,
                "activeCompanies", 4,
                "totalUsers", 12,
                "activeCalls", 0,
                "publishedIvrs", 18,
                "aiRequestsToday", 48
        );
        when(service.getPlatformStats()).thenReturn(stats);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("\"totalCompanies\": 5"));
    }

    @Test
    public void testRbacForbiddenForTenantAdmin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/platform-stats");
        when(request.getHeader("X-User-Role")).thenReturn("tenant_admin");

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        String body = responseWriter.toString();
        assertTrue(body.contains("Forbidden: Super Admin access required"));
    }

    @Test
    public void testGetLatestCompanies() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/latest-companies");

        Map<String, Object> c1 = Map.of("name", "Meridian Health", "plan", "Enterprise", "users", 14, "status", "ACTIVE", "joined", "2026-08-14");
        when(service.getLatestCompanies()).thenReturn(List.of(c1));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("Meridian Health"));
    }

    @Test
    public void testGetRecentActivityFeed() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/recent-activity");

        Map<String, Object> act = Map.of("action", "Company Created", "subject", "Meridian Health", "time", "10m ago", "type", "info");
        when(service.getRecentActivityFeed()).thenReturn(List.of(act));
        when(service.getLatestUsers()).thenReturn(Collections.emptyList());

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("Meridian Health"));
    }
}
