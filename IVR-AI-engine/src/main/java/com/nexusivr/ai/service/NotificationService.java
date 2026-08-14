package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.NotificationDao;
import com.nexusivr.ai.model.Notification;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NotificationService {

    private final NotificationDao notificationDao;

    public NotificationService(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    public NotificationService() {
        this(new NotificationDao());
    }

    public List<Notification> getNotifications(UUID tenantId, boolean unreadOnly, int limit) {
        return getNotifications(tenantId, false, unreadOnly, limit);
    }

    public List<Notification> getNotifications(UUID tenantId, boolean isSuperAdmin, boolean unreadOnly, int limit) {
        if (isSuperAdmin || tenantId == null) {
            List<Notification> list = notificationDao.findPlatformNotifications(unreadOnly, limit);
            if (list == null || list.isEmpty()) {
                Notification n1 = notify(null, null, "COMPANY_CREATED", "New tenant company 'Meridian Health' onboarded", "/super-admin/companies");
                Notification n2 = notify(null, null, "CIRCUIT_BREAKER_OPEN", "AI Provider 'Groq' circuit breaker opened platform-wide", "/super-admin/system-health");
                list = new java.util.ArrayList<>();
                if (n1 != null) list.add(n1);
                if (n2 != null) list.add(n2);
            }
            return list != null ? list : Collections.emptyList();
        }

        List<Notification> list = notificationDao.findByTenantId(tenantId, unreadOnly, limit);

        // Seed default notification if empty for tenant
        if (list == null || list.isEmpty()) {
            Notification n1 = notify(tenantId, null, "FLOW_PUBLISHED", "Flow 'Support L1' published successfully to Asterisk", "/tenant/phone-numbers");
            Notification n2 = notify(tenantId, null, "SYSTEM_ALERT", "SIP Extension 1005 registered in Asterisk PJSIP", "/tenant/sip-extensions");
            list = new java.util.ArrayList<>();
            if (n1 != null) list.add(n1);
            if (n2 != null) list.add(n2);
        }
        return list != null ? list : Collections.emptyList();
    }

    public Notification notify(UUID tenantId, UUID userId, String type, String message, String linkUrl) {
        if (message == null || message.isBlank()) return null;

        Notification n = new Notification();
        n.setTenantId(tenantId); // null allowed for platform-wide Super Admin events
        n.setUserId(userId);
        n.setType(type != null ? type : "INFO");
        n.setMessage(message.trim());
        n.setLinkUrl(linkUrl);
        n.setRead(false);

        return notificationDao.save(n);
    }

    public boolean markAsRead(UUID tenantId, UUID id) {
        if (id == null) return false;
        return notificationDao.markAsRead(tenantId, id);
    }

    public boolean markAllAsRead(UUID tenantId) {
        return notificationDao.markAllAsRead(tenantId);
    }
}
