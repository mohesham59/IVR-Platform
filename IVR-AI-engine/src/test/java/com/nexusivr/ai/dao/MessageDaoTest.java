package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageDao Integration Preparation Test")
public class MessageDaoTest {

    @Test
    @DisplayName("Should construct Message model with valid fields")
    void testMessageConstruction() {
        UUID msgId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Message msg = new Message(msgId, sessionId, tenantId, 1, MessageRole.USER,
                "Hello AI Assistant", "llama-3.3-70b-versatile", 15, 20, "{}", null);

        assertEquals(msgId, msg.getId());
        assertEquals(sessionId, msg.getSessionId());
        assertEquals(tenantId, msg.getTenantId());
        assertEquals(1, msg.getTurnNumber());
        assertEquals(MessageRole.USER, msg.getRole());
        assertEquals("Hello AI Assistant", msg.getContent());
    }
}
