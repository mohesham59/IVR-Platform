package com.nexusivr.ai.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SuperAdminDashboardDaoTest {

    private SuperAdminDashboardDao dao;

    @BeforeEach
    public void setUp() {
        dao = new SuperAdminDashboardDao();
    }

    @Test
    public void testAiRequestsTodayChartFutureHoursAreZero() {
        int simulatedHour = 8; // Simulated 08:15 AM
        List<Map<String, Object>> chartPoints = dao.getAiRequestsTodayChart(simulatedHour);

        assertNotNull(chartPoints);
        assertEquals(6, chartPoints.size()); // 00:00, 04:00, 08:00, 12:00, 16:00, 20:00

        for (Map<String, Object> point : chartPoints) {
            String hourStr = (String) point.get("hour");
            int hour = Integer.parseInt(hourStr.split(":")[0]);
            int requests = (Integer) point.get("requests");

            if (hour > simulatedHour) {
                assertEquals(0, requests, "Future hour bucket " + hourStr + " must be strictly 0 requests");
            }
        }
    }

    @Test
    public void testPlatformStatsStructure() {
        Map<String, Object> stats = dao.getPlatformStats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalCompanies"));
        assertTrue(stats.containsKey("activeCompanies"));
        assertTrue(stats.containsKey("totalUsers"));
        assertTrue(stats.containsKey("publishedIvrs"));
        assertTrue(stats.containsKey("aiRequestsToday"));
    }
}
