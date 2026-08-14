package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.DashboardDao;
import com.nexusivr.ai.dao.QueueDao;
import com.nexusivr.ai.dao.VoicePromptDao;
import com.nexusivr.ai.model.CallLog;
import com.nexusivr.ai.model.Queue;

import java.util.*;

public class DashboardService {

    private final DashboardDao dashboardDao;
    private final VoicePromptDao voicePromptDao;
    private final QueueDao queueDao;
    private final PhoneNumberService phoneNumberService;
    private final AsteriskAmiClient amiClient;
    private final com.nexusivr.ai.dao.AgentStateDao agentStateDao;

    public DashboardService(DashboardDao dashboardDao, VoicePromptDao voicePromptDao, QueueDao queueDao,
                            PhoneNumberService phoneNumberService, AsteriskAmiClient amiClient,
                            com.nexusivr.ai.dao.AgentStateDao agentStateDao) {
        this.dashboardDao = dashboardDao;
        this.voicePromptDao = voicePromptDao;
        this.queueDao = queueDao;
        this.phoneNumberService = phoneNumberService;
        this.amiClient = amiClient;
        this.agentStateDao = agentStateDao;
    }

    public DashboardService(DashboardDao dashboardDao, VoicePromptDao voicePromptDao, QueueDao queueDao,
                            PhoneNumberService phoneNumberService, AsteriskAmiClient amiClient) {
        this(dashboardDao, voicePromptDao, queueDao, phoneNumberService, amiClient, new com.nexusivr.ai.dao.AgentStateDao());
    }

    public DashboardService() {
        this(new DashboardDao(), new VoicePromptDao(), new QueueDao(), new PhoneNumberService(),
             AsteriskAmiClient.getInstance(), new com.nexusivr.ai.dao.AgentStateDao());
    }

    public Map<String, Object> getDashboardStats(UUID tenantId) {
        if (tenantId == null) return Collections.emptyMap();

        Map<String, Object> stats = dashboardDao.getAggregateStats(tenantId, null);

        // Additional counts
        int publishedCount = phoneNumberService.getPublishedFlows(tenantId).size();
        int voicePromptsCount = voicePromptDao.getVoicePromptsCount(tenantId);

        List<Queue> queues = queueDao.findByTenantId(tenantId);
        int queuesCount = queues.size();

        // Calculate active agents (available or in_call) for the tenant
        int activeAgentsCount = agentStateDao.getActiveAgentsCount(tenantId);

        stats.put("publishedIvrs", publishedCount);
        stats.put("voicePrompts", voicePromptsCount);
        stats.put("queues", queuesCount);
        stats.put("activeAgents", activeAgentsCount);

        return stats;
    }

    public List<Map<String, Object>> getCallVolume(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        return dashboardDao.getCallVolumeData(tenantId);
    }

    public List<Map<String, Object>> getCallDistribution(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        return dashboardDao.getCallDistributionData(tenantId);
    }

    public List<Map<String, Object>> getAgentPerformance(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        return dashboardDao.getAgentPerformanceData(tenantId);
    }

    public List<Map<String, Object>> getQueuePerformance(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        List<Queue> queues = queueDao.findByTenantId(tenantId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Queue q : queues) {
            AsteriskAmiClient.LiveQueueStats live = amiClient.getLiveStats(q.getName());
            Map<String, Object> item = new HashMap<>();
            item.put("queue", q.getName());
            item.put("waiting", live != null ? live.waitingCalls : 0);
            item.put("avgWait", live != null ? live.avgWaitSeconds : 0);
            item.put("status", q.getStatus());
            result.add(item);
        }

        return result;
    }

    public List<CallLog> getRecentCalls(UUID tenantId, int limit) {
        if (tenantId == null) return Collections.emptyList();
        return dashboardDao.getRecentCalls(tenantId, limit);
    }

    public String exportRecentCallsCsv(UUID tenantId) {
        if (tenantId == null) return "";
        return dashboardDao.generateRecentCallsCsv(tenantId);
    }

    public int getActiveCallsCount(UUID tenantId) {
        if (tenantId == null) return 0;
        int activeCalls = 0;
        List<Queue> queues = queueDao.findByTenantId(tenantId);
        for (Queue q : queues) {
            AsteriskAmiClient.LiveQueueStats live = amiClient.getLiveStats(q.getName());
            if (live != null) {
                activeCalls += live.waitingCalls;
            }
        }
        return activeCalls;
    }
}
