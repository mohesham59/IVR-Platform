package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationDao {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDao.class);

    public boolean createNotification(UUID tenantId, UUID userId, String message, String linkUrl, String type) {
        String sql = """
            INSERT INTO notifications (id, tenant_id, user_id, message, link_url, is_read, created_at, type)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, false, CURRENT_TIMESTAMP, ?)
            """;
        logger.info("Attempting to create notification for tenantId={}, userId={}, message='{}'", tenantId, userId, message);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            } else {
                ps.setNull(1, java.sql.Types.OTHER);
            }
            
            if (userId != null) {
                ps.setObject(2, userId);
            } else {
                ps.setNull(2, java.sql.Types.OTHER);
            }
            
            ps.setString(3, message);
            ps.setString(4, linkUrl);
            ps.setString(5, type);
            
            boolean inserted = ps.executeUpdate() > 0;
            if (inserted) {
                logger.info("Notification created successfully for tenantId={}, userId={}", tenantId, userId);
            }
            return inserted;
        } catch (SQLException e) {
            logger.error("Error creating notification: {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Notification> getNotificationsForUser(UUID tenantId, UUID userId) {
        logger.info("Fetching notifications for tenantId={}, userId={}", tenantId, userId);
        List<Notification> list = new ArrayList<>();

        // Collect tenant-scoped notifications (plan overrides etc. — user_id may be null)
        if (tenantId != null) {
            String tenantSql = """
                SELECT id, tenant_id, user_id, message, link_url, is_read, created_at, type
                FROM notifications
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                """;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(tenantSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapNotification(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching tenant-scoped notifications for tenantId={}: {}", tenantId, e.getMessage(), e);
            }
        }

        // Collect user-scoped notifications (direct user messages), avoiding duplicates
        if (userId != null) {
            String userSql = """
                SELECT id, tenant_id, user_id, message, link_url, is_read, created_at, type
                FROM notifications
                WHERE user_id = ?
                  AND (tenant_id IS NULL OR tenant_id != ?)
                ORDER BY created_at DESC
                """;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setObject(1, userId);
                // Exclude already-fetched tenant-scoped rows; if no tenantId, use a dummy that won't match
                if (tenantId != null) {
                    ps.setObject(2, tenantId);
                } else {
                    ps.setNull(2, java.sql.Types.OTHER);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapNotification(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching user-scoped notifications for userId={}: {}", userId, e.getMessage(), e);
            }
        }

        // Sort combined list by createdAt DESC
        list.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        logger.info("Found {} notification(s) for tenantId={}, userId={}", list.size(), tenantId, userId);
        return list;
    }

    public boolean markAsRead(UUID id) {
        String sql = "UPDATE notifications SET is_read = true WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error marking notification as read: {}", e.getMessage(), e);
            return false;
        }
    }

    private Notification mapNotification(ResultSet rs) throws SQLException {
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
