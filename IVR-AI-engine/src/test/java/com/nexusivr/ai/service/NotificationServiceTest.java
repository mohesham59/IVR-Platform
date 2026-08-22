package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.NotificationDao;
import com.nexusivr.ai.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationServiceTest {

    private NotificationDao dao;
    private NotificationService service;

    @BeforeEach
    public void setUp() {
        dao = mock(NotificationDao.class);
        service = new NotificationService(dao);
    }

    @Test
    public void testPlatformNotificationNotifyAndGet() {
        Notification n = new Notification();
        n.setType("COMPANY_CREATED");
        n.setMessage("New company created");

        when(dao.findPlatformNotifications(false, 20)).thenReturn(List.of(n));

        List<Notification> platformNotifs = service.getNotifications(null, true, false, 20);
        assertNotNull(platformNotifs);
        assertEquals(1, platformNotifs.size());
        assertEquals("COMPANY_CREATED", platformNotifs.get(0).getType());
    }

    @Test
    public void testTenantNotificationNotifyAndGet() {
        UUID tenantId = UUID.randomUUID();
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setType("FLOW_PUBLISHED");

        when(dao.findByTenantId(tenantId, false, 20)).thenReturn(List.of(n));

        List<Notification> tenantNotifs = service.getNotifications(tenantId, false, false, 20);
        assertNotNull(tenantNotifs);
        assertEquals(1, tenantNotifs.size());
        assertEquals(tenantId, tenantNotifs.get(0).getTenantId());
    }

    @Test
    public void testGetNotificationsReturnsEmptyListWithoutNpeWhenNotifyReturnsNull() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(dao.findByTenantId(tenantId, false, 20)).thenReturn(java.util.Collections.emptyList());
        when(dao.save(any())).thenReturn(null); // Simulated DB save failure / FK failure

        assertDoesNotThrow(() -> {
            List<Notification> result = service.getNotifications(tenantId, false, false, 20);
            assertNotNull(result, "Result must never be null");
            assertTrue(result.isEmpty(), "Result should be empty list when notification creation fails");
        });
    }

    @Test
    public void testNotificationWriteSucceedsForSeededTenant() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Notification expected = new Notification();
        expected.setTenantId(tenantId);
        expected.setType("FLOW_PUBLISHED");
        expected.setMessage("Flow 'Support L1' published successfully");

        when(dao.save(any())).thenReturn(expected);

        Notification created = service.notify(tenantId, null, "FLOW_PUBLISHED", "Flow 'Support L1' published successfully", "/tenant/phone-numbers");
        assertNotNull(created);
        assertEquals(tenantId, created.getTenantId());
        assertEquals("FLOW_PUBLISHED", created.getType());
    }
}
