package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationDao {

    public NotificationDao() {}

    public List<Notification> findByTenantId(UUID tenantId, boolean unreadOnly, int limit) {
        List<Notification> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, tenant_id, user_id, message, link_url, is_read, created_at, type FROM notifications WHERE tenant_id = ? ");
        if (unreadOnly) {
            sql.append("AND is_read = false ");
        }
        sql.append("ORDER BY created_at DESC LIMIT ?");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            pstmt.setObject(1, tenantId);
            pstmt.setInt(2, limit > 0 ? limit : 20);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching notifications for tenant " + tenantId + ": " + e.getMessage());
        }
        return list;
    }

    public List<Notification> findPlatformNotifications(boolean unreadOnly, int limit) {
        List<Notification> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, tenant_id, user_id, message, link_url, is_read, created_at, type FROM notifications WHERE tenant_id IS NULL ");
        if (unreadOnly) {
            sql.append("AND is_read = false ");
        }
        sql.append("ORDER BY created_at DESC LIMIT ?");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            pstmt.setInt(1, limit > 0 ? limit : 20);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching platform notifications: " + e.getMessage());
        }
        return list;
    }

    public Notification save(Notification n) {
        String sql = "INSERT INTO notifications (tenant_id, user_id, message, link_url, is_read, type) VALUES (?, ?, ?, ?, ?, ?) RETURNING id, tenant_id, user_id, message, link_url, is_read, created_at, type";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, n.getTenantId());
            pstmt.setObject(2, n.getUserId());
            pstmt.setString(3, n.getMessage());
            pstmt.setString(4, n.getLinkUrl());
            pstmt.setBoolean(5, n.isRead());
            pstmt.setString(6, n.getType() != null ? n.getType() : "INFO");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("foreign key constraint")) {
                System.err.println("Warning: Cannot save notification for tenant ID (" + n.getTenantId() + "): tenant does not exist in tenants table.");
            } else {
                System.err.println("Error saving notification: " + e.getMessage());
            }
        }
        return null;
    }

    public Notification createNotification(UUID tenantId, UUID userId, String message, String linkUrl, String type) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setUserId(userId);
        n.setType(type != null ? type : "INFO");
        n.setMessage(message);
        n.setLinkUrl(linkUrl);
        n.setRead(false);
        return save(n);
    }

    public boolean markAsRead(UUID tenantId, UUID id) {
        String sql = tenantId != null
                ? "UPDATE notifications SET is_read = true WHERE tenant_id = ? AND id = ?"
                : "UPDATE notifications SET is_read = true WHERE tenant_id IS NULL AND id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (tenantId != null) {
                pstmt.setObject(1, tenantId);
                pstmt.setObject(2, id);
            } else {
                pstmt.setObject(1, id);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking notification as read: " + e.getMessage());
            return false;
        }
    }

    public boolean markAllAsRead(UUID tenantId) {
        String sql = tenantId != null
                ? "UPDATE notifications SET is_read = true WHERE tenant_id = ?"
                : "UPDATE notifications SET is_read = true WHERE tenant_id IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (tenantId != null) {
                pstmt.setObject(1, tenantId);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking all notifications as read: " + e.getMessage());
            return false;
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId((UUID) rs.getObject("id"));
        n.setTenantId((UUID) rs.getObject("tenant_id"));
        n.setUserId((UUID) rs.getObject("user_id"));
        n.setMessage(rs.getString("message"));
        n.setLinkUrl(rs.getString("link_url"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at"));
        n.setType(rs.getString("type"));
        return n;
    }
}
