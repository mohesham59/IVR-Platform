package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.common.ProviderAttemptDto;
import com.nexusivr.ai.model.Flow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Score & Diagnostic Metadata Tests")
public class ScoreAndDiagnosticMetadataTest {

    @Test
    @DisplayName("Should verify Flow model carries validationScore and score getters/setters")
    void testFlowValidationScore() {
        Flow flow = new Flow();
        assertEquals(100, flow.getValidationScore());

        flow.setValidationScore(65);
        assertEquals(65, flow.getValidationScore());
        assertEquals(65, flow.getScore());

        flow.setScore(80);
        assertEquals(80, flow.getValidationScore());
        assertEquals(80, flow.getScore());
    }

    @Test
    @DisplayName("Should verify ChatResponse carries validationResult score and provider attempts from Flow")
    void testChatResponseCopiesValidationScoreAndAttempts() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Flow flow = new Flow();
        flow.setValidationScore(65);
        flow.setSelectedProvider("groq");
        flow.setActualProviderUsed("groq");
        flow.setProviderAttempts(List.of(
                new ProviderAttemptDto("groq", 200, "Success", 0)
        ));

        ChatResponse response = new ChatResponse(sessionId, tenantId, "Generated flow", null, 1, 100);
        response.setSelectedProvider(flow.getSelectedProvider());
        response.setActualProviderUsed(flow.getActualProviderUsed());
        response.setProviderAttempts(flow.getProviderAttempts());
        if (flow.getValidationScore() > 0) {
            com.nexusivr.ai.dto.response.FlowValidationResponse valRes = new com.nexusivr.ai.dto.response.FlowValidationResponse();
            valRes.setScore(flow.getValidationScore());
            response.setValidationResult(valRes);
        }

        assertNotNull(response.getValidationResult());
        assertEquals(65, response.getValidationResult().getScore());
        assertNotNull(response.getProviderAttempts());
        assertEquals(1, response.getProviderAttempts().size());
        assertEquals("groq", response.getProviderAttempts().get(0).getProvider());
    }
}
