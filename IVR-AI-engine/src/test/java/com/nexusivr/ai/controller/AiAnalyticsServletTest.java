package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.AnalyticsResponse;
import com.nexusivr.ai.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AiAnalyticsServlet Unit Tests")
public class AiAnalyticsServletTest {

    private AnalyticsService analyticsService;
    private AiAnalyticsServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        analyticsService = mock(AnalyticsService.class);
        servlet = new AiAnalyticsServlet(analyticsService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should return 200 OK with Analytics metrics")
    void testGetAnalytics() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AnalyticsResponse mockAnalytics = new AnalyticsResponse(tenantId, 10, 3, 45);
        when(analyticsService.getTenantAnalytics(any(UUID.class))).thenReturn(mockAnalytics);

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String output = responseWriter.toString();
        assertTrue(output.contains("activeSessions"));
        assertTrue(output.contains("totalMessages"));
    }
}
