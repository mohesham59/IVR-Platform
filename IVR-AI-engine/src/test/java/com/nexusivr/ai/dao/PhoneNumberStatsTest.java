package com.nexusivr.ai.dao;

import com.nexusivr.ai.service.PhoneNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhoneNumberStatsTest {

    private PhoneNumberDao phoneNumberDao;
    private PhoneNumberService phoneNumberService;

    @BeforeEach
    public void setUp() {
        phoneNumberDao = new PhoneNumberDao();
        phoneNumberService = new PhoneNumberService(phoneNumberDao, null);
    }

    @Test
    public void testGetTodaysInboundCallsCountForEmptyTenant() {
        UUID mockTenantId = UUID.randomUUID();
        int count = phoneNumberDao.getTodaysInboundCallsCount(mockTenantId);
        assertEquals(0, count, "Tenant with no provisioned phone numbers must return 0 today's inbound calls");
    }

    @Test
    public void testGetPhoneNumberStatsStructure() {
        UUID mockTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> stats = phoneNumberService.getPhoneNumberStats(mockTenantId);

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalNumbers"));
        assertTrue(stats.containsKey("activeNumbers"));
        assertTrue(stats.containsKey("unassignedNumbers"));
        assertTrue(stats.containsKey("todaysInbound"));
    }
}
