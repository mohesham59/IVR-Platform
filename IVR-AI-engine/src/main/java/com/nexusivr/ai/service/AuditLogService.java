package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.AuditLogDao;
import com.nexusivr.ai.model.AuditLog;

import java.sql.Timestamp;
import java.util.*;

public class AuditLogService {

    private final AuditLogDao dao;

    public AuditLogService(AuditLogDao dao) {
        this.dao = dao;
    }

    public AuditLogService() {
        this(new AuditLogDao());
    }

    public AuditLog logEvent(UUID tenantId, UUID actorUserId, String actorEmail, String actionType,
                             String targetEntityType, String targetEntityId, String detailsJson, String ipAddress) {
        return dao.logEvent(tenantId, actorUserId, actorEmail, actionType, targetEntityType, targetEntityId, detailsJson, ipAddress);
    }

    public Map<String, Object> getPaginatedAuditLogs(UUID tenantId, String actionType, Timestamp dateFrom, Timestamp dateTo, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));
        int offset = (p - 1) * size;

        List<AuditLog> logs = dao.findAuditLogs(tenantId, actionType, dateFrom, dateTo, offset, size);
        int total = dao.countAuditLogs(tenantId, actionType, dateFrom, dateTo);
        int totalPages = (int) Math.ceil((double) total / size);

        Map<String, Object> result = new HashMap<>();
        result.put("items", logs);
        result.put("total", total);
        result.put("page", p);
        result.put("pageSize", size);
        result.put("totalPages", totalPages);

        return result;
    }
}
