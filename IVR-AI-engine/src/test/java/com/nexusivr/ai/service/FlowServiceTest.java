package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.LlmProviderFactory;
import com.nexusivr.ai.ai.MockLlmClient;
import com.nexusivr.ai.dao.FlowDao;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowService Unit Tests")
public class FlowServiceTest {

    private FlowDao flowDao;
    private AiService aiService;
    private FlowService flowService;

    @BeforeEach
    void setUp() {
        LlmProviderFactory.setOverrideClient(new MockLlmClient());
        flowDao = new FlowDao();
        aiService = new AiService();
        flowService = new FlowService(flowDao, aiService);
    }

    @AfterEach
    void tearDown() {
        LlmProviderFactory.clearCache();
    }

    @Test
    @DisplayName("Should throw ValidationException if flow name is missing")
    void testFlowValidationNameMissing() {
        UUID tenantId = UUID.randomUUID();
        Flow flow = new Flow();
        flow.setFlowJson("{\"nodes\":[]}");

        assertThrows(ValidationException.class, () -> flowService.createFlow(tenantId, flow));
    }

    @Test
    @DisplayName("Should throw ValidationException if flowJson is empty")
    void testFlowValidationJsonEmpty() {
        UUID tenantId = UUID.randomUUID();
        Flow flow = new Flow();
        flow.setName("Test Flow");
        flow.setFlowJson("   ");

        assertThrows(ValidationException.class, () -> flowService.createFlow(tenantId, flow));
    }
}
