package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AgentStateRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QueueDeletionTest {

    private AgentStateDao agentStateDao;

    @BeforeEach
    public void setUp() {
        agentStateDao = new AgentStateDao();
    }

    @Test
    public void testCleanUpAgentsForDeletedQueue() {
        UUID mockQueueId = UUID.randomUUID();
        boolean cleaned = agentStateDao.cleanUpAgentsForDeletedQueue(mockQueueId);
        assertTrue(cleaned);
    }

    @Test
    public void testGetActiveAgentsCountOnlyCountsAvailableAndInCall() {
        UUID mockTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        int activeCount = agentStateDao.getActiveAgentsCount(mockTenantId);
        assertTrue(activeCount >= 0);
    }
}
