package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AiChatServlet Unit Tests")
public class AiChatServletTest {

    private ChatService chatService;
    private AiChatServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        chatService = mock(ChatService.class);
        servlet = new AiChatServlet(chatService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should process chat message turn and return 200 OK with JSON response")
    void testDoPostSuccess() throws Exception {
        String requestJson = "{\"sessionId\": \"11111111-1111-1111-1111-111111111111\", \"userMessage\": \"Hello AI\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
        when(request.getHeader("X-Tenant-ID")).thenReturn("00000000-0000-0000-0000-000000000001");

        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ChatResponse mockResponse = new ChatResponse(sessionId, tenantId, "AI Reply message", MessageRole.ASSISTANT, 2, 50);

        when(chatService.sendMessage(eq(sessionId), eq(tenantId), eq("Hello AI"), any(), any())).thenReturn(mockResponse);
        when(chatService.sendMessage(eq(sessionId), eq(tenantId), eq("Hello AI"), any())).thenReturn(mockResponse);
        when(chatService.sendMessage(eq(sessionId), eq(tenantId), eq("Hello AI"))).thenReturn(mockResponse);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        String jsonOutput = responseWriter.toString();
        assertTrue(jsonOutput.contains("AI Reply message"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when message is empty")
    void testDoPostValidationFailure() throws Exception {
        String requestJson = "{\"userMessage\": \"\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertTrue(responseWriter.toString().contains("VALIDATION_ERROR"));
    }
}
