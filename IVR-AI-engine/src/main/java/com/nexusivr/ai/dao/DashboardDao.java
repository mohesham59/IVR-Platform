package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.CallLog;

import java.sql.*;
import java.util.*;

public class DashboardDao {

    public Map<String, Object> getAggregateStats(UUID tenantId, String dateStr) {
        Map<String, Object> stats = new HashMap<>();

        // 1. Today's stats
        String todaySql = "SELECT COUNT(*) AS total_calls, " +
                          "COUNT(CASE WHEN status = 'ANSWERED' THEN 1 END) AS answered_calls, " +
                          "COUNT(CASE WHEN status IN ('MISSED', 'BUSY', 'FAILED') THEN 1 END) AS missed_calls, " +
                          "COALESCE(AVG(CASE WHEN status = 'ANSWERED' THEN duration END), 0) AS avg_duration " +
                          "FROM call_logs WHERE tenant_id = ? AND start_time >= CURRENT_DATE";

        // 2. Yesterday's baseline stats for trend calculation
        String yesterdaySql = "SELECT COUNT(*) AS total_calls, " +
                              "COUNT(CASE WHEN status = 'ANSWERED' THEN 1 END) AS answered_calls, " +
                              "COUNT(CASE WHEN status IN ('MISSED', 'BUSY', 'FAILED') THEN 1 END) AS missed_calls, " +
                              "COALESCE(AVG(CASE WHEN status = 'ANSWERED' THEN duration END), 0) AS avg_duration " +
                              "FROM call_logs WHERE tenant_id = ? AND start_time >= (CURRENT_DATE - INTERVAL '1 day') AND start_time < CURRENT_DATE";

        try (Connection conn = DatabaseManager.getConnection()) {
            int todayTotal = 0, todayAnswered = 0, todayMissed = 0, todayAvgDuration = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(todaySql)) {
                pstmt.setObject(1, tenantId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        todayTotal = rs.getInt("total_calls");
                        todayAnswered = rs.getInt("answered_calls");
                        todayMissed = rs.getInt("missed_calls");
                        todayAvgDuration = (int) Math.round(rs.getDouble("avg_duration"));
                    }
                }
            }

            int yestTotal = 0, yestAnswered = 0, yestMissed = 0, yestAvgDuration = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(yesterdaySql)) {
                pstmt.setObject(1, tenantId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        yestTotal = rs.getInt("total_calls");
                        yestAnswered = rs.getInt("answered_calls");
                        yestMissed = rs.getInt("missed_calls");
                        yestAvgDuration = (int) Math.round(rs.getDouble("avg_duration"));
                    }
                }
            }

            stats.put("totalCalls", todayTotal);
            stats.put("answeredCalls", todayAnswered);
            stats.put("missedCalls", todayMissed);
            stats.put("avgDurationSeconds", todayAvgDuration);

            stats.put("totalCallsTrend", calculateTrend(todayTotal, yestTotal));
            stats.put("answeredTrend", calculateTrend(todayAnswered, yestAnswered));
            stats.put("missedTrend", calculateTrend(todayMissed, yestMissed));
            stats.put("avgDurationTrend", calculateTrend(todayAvgDuration, yestAvgDuration));

        } catch (SQLException e) {
            System.err.println("Error fetching aggregate stats for tenant " + tenantId + ": " + e.getMessage());
        }

        return stats;
    }

    public List<Map<String, Object>> getCallVolumeData(UUID tenantId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT EXTRACT(HOUR FROM start_time) AS hr, " +
                     "COUNT(*) AS total, " +
                     "COUNT(CASE WHEN caller_id LIKE '+%' THEN 1 END) AS inbound, " +
                     "COUNT(CASE WHEN caller_id NOT LIKE '+%' THEN 1 END) AS outbound " +
                     "FROM call_logs WHERE tenant_id = ? AND start_time >= CURRENT_DATE " +
                     "GROUP BY hr ORDER BY hr ASC";

        Map<Integer, Map<String, Object>> hourlyMap = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int hr = rs.getInt("hr");
                    Map<String, Object> map = new HashMap<>();
                    map.put("time", String.format("%02d:00", hr));
                    map.put("inbound", rs.getInt("inbound"));
                    map.put("outbound", rs.getInt("outbound"));
                    hourlyMap.put(hr, map);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching call volume data: " + e.getMessage());
        }

        // Ensure smooth 24-hour baseline
        for (int h = 0; h < 24; h++) {
            if (hourlyMap.containsKey(h)) {
                list.add(hourlyMap.get(h));
            } else {
                Map<String, Object> defaultHr = new HashMap<>();
                defaultHr.put("time", String.format("%02d:00", h));
                defaultHr.put("inbound", 0);
                defaultHr.put("outbound", 0);
                list.add(defaultHr);
            }
        }
        return list;
    }

    public List<Map<String, Object>> getCallDistributionData(UUID tenantId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT COALESCE(scenario_name, 'General Support') AS dept, COUNT(*) AS count " +
                     "FROM call_logs WHERE tenant_id = ? " +
                     "GROUP BY dept ORDER BY count DESC LIMIT 5";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", rs.getString("dept"));
                    map.put("value", rs.getInt("count"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching call distribution data: " + e.getMessage());
        }

        return list;
    }

    public List<Map<String, Object>> getAgentPerformanceData(UUID tenantId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.username AS agent_name, COUNT(cl.id) AS calls_handled " +
                     "FROM users u " +
                     "LEFT JOIN call_logs cl ON cl.tenant_id = ? AND cl.last_node LIKE '%' || u.username || '%' " +
                     "WHERE u.active_tenant_id = ? OR u.id = (SELECT owner_user_id FROM tenants WHERE id = ?) " +
                     "GROUP BY u.username ORDER BY calls_handled DESC LIMIT 5";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, tenantId);
            pstmt.setObject(3, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("agent", rs.getString("agent_name"));
                    map.put("calls", rs.getInt("calls_handled"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching agent performance data: " + e.getMessage());
        }

        return list;
    }

    public List<CallLog> getRecentCalls(UUID tenantId, int limit) {
        List<CallLog> list = new ArrayList<>();
        String sql = "SELECT id, session_id, tenant_id, caller_id, scenario_name, status, start_time, end_time, duration, last_node " +
                     "FROM call_logs WHERE tenant_id = ? ORDER BY start_time DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setInt(2, limit > 0 ? limit : 10);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    CallLog cl = new CallLog();
                    cl.setId((UUID) rs.getObject("id"));
                    cl.setSessionId(rs.getString("session_id"));
                    cl.setTenantId((UUID) rs.getObject("tenant_id"));
                    cl.setCallerId(rs.getString("caller_id"));
                    cl.setScenarioName(rs.getString("scenario_name"));
                    cl.setStatus(rs.getString("status"));
                    cl.setStartTime(rs.getTimestamp("start_time"));
                    cl.setEndTime(rs.getTimestamp("end_time"));
                    cl.setDuration(rs.getInt("duration"));
                    cl.setLastNode(rs.getString("last_node"));
                    list.add(cl);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching recent calls for tenant " + tenantId + ": " + e.getMessage());
        }
        return list;
    }

    public String generateRecentCallsCsv(UUID tenantId) {
        StringBuilder csv = new StringBuilder();
        csv.append("Call ID,Session ID,Caller,Scenario/Queue,Last Node,Status,Duration (sec),Start Time\n");

        List<CallLog> calls = getRecentCalls(tenantId, 100);
        for (CallLog c : calls) {
            csv.append(String.format("%s,%s,\"%s\",\"%s\",\"%s\",%s,%d,\"%s\"\n",
                    c.getId(),
                    c.getSessionId(),
                    c.getCallerId(),
                    c.getScenarioName(),
                    c.getLastNode() != null ? c.getLastNode() : "",
                    c.getStatus(),
                    c.getDuration(),
                    c.getStartTime() != null ? c.getStartTime().toString() : ""
            ));
        }
        return csv.toString();
    }

    private double calculateTrend(double todayVal, double yestVal) {
        if (yestVal == 0) return 0.0;
        return Math.round(((todayVal - yestVal) / yestVal) * 100.0 * 10.0) / 10.0;
    }
}
