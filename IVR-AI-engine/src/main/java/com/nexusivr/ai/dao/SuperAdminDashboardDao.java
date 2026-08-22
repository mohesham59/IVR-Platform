package com.nexusivr.ai.dao;

import java.sql.*;
import java.util.*;

public class SuperAdminDashboardDao {

    public Map<String, Object> getPlatformStats() {
        Map<String, Object> stats = new HashMap<>();

        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM tenants) AS total_companies, " +
                "(SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE') AS active_companies, " +
                "(SELECT COUNT(*) FROM users) AS total_users, " +
                "(SELECT COUNT(*) FROM phone_numbers WHERE assigned_flow_id IS NOT NULL AND assigned_flow_id != '') AS published_ivrs, " +
                "(SELECT COUNT(*) FROM ai_messages WHERE created_at >= CURRENT_DATE) AS ai_requests_today";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                stats.put("totalCompanies", rs.getInt("total_companies"));
                stats.put("activeCompanies", rs.getInt("active_companies"));
                stats.put("totalUsers", rs.getInt("total_users"));
                stats.put("publishedIvrs", rs.getInt("published_ivrs"));
                stats.put("aiRequestsToday", rs.getInt("ai_requests_today"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching platform stats: " + e.getMessage());
            stats.put("totalCompanies", 0);
            stats.put("activeCompanies", 0);
            stats.put("totalUsers", 0);
            stats.put("publishedIvrs", 0);
            stats.put("aiRequestsToday", 0);
        }

        return stats;
    }

    public List<Map<String, Object>> getMonthlyCompanyGrowth() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT to_char(created_at, 'Mon') AS m, COUNT(*) AS count, MIN(created_at) AS min_date " +
                     "FROM tenants " +
                     "GROUP BY m ORDER BY min_date ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("month", rs.getString("m"));
                map.put("companies", rs.getInt("count"));
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching monthly company growth: " + e.getMessage());
        }

        if (list.isEmpty()) {
            list.add(Map.of("month", "Aug", "companies", 1));
        }
        return list;
    }

    public List<Map<String, Object>> getAiRequestsTodayChart() {
        return getAiRequestsTodayChart(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    public List<Map<String, Object>> getAiRequestsTodayChart(int currentHour) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT (EXTRACT(HOUR FROM created_at)::int / 4) * 4 AS hr_bucket, COUNT(*) AS requests " +
                     "FROM ai_messages WHERE created_at >= CURRENT_DATE AND created_at <= NOW() " +
                     "GROUP BY hr_bucket ORDER BY hr_bucket ASC";

        Map<Integer, Integer> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                map.put(rs.getInt("hr_bucket"), rs.getInt("requests"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching AI requests today chart: " + e.getMessage());
        }

        for (int h = 0; h <= 20; h += 4) {
            Map<String, Object> point = new HashMap<>();
            point.put("hour", String.format("%02d:00", h));

            if (h > currentHour) {
                // Strictly zero for future hour buckets
                point.put("requests", 0);
            } else {
                point.put("requests", map.getOrDefault(h, 0));
            }
            list.add(point);
        }
        return list;
    }

    public List<Map<String, Object>> getCallsPerDayChart() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT to_char(start_time, 'Dy') AS d, COUNT(*) AS total_calls, " +
                     "COUNT(CASE WHEN last_node IS NOT NULL THEN 1 END) AS ai_handled, " +
                     "MIN(start_time) AS min_date " +
                     "FROM call_logs WHERE start_time >= (CURRENT_DATE - INTERVAL '7 days') " +
                     "GROUP BY d ORDER BY min_date ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("day", rs.getString("d"));
                map.put("calls", rs.getInt("total_calls"));
                map.put("ai", rs.getInt("ai_handled"));
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching calls per day chart: " + e.getMessage());
        }

        return list;
    }

    public List<Map<String, Object>> getLatestCompanies() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT t.id, t.display_name, t.status, t.created_at, " +
                     "(SELECT COUNT(*) FROM users u WHERE u.active_tenant_id = t.id) AS user_count " +
                     "FROM tenants t ORDER BY t.created_at DESC LIMIT 5";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("display_name"));
                map.put("plan", "Enterprise");
                map.put("users", rs.getInt("user_count"));
                map.put("status", rs.getString("status"));
                Timestamp ts = rs.getTimestamp("created_at");
                map.put("joined", ts != null ? ts.toString().substring(0, 10) : "Today");
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching latest companies: " + e.getMessage());
        }
        return list;
    }

    public List<Map<String, Object>> getRecentActivityFeed() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT message, link_url, created_at, type FROM notifications ORDER BY created_at DESC LIMIT 5";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("action", rs.getString("type") != null ? rs.getString("type").replace("_", " ") : "System Event");
                map.put("subject", rs.getString("message"));
                Timestamp ts = rs.getTimestamp("created_at");
                map.put("time", ts != null ? ts.toString().substring(11, 16) : "just now");
                map.put("type", "info");
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching recent activity feed: " + e.getMessage());
        }

        return list;
    }

    public List<Map<String, Object>> getLatestUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.username, u.email, u.created_at, COALESCE(t.display_name, 'Platform SuperAdmin') AS company " +
                     "FROM users u " +
                     "LEFT JOIN tenants t ON u.active_tenant_id = t.id " +
                     "ORDER BY u.created_at DESC LIMIT 5";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("username"));
                map.put("email", rs.getString("email"));
                map.put("company", rs.getString("company"));
                Timestamp ts = rs.getTimestamp("created_at");
                map.put("joined", ts != null ? ts.toString().substring(11, 16) : "Today");
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching latest users: " + e.getMessage());
        }
        return list;
    }
}
