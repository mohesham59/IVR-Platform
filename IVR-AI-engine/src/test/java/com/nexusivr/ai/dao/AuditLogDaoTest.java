package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuditLogDaoTest {

    private AuditLogDao dao;

    @BeforeEach
    public void setUp() {
        dao = new AuditLogDao();
    }

    @Test
    public void testFindAuditLogsFallbackOrDb() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<AuditLog> logs = dao.findAuditLogs(tenantId, "COMPANY_CREATED", null, null, 0, 10);
        assertNotNull(logs);
    }

    @Test
    public void testCountAuditLogs() {
        int count = dao.countAuditLogs(null, null, null, null);
        assertTrue(count >= 0);
    }
}
