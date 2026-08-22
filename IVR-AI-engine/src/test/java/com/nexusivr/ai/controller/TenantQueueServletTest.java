package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.AgentStateRecord;
import com.nexusivr.ai.model.Queue;
import com.nexusivr.ai.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TenantQueueServletTest {

    private QueueService queueService;
    private TenantQueueServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUEUE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID AGENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    @BeforeEach
    public void setUp() throws Exception {
        queueService = mock(QueueService.class);
        servlet = new TenantQueueServlet(queueService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        when(request.getHeader("X-Tenant-ID")).thenReturn(TENANT_ID.toString());
    }

    @Test
    public void testGetQueuesList() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/queues");

        Queue q = new Queue(QUEUE_ID, TENANT_ID, "Support L1", "round_robin", 15, 300, "default", "voicemail", "{}", "active", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        when(queueService.getQueues(TENANT_ID)).thenReturn(List.of(q));

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("Support L1"));
    }

    @Test
    public void testCreateQueue() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/queues");
        String json = "{\"name\":\"VIP Queue\",\"strategy\":\"least_recent\",\"wrapUpTimeSeconds\":20}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(json)));

        Queue created = new Queue(UUID.randomUUID(), TENANT_ID, "VIP Queue", "least_recent", 20, 300, "default", "voicemail", "{}", "active", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        when(queueService.createQueue(eq(TENANT_ID), any(Queue.class))).thenReturn(created);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("VIP Queue"));
    }

    @Test
    public void testPatchAgentState() throws Exception {
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getRequestURI()).thenReturn("/api/v1/agents/" + AGENT_ID + "/state");
        String json = "{\"state\":\"paused\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(json)));

        AgentStateRecord record = new AgentStateRecord(UUID.randomUUID(), AGENT_ID, "paused", new Timestamp(System.currentTimeMillis()), null);
        when(queueService.updateAgentState(TENANT_ID, AGENT_ID, "paused")).thenReturn(record);

        servlet.service(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"success\": true"));
        assertTrue(body.contains("paused"));
    }
}
