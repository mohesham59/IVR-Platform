package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.Queue;
import com.nexusivr.ai.model.QueueMember;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QueueDao {

    public QueueDao() {}

    public List<Queue> findByTenantId(UUID tenantId) {
        List<Queue> queues = new ArrayList<>();
        String sql = "SELECT id, tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, business_hours, status, created_at, updated_at " +
                     "FROM queues WHERE tenant_id = ? ORDER BY created_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    queues.add(mapResultSetToQueue(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching queues for tenant " + tenantId + ": " + e.getMessage());
        }
        return queues;
    }

    public List<Queue> findAllQueues() {
        List<Queue> queues = new ArrayList<>();
        String sql = "SELECT id, tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, business_hours, status, created_at, updated_at " +
                     "FROM queues ORDER BY created_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                queues.add(mapResultSetToQueue(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all queues: " + e.getMessage());
        }
        return queues;
    }

    public Queue findById(UUID tenantId, UUID id) {
        String sql = "SELECT id, tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, business_hours, status, created_at, updated_at " +
                     "FROM queues WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToQueue(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching queue by ID " + id + ": " + e.getMessage());
        }
        return null;
    }

    public Queue save(Queue queue) {
        String sql = "INSERT INTO queues (tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, business_hours, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id, tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, business_hours, status, created_at, updated_at";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, queue.getTenantId());
            pstmt.setString(2, queue.getName());
            pstmt.setString(3, queue.getStrategy() != null ? queue.getStrategy() : "round_robin");
            pstmt.setInt(4, queue.getWrapUpTimeSeconds() > 0 ? queue.getWrapUpTimeSeconds() : 15);
            pstmt.setInt(5, queue.getMaxWaitSeconds() > 0 ? queue.getMaxWaitSeconds() : 300);
            pstmt.setString(6, queue.getMusicOnHold() != null ? queue.getMusicOnHold() : "default");
            pstmt.setString(7, queue.getOverflowAction() != null ? queue.getOverflowAction() : "voicemail");
            pstmt.setString(8, queue.getBusinessHours() != null ? queue.getBusinessHours() : "{\"mon_fri\":{\"open\":\"08:00\",\"close\":\"18:00\"}}");
            pstmt.setString(9, queue.getStatus() != null ? queue.getStatus() : "active");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToQueue(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving queue: " + e.getMessage());
            throw new RuntimeException("Database error saving queue: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean update(UUID tenantId, UUID id, Queue queue) {
        String sql = "UPDATE queues SET name = ?, strategy = ?, wrap_up_time_seconds = ?, max_wait_seconds = ?, " +
                     "music_on_hold = ?, overflow_action = ?, status = ?, updated_at = now() WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, queue.getName());
            pstmt.setString(2, queue.getStrategy());
            pstmt.setInt(3, queue.getWrapUpTimeSeconds());
            pstmt.setInt(4, queue.getMaxWaitSeconds());
            pstmt.setString(5, queue.getMusicOnHold());
            pstmt.setString(6, queue.getOverflowAction());
            pstmt.setString(7, queue.getStatus());
            pstmt.setObject(8, tenantId);
            pstmt.setObject(9, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating queue: " + e.getMessage());
            throw new RuntimeException("Database error updating queue: " + e.getMessage(), e);
        }
    }

    public boolean delete(UUID tenantId, UUID id) {
        String sql = "DELETE FROM queues WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting queue: " + e.getMessage());
            return false;
        }
    }

    public List<QueueMember> getMembers(UUID queueId) {
        List<QueueMember> members = new ArrayList<>();
        String sql = "SELECT qm.id, qm.queue_id, qm.agent_id, qm.penalty, qm.added_at, u.username, u.email, COALESCE(ast.current_state, 'available') AS current_state " +
                     "FROM queue_members qm " +
                     "JOIN users u ON qm.agent_id = u.id " +
                     "LEFT JOIN agent_states ast ON qm.agent_id = ast.agent_id " +
                     "WHERE qm.queue_id = ? ORDER BY qm.added_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, queueId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    QueueMember m = new QueueMember();
                    m.setId((UUID) rs.getObject("id"));
                    m.setQueueId((UUID) rs.getObject("queue_id"));
                    m.setAgentId((UUID) rs.getObject("agent_id"));
                    m.setPenalty(rs.getInt("penalty"));
                    m.setAddedAt(rs.getTimestamp("added_at"));
                    m.setAgentUsername(rs.getString("username"));
                    m.setAgentEmail(rs.getString("email"));
                    m.setAgentState(rs.getString("current_state"));
                    members.add(m);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching queue members for queue " + queueId + ": " + e.getMessage());
        }
        return members;
    }

    public QueueMember addMember(UUID queueId, UUID agentId, int penalty) {
        String sql = "INSERT INTO queue_members (queue_id, agent_id, penalty) VALUES (?, ?, ?) " +
                     "ON CONFLICT (queue_id, agent_id) DO UPDATE SET penalty = EXCLUDED.penalty " +
                     "RETURNING id, queue_id, agent_id, penalty, added_at";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, queueId);
            pstmt.setObject(2, agentId);
            pstmt.setInt(3, penalty);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    QueueMember m = new QueueMember();
                    m.setId((UUID) rs.getObject("id"));
                    m.setQueueId((UUID) rs.getObject("queue_id"));
                    m.setAgentId((UUID) rs.getObject("agent_id"));
                    m.setPenalty(rs.getInt("penalty"));
                    m.setAddedAt(rs.getTimestamp("added_at"));
                    return m;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding queue member: " + e.getMessage());
            throw new RuntimeException("Database error adding member: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean removeMember(UUID queueId, UUID agentId) {
        String sql = "DELETE FROM queue_members WHERE queue_id = ? AND agent_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, queueId);
            pstmt.setObject(2, agentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error removing queue member: " + e.getMessage());
            return false;
        }
    }

    private Queue mapResultSetToQueue(ResultSet rs) throws SQLException {
        Queue q = new Queue();
        q.setId((UUID) rs.getObject("id"));
        q.setTenantId((UUID) rs.getObject("tenant_id"));
        q.setName(rs.getString("name"));
        q.setStrategy(rs.getString("strategy"));
        q.setWrapUpTimeSeconds(rs.getInt("wrap_up_time_seconds"));
        q.setMaxWaitSeconds(rs.getInt("max_wait_seconds"));
        q.setMusicOnHold(rs.getString("music_on_hold"));
        q.setOverflowAction(rs.getString("overflow_action"));
        q.setBusinessHours(rs.getString("business_hours"));
        q.setStatus(rs.getString("status"));
        q.setCreatedAt(rs.getTimestamp("created_at"));
        q.setUpdatedAt(rs.getTimestamp("updated_at"));
        return q;
    }
}
