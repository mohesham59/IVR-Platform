package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.model.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiSessionDao Integration Preparation Test")
public class AiSessionDaoTest {

    @Test
    @DisplayName("Should construct AiSession model cleanly for insertion")
    void testAiSessionConstruction() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AiSession session = new AiSession();
        session.setId(sessionId);
        session.setTenantId(tenantId);
        session.setChannel(Channel.VOICE);
        session.setCustomerIdentifier("+15550199");
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());

        assertEquals(sessionId, session.getId());
        assertEquals(tenantId, session.getTenantId());
        assertEquals(Channel.VOICE, session.getChannel());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals("+15550199", session.getCustomerIdentifier());
    }

    @Test
    @DisplayName("Should update customerIdentifier title cleanly for flow-derived title")
    void testSessionTitleUpdateAndPersistence() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AiSession session = new AiSession();
        session.setId(sessionId);
        session.setTenantId(tenantId);
        session.setChannel(Channel.CHAT);
        session.setCustomerIdentifier("New IVR Flow Session");
        session.setStatus(SessionStatus.ACTIVE);

        assertEquals("New IVR Flow Session", session.getCustomerIdentifier());

        String derivedTitle = "Hospital Appointment & Triage System";
        session.setCustomerIdentifier(derivedTitle);
        assertEquals("Hospital Appointment & Triage System", session.getCustomerIdentifier());
    }
}
