package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AgentStateRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentStateDao {

    public AgentStateDao() {}

    public AgentStateRecord getAgentState(UUID agentId) {
        String sql = "SELECT ast.id, ast.agent_id, ast.current_state, ast.state_changed_at, ast.current_queue_id, u.username, u.email " +
                     "FROM agent_states ast JOIN users u ON ast.agent_id = u.id WHERE ast.agent_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, agentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    AgentStateRecord r = new AgentStateRecord();
                    r.setId((UUID) rs.getObject("id"));
                    r.setAgentId((UUID) rs.getObject("agent_id"));
                    r.setCurrentState(rs.getString("current_state"));
                    r.setStateChangedAt(rs.getTimestamp("state_changed_at"));
                    r.setCurrentQueueId((UUID) rs.getObject("current_queue_id"));
                    r.setUsername(rs.getString("username"));
                    r.setEmail(rs.getString("email"));
                    return r;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching agent state: " + e.getMessage());
        }
        return null;
    }

    public boolean updateAgentState(UUID agentId, String state, UUID currentQueueId) {
        String sql = "INSERT INTO agent_states (agent_id, current_state, state_changed_at, current_queue_id) " +
                     "VALUES (?, ?, now(), ?) " +
                     "ON CONFLICT (agent_id) DO UPDATE SET current_state = EXCLUDED.current_state, " +
                     "state_changed_at = now(), current_queue_id = EXCLUDED.current_queue_id";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, agentId);
            pstmt.setString(2, state);
            pstmt.setObject(3, currentQueueId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating agent state: " + e.getMessage());
            throw new RuntimeException("Database error updating agent state: " + e.getMessage(), e);
        }
    }

    public List<AgentStateRecord> getTenantAgentsWithState(UUID tenantId) {
        List<AgentStateRecord> list = new ArrayList<>();
        String sql = "SELECT u.id AS agent_id, u.username, u.email, COALESCE(ast.current_state, 'available') AS current_state, " +
                     "ast.id, ast.state_changed_at, ast.current_queue_id " +
                     "FROM users u " +
                     "LEFT JOIN agent_states ast ON u.id = ast.agent_id " +
                     "WHERE u.active_tenant_id = ? OR u.id = (SELECT owner_user_id FROM tenants WHERE id = ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AgentStateRecord r = new AgentStateRecord();
                    r.setAgentId((UUID) rs.getObject("agent_id"));
                    r.setUsername(rs.getString("username"));
                    r.setEmail(rs.getString("email"));
                    r.setCurrentState(rs.getString("current_state"));
                    r.setId((UUID) rs.getObject("id"));
                    r.setStateChangedAt(rs.getTimestamp("state_changed_at"));
                    r.setCurrentQueueId((UUID) rs.getObject("current_queue_id"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching tenant agents: " + e.getMessage());
        }
        return list;
    }

    public boolean cleanUpAgentsForDeletedQueue(UUID queueId) {
        String sql = "UPDATE agent_states SET current_queue_id = NULL, current_state = 'offline', state_changed_at = now() WHERE current_queue_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, queueId);
            return pstmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("Error cleaning up agent states for deleted queue " + queueId + ": " + e.getMessage());
            return false;
        }
    }

    public int getActiveAgentsCount(UUID tenantId) {
        String sql = "SELECT COUNT(*) FROM agent_states ast JOIN users u ON ast.agent_id = u.id " +
                     "WHERE (u.active_tenant_id = ? OR u.id = (SELECT owner_user_id FROM tenants WHERE id = ?)) " +
                     "AND ast.current_state IN ('available', 'in_call')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting active agents for tenant " + tenantId + ": " + e.getMessage());
        }
        return 0;
    }
}
