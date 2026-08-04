package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.Flow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowDao Integration Preparation Test")
public class FlowDaoTest {

    @Test
    @DisplayName("Should construct Flow model cleanly")
    void testFlowConstruction() {
        UUID flowId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Flow flow = new Flow();
        flow.setId(flowId);
        flow.setTenantId(tenantId);
        flow.setName("Customer Support Flow");
        flow.setDescription("Default IVR main menu");
        flow.setFlowJson("{\"nodes\":[], \"edges\":[]}");
        flow.setStatus("DRAFT");

        assertEquals(flowId, flow.getId());
        assertEquals(tenantId, flow.getTenantId());
        assertEquals("Customer Support Flow", flow.getName());
        assertEquals("DRAFT", flow.getStatus());
    }
}
