package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.dao.DatabaseManager;

import java.lang.management.ManagementFactory;

import java.util.*;

public class SystemHealthService {

    private final ProviderManager providerManager;
    private final AsteriskAmiClient amiClient;

    public SystemHealthService(ProviderManager providerManager, AsteriskAmiClient amiClient) {
        this.providerManager = providerManager;
        this.amiClient = amiClient;
    }

    public SystemHealthService() {
        this(new ProviderManager(), AsteriskAmiClient.getInstance());
    }

    public Map<String, Object> getSystemHealth() {
        Map<String, Object> response = new LinkedHashMap<>();

        // 1. JVM & Memory Health
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("status", "HEALTHY");
        jvm.put("uptimeMs", uptimeMs);
        jvm.put("uptimeFormatted", formatUptime(uptimeMs));
        jvm.put("maxMemoryMb", maxMem / (1024 * 1024));
        jvm.put("totalMemoryMb", totalMem / (1024 * 1024));
        jvm.put("usedMemoryMb", usedMem / (1024 * 1024));
        jvm.put("freeMemoryMb", freeMem / (1024 * 1024));
        jvm.put("activeThreads", Thread.activeCount());
        jvm.put("availableProcessors", runtime.availableProcessors());

        // 2. Database Connection Pool Health
        Map<String, Object> db = DatabaseManager.getPoolStats();

        // 3. AI Providers Health
        Map<String, Map<String, Object>> aiProviders = providerManager.getProviderHealthOverview();

        // 4. Asterisk Telephony Health
        Map<String, Object> asterisk = amiClient.getAmiHealthStatus();

        // Overall Status calculation
        String overallStatus = "HEALTHY";
        boolean anyAiCircuitOpen = aiProviders.values().stream()
                .anyMatch(p -> "CIRCUIT_OPEN".equals(p.get("status")) || "UNHEALTHY".equals(p.get("status")));
        boolean dbOffline = "OFFLINE".equalsIgnoreCase((String) db.get("status"));

        if (dbOffline) {
            overallStatus = "CRITICAL";
        } else if (anyAiCircuitOpen || !"HEALTHY".equalsIgnoreCase((String) asterisk.get("status"))) {
            overallStatus = "DEGRADED";
        }

        response.put("overallStatus", overallStatus);
        response.put("jvm", jvm);
        response.put("database", db);
        response.put("aiProviders", aiProviders);
        response.put("asterisk", asterisk);
        response.put("timestamp", new Date().toString());

        return response;
    }

    private String formatUptime(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        long hr = min / 60;
        long days = hr / 24;

        if (days > 0) return String.format("%dd %dh %dm", days, hr % 24, min % 60);
        if (hr > 0) return String.format("%dh %dm", hr, min % 60);
        if (min > 0) return String.format("%dm %ds", min, sec % 60);
        return String.format("%ds", sec);
    }
}
