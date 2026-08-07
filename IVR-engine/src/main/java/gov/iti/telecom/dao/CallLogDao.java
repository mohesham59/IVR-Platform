package gov.iti.telecom.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class CallLogDao {

    // Simple JDBC helper based on the existing pattern in SchoolApiServer
    // In production, you would move DB_URL to a config file.
    private static final String DB_URL = System.getProperty("DB_URL", "jdbc:postgresql://ep-empty-cell-ay0ibimz-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require");
    private static final String DB_USER = System.getProperty("DB_USER", "neondb_owner");
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD", "npg_6OuHh2PKRUWk");

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void saveNewCallLog(String sessionId, UUID tenantId, String callerId, String scenarioName) {
        String sql = "INSERT INTO call_logs (session_id, tenant_id, caller_id, scenario_name, status, start_time) " +
                     "VALUES (?, ?, ?, ?, 'IN_PROGRESS', now())";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setObject(2, tenantId);
            ps.setString(3, callerId);
            ps.setString(4, scenarioName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[CallLogDao] Error saving new call log: " + e.getMessage());
        }
    }

    public static void updateCallStatus(String sessionId, String status, String lastNode) {
        String sql = "UPDATE call_logs SET status = ?, last_node = ?, end_time = now(), " +
                     "duration = EXTRACT(EPOCH FROM (now() - start_time)) " +
                     "WHERE session_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, lastNode);
            ps.setString(3, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[CallLogDao] Error updating call status: " + e.getMessage());
        }
    }

    public static void logMenuSelection(String sessionId, String menuNodeName) {
        String sql = "INSERT INTO call_events (session_id, event_type, node_name) VALUES (?, 'MENU_SELECTION', ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, menuNodeName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[CallLogDao] Error logging menu selection: " + e.getMessage());
        }
    }
}
