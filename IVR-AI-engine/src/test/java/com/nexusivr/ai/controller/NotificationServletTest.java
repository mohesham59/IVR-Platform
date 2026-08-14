package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.Notification;
import com.nexusivr.ai.service.NotificationService;
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

public class NotificationServletTest {

    private NotificationService notificationService;
    private NotificationServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    public void setUp() throws Exception {
        notificationService = mock(NotificationService.class);
        servlet = new NotificationServlet(notificationService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getHeader("X-Tenant-ID")).thenReturn(TENANT_ID.toString());
    }

    @Test
    public void testGetNotificationsTenantScoped() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        when(request.getHeader("X-User-Role")).thenReturn("tenant_admin");

        Notification n = new Notification(UUID.randomUUID(), TENANT_ID, null, "Flow published", "/flows", false, new Timestamp(System.currentTimeMillis()), "FLOW_PUBLISHED");
        when(notificationService.getNotifications(TENANT_ID, false, false, 20)).thenReturn(List.of(n));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("Flow published"));
    }

    @Test
    public void testGetNotificationsSuperAdminPlatformScoped() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        when(request.getHeader("X-Is-SuperAdmin")).thenReturn("true");

        Notification platformNotif = new Notification(UUID.randomUUID(), null, null, "Company created: Meridian", "/super-admin/companies", false, new Timestamp(System.currentTimeMillis()), "COMPANY_CREATED");
        when(notificationService.getNotifications(null, true, false, 20)).thenReturn(List.of(platformNotif));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("COMPANY_CREATED"));
    }

    @Test
    public void testMarkAllRead() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/read-all");
        when(notificationService.markAllAsRead(TENANT_ID)).thenReturn(true);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("All notifications marked as read"));
    }
}
