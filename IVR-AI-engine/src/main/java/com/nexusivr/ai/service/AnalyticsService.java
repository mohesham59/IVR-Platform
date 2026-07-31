package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.dto.AnalyticsResponse;
import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.AiSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete service for tenant-scoped MVP analytics and metrics reporting.
 */
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final AiSessionDao sessionDao;
    private final MessageDao messageDao;

    public AnalyticsService(AiSessionDao sessionDao, MessageDao messageDao) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao must not be null");
        this.messageDao = Objects.requireNonNull(messageDao, "messageDao must not be null");
    }

    public long getActiveSessions(UUID tenantId) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        try {
            List<AiSession> activeSessions = sessionDao.findActiveSessions(tenantId);
            return activeSessions.size();
        } catch (Exception e) {
            logger.warn("DB offline retrieving active session count for tenant {}", tenantId);
            return 0;
        }
    }

    public AnalyticsResponse getTenantAnalytics(UUID tenantId) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        try {
            List<AiSession> activeSessions = sessionDao.findActiveSessions(tenantId);
            long activeCount = activeSessions.size();
            long totalSessions = activeCount;
            long totalMessages = 0;

            for (AiSession s : activeSessions) {
                totalMessages += messageDao.countMessages(s.getId(), tenantId);
            }

            return new AnalyticsResponse(tenantId, totalSessions, activeCount, totalMessages);
        } catch (Exception e) {
            logger.warn("DB offline building analytics response for tenant {}, returning degraded response", tenantId);
            return new AnalyticsResponse(tenantId, 0, 0, 0, true);
        }
    }
}
