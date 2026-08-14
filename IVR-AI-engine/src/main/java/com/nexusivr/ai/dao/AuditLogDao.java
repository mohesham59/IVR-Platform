package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.AuditLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogDao {

    public AuditLogDao() {}

    public AuditLog logEvent(UUID tenantId, UUID actorUserId, String actorEmail, String actionType,
                             String targetEntityType, String targetEntityId, String detailsJson, String ipAddress) {
        String sql = "INSERT INTO audit_logs (tenant_id, actor_user_id, actor_email, action_type, target_entity_type, target_entity_id, details, ip_address) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?) " +
                     "RETURNING id, tenant_id, actor_user_id, actor_email, action_type, target_entity_type, target_entity_id, details, ip_address, created_at";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, actorUserId);
            pstmt.setString(3, actorEmail);
            pstmt.setString(4, actionType);
            pstmt.setString(5, targetEntityType);
            pstmt.setString(6, targetEntityId);
            pstmt.setString(7, (detailsJson != null && !detailsJson.isBlank()) ? detailsJson : "{}");
            pstmt.setString(8, ipAddress != null ? ipAddress : "127.0.0.1");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAuditLog(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error inserting audit log: " + e.getMessage());
        }
        return null;
    }

    public List<AuditLog> findAuditLogs(UUID tenantId, String actionType, Timestamp dateFrom, Timestamp dateTo, int offset, int limit) {
        List<AuditLog> logs = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, tenant_id, actor_user_id, actor_email, action_type, target_entity_type, target_entity_id, details, ip_address, created_at FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (tenantId != null) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (actionType != null && !actionType.isBlank() && !"ALL".equalsIgnoreCase(actionType)) {
            sql.append(" AND action_type = ?");
            params.add(actionType);
        }
        if (dateFrom != null) {
            sql.append(" AND created_at >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND created_at <= ?");
            params.add(dateTo);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error querying audit logs: " + e.getMessage());
        }
        return logs;
    }

    public int countAuditLogs(UUID tenantId, String actionType, Timestamp dateFrom, Timestamp dateTo) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (tenantId != null) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (actionType != null && !actionType.isBlank() && !"ALL".equalsIgnoreCase(actionType)) {
            sql.append(" AND action_type = ?");
            params.add(actionType);
        }
        if (dateFrom != null) {
            sql.append(" AND created_at >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND created_at <= ?");
            params.add(dateTo);
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting audit logs: " + e.getMessage());
        }
        return 0;
    }

    private AuditLog mapResultSetToAuditLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId((UUID) rs.getObject("id"));
        log.setTenantId((UUID) rs.getObject("tenant_id"));
        log.setActorUserId((UUID) rs.getObject("actor_user_id"));
        log.setActorEmail(rs.getString("actor_email"));
        log.setActionType(rs.getString("action_type"));
        log.setTargetEntityType(rs.getString("target_entity_type"));
        log.setTargetEntityId(rs.getString("target_entity_id"));
        log.setDetails(rs.getString("details"));
        log.setIpAddress(rs.getString("ip_address"));
        log.setCreatedAt(rs.getTimestamp("created_at"));
        return log;
    }
}
