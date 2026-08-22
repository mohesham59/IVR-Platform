package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.QueueDao;
import com.nexusivr.ai.dao.SuperAdminDashboardDao;
import com.nexusivr.ai.model.Queue;

import java.util.*;

public class SuperAdminDashboardService {

    private final SuperAdminDashboardDao dao;
    private final QueueDao queueDao;
    private final AsteriskAmiClient amiClient;

    public SuperAdminDashboardService(SuperAdminDashboardDao dao, QueueDao queueDao, AsteriskAmiClient amiClient) {
        this.dao = dao;
        this.queueDao = queueDao;
        this.amiClient = amiClient;
    }

    public SuperAdminDashboardService() {
        this(new SuperAdminDashboardDao(), new QueueDao(), AsteriskAmiClient.getInstance());
    }

    public Map<String, Object> getPlatformStats() {
        Map<String, Object> stats = dao.getPlatformStats();

        // Cross-tenant live active calls count via shared AsteriskAmiClient
        int activeCalls = 0;
        List<Queue> allQueues = queueDao.findAllQueues();

        // Refresh live queue stats from Asterisk CLI
        amiClient.refreshQueueStatsFromCli();

        for (Queue q : allQueues) {
            AsteriskAmiClient.LiveQueueStats live = amiClient.getLiveStats(q.getName());
            if (live != null) {
                activeCalls += live.waitingCalls;
            }
        }

        stats.put("activeCalls", activeCalls);
        return stats;
    }

    public List<Map<String, Object>> getMonthlyCompanyGrowth() {
        return dao.getMonthlyCompanyGrowth();
    }

    public List<Map<String, Object>> getAiRequestsTodayChart() {
        return dao.getAiRequestsTodayChart();
    }

    public List<Map<String, Object>> getCallsPerDayChart() {
        return dao.getCallsPerDayChart();
    }

    public List<Map<String, Object>> getLatestCompanies() {
        return dao.getLatestCompanies();
    }

    public List<Map<String, Object>> getRecentActivityFeed() {
        return dao.getRecentActivityFeed();
    }

    public List<Map<String, Object>> getLatestUsers() {
        return dao.getLatestUsers();
    }
}
