package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.CallLog;
import com.nexusivr.ai.service.DashboardService;
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

public class DashboardServletTest {

    private DashboardService dashboardService;
    private DashboardServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    public void setUp() throws Exception {
        dashboardService = mock(DashboardService.class);
        servlet = new DashboardServlet(dashboardService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getHeader("X-Tenant-ID")).thenReturn(TENANT_ID.toString());
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    public void testGetDashboardStats() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/dashboard/stats");

        Map<String, Object> stats = Map.of(
                "totalCalls", 128,
                "answeredCalls", 112,
                "missedCalls", 16,
                "avgDurationSeconds", 142
        );
        when(dashboardService.getDashboardStats(TENANT_ID)).thenReturn(stats);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("128"));
    }

    @Test
    public void testGetActiveCallsCount() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/calls/active-count");
        when(dashboardService.getActiveCallsCount(TENANT_ID)).thenReturn(5);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("\"activeCalls\": 5"));
    }

    @Test
    public void testGetRecentCalls() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/dashboard/recent-calls");
        when(request.getParameter("limit")).thenReturn("10");

        CallLog c1 = new CallLog(UUID.randomUUID(), "sess-1", TENANT_ID, "+1 (555) 234-5678", "Support L1", "ANSWERED", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis() + 240000), 240, "Support_Agent");
        when(dashboardService.getRecentCalls(TENANT_ID, 10)).thenReturn(List.of(c1));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("+1 (555) 234-5678"));
        assertTrue(body.contains("Support L1"));
    }

    @Test
    public void testExportRecentCallsCsv() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/dashboard/recent-calls/export");
        when(dashboardService.exportRecentCallsCsv(TENANT_ID)).thenReturn("Call ID,Session ID,Caller,Scenario/Queue,Last Node,Status,Duration (sec),Start Time\n1,sess-1,+1555123,Support L1,Agent,ANSWERED,240,2026-08-14\n");

        servlet.service(request, response);

        verify(response).setContentType("text/csv");
        String body = responseWriter.toString();
        assertTrue(body.contains("Call ID,Session ID,Caller,Scenario/Queue,Last Node,Status,Duration (sec),Start Time"));
    }
}
