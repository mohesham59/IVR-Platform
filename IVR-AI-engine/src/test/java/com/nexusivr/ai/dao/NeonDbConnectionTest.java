package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.model.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.UUID;

public class NeonDbConnectionTest {

    private static final String EXPECTED_HOST = "ep-empty-cell-ay0ibimz-pooler.c-5.us-east-2.aws.neon.tech";

    @Test
    public void testDatabaseConnectionAndRoundTrip() throws Exception {
        // 1. Verify Connection and Target Host
        try (Connection conn = DatabaseManager.getConnection()) {
            assertNotNull(conn, "Connection should not be null");
            DatabaseMetaData metaData = conn.getMetaData();
            String url = metaData.getURL();
            System.out.println("[NeonDbConnectionTest] Connected Database URL: " + url);
            assertTrue(url.contains(EXPECTED_HOST), "Connection URL must match NEW Neon host: " + EXPECTED_HOST);
        }

        // 2. Perform Save and Retrieve Round Trip on New Database
        AiSessionDao sessionDao = new AiSessionDao();
        MessageDao messageDao = new MessageDao();

        UUID tenantId = UUID.randomUUID();
        String extRef = "test-session-" + UUID.randomUUID();

        AiSession session = new AiSession();
        session.setTenantId(tenantId);
        session.setChannel(Channel.CHAT);
        session.setExternalReferenceId(extRef);
        session.setCustomerIdentifier("customer-unit-test");
        session.setStatus(SessionStatus.ACTIVE);

        AiSession createdSession = sessionDao.create(session);
        assertNotNull(createdSession, "Created session should not be null");
        assertNotNull(createdSession.getId(), "Session ID should not be null");

        // Save a test user message
        Message msg = new Message();
        msg.setSessionId(createdSession.getId());
        msg.setTenantId(tenantId);
        msg.setTurnNumber(1);
        msg.setRole(MessageRole.USER);
        msg.setContent("Hello, this is a test message to verify Neon DB round trip.");
        msg.setModelUsed("test-model");
        msg.setTokensInput(10);
        msg.setTokensOutput(20);

        Message savedMsg = messageDao.save(msg);
        assertNotNull(savedMsg, "Saved message should not be null");
        assertEquals(MessageRole.USER, savedMsg.getRole());

        // Retrieve session history
        List<Message> history = messageDao.findBySessionId(createdSession.getId(), tenantId);
        assertNotNull(history, "Retrieved history list should not be null");
        assertFalse(history.isEmpty(), "History should contain the saved test message");
        assertEquals(1, history.size());
        assertEquals(msg.getContent(), history.get(0).getContent());
        System.out.println("[NeonDbConnectionTest] Successfully performed save and retrieve round trip on session: " + createdSession.getId());
    }
}
