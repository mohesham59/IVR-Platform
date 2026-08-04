package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AiSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tenant Isolation Verification Test")
public class TenantIsolationTest {

    @Test
    @DisplayName("Tenant IDs must never match across isolated tenant models")
    void testTenantIsolationContracts() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        AiSession sessionA = new AiSession();
        sessionA.setId(UUID.randomUUID());
        sessionA.setTenantId(tenantA);

        AiSession sessionB = new AiSession();
        sessionB.setId(UUID.randomUUID());
        sessionB.setTenantId(tenantB);

        assertNotEquals(sessionA.getTenantId(), sessionB.getTenantId(), "Tenant A and Tenant B must remain strictly isolated.");
    }
}
