package com.nexusivr.ai.controller;

import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.service.AiService;
import com.nexusivr.ai.service.FlowService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AiFlowServlet Unit Tests")
public class AiFlowServletTest {

    private FlowService flowService;
    private AiService aiService;
    private AiFlowServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        flowService = mock(FlowService.class);
        aiService = mock(AiService.class);
        servlet = new AiFlowServlet(flowService, aiService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should generate IVR flow and return 200 OK")
    void testGenerateFlowEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/ai/flow/generate");
        String requestJson = "{\"description\": \"Hospital appointment flow\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
        when(request.getHeader("X-Tenant-ID")).thenReturn("00000000-0000-0000-0000-000000000001");

        Flow generated = new Flow();
        generated.setName("Hospital IVR");
        generated.setFlowJson("{\"nodes\":[]}");
        when(flowService.generateAndSaveFlow(any(UUID.class), anyString(), eq("Hospital appointment flow"))).thenReturn(generated);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Hospital IVR"));
    }

    @Test
    @DisplayName("Should return 502 Bad Gateway with actualProviderUsed=none and fallbackUsed=false on ProviderException total failure")
    void testTotalFailureProviderExceptionFormatting() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/ai/flow/generate");
        String requestJson = "{\"description\": \"design Airplane IVR\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
        when(request.getHeader("X-Tenant-ID")).thenReturn("00000000-0000-0000-0000-000000000001");

        java.util.Map<String, String> providerStatuses = new java.util.LinkedHashMap<>();
        providerStatuses.put("groq", "CIRCUIT_OPEN");
        providerStatuses.put("gemini", "CIRCUIT_OPEN");
        providerStatuses.put("ollama", "CIRCUIT_OPEN");

        com.nexusivr.ai.service.exception.ProviderException ex = new com.nexusivr.ai.service.exception.ProviderException(
                "groq",
                "I couldn't reach any AI provider and don't have enough information to build a fallback for this request.",
                com.nexusivr.ai.service.exception.ProviderException.FailureReason.PROVIDER_ERROR,
                providerStatuses
        );

        when(flowService.generateAndSaveFlow(any(UUID.class), anyString(), anyString())).thenThrow(ex);

        servlet.doPost(request, response);

        verify(response).setStatus(502);
        String json = responseWriter.toString();
        assertTrue(json.contains("\"actualProviderUsed\": \"none\""), "Total failure must set actualProviderUsed to 'none'");
        assertTrue(json.contains("\"fallbackUsed\": false"), "Total failure must set fallbackUsed to false");
        assertFalse(json.contains("\"actualProviderUsed\": \"gemini\""), "Total failure must not claim gemini succeeded");
    }

    @Test
    @DisplayName("Should return 429 status code and retryAfterSeconds ~300 when all providers are OPEN with quota exceeded")
    void testQuotaExceededResponseContainsCorrectRemainingCooldown() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/ai/flow/generate");
        String requestJson = "{\"description\": \"design Clinic IVR\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestJson)));
        when(request.getHeader("X-Tenant-ID")).thenReturn("00000000-0000-0000-0000-000000000001");

        com.nexusivr.ai.ai.ProviderManager pm = ServiceRegistry.getProviderManager();
        pm.markRateLimited("groq");
        pm.markRateLimited("gemini");
        pm.markRateLimited("ollama");

        java.util.Map<String, String> providerStatuses = new java.util.LinkedHashMap<>();
        providerStatuses.put("groq", "CIRCUIT_OPEN");
        providerStatuses.put("gemini", "CIRCUIT_OPEN");
        providerStatuses.put("ollama", "CIRCUIT_OPEN");

        com.nexusivr.ai.service.exception.ProviderException ex = new com.nexusivr.ai.service.exception.ProviderException(
                "all",
                "All providers exhausted after retries due to quota limits",
                com.nexusivr.ai.service.exception.ProviderException.FailureReason.QUOTA_EXCEEDED,
                providerStatuses
        );

        when(flowService.generateAndSaveFlow(any(UUID.class), anyString(), anyString())).thenThrow(ex);

        servlet.doPost(request, response);

        verify(response).setStatus(429);
        String json = responseWriter.toString();
        assertTrue(json.contains("\"retryAfterSeconds\":"), "Error response must include retryAfterSeconds");
        assertFalse(json.contains("\"retryAfterSeconds\": 30,"), "retryAfterSeconds must NOT be hardcoded 30 when 300s quota cooldown is in effect");
        assertTrue(json.contains("\"code\": \"QUOTA_EXCEEDED\"") || json.contains("\"code\":\"QUOTA_EXCEEDED\""));
    }
}
