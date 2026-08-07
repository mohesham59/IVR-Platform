package com.nexusivr.ai.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TenantDao {

    private static final Logger logger = LoggerFactory.getLogger(TenantDao.class);

    public static class Tenant {
        private String id;
        private String displayName;
        private String ownerUserId;
        private String ownerUsername;
        private String ownerEmail;
        private String status;
        private Timestamp createdAt;
        private Timestamp updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

        public String getOwnerUsername() { return ownerUsername; }
        public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

        public String getOwnerEmail() { return ownerEmail; }
        public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    }

    public List<Tenant> findAllTenants() {
        List<Tenant> list = new ArrayList<>();
        String sql = """
            SELECT t.id, t.display_name, t.owner_user_id, t.status, t.created_at, t.updated_at,
                   u.username AS owner_username, u.email AS owner_email
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            ORDER BY t.created_at DESC
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapTenant(rs));
            }
        } catch (SQLException e) {
            logger.error("Error listing tenants: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<Tenant> findTenantsByUserId(String userId) {
        List<Tenant> list = new ArrayList<>();
        String sql = """
            SELECT t.id, t.display_name, t.owner_user_id, t.status, t.created_at, t.updated_at,
                   u.username AS owner_username, u.email AS owner_email
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            WHERE t.owner_user_id = ?::uuid
            ORDER BY t.created_at DESC
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTenant(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing tenants for user {}: {}", userId, e.getMessage(), e);
        }
        return list;
    }

    public boolean updateActiveTenant(String userId, String tenantId) {
        String sql = "UPDATE users SET active_tenant_id = ?::uuid, updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating active tenant for user {} to {}: {}", userId, tenantId, e.getMessage(), e);
            return false;
        }
    }

    public Tenant findById(String tenantId) {
        String sql = """
            SELECT t.id, t.display_name, t.owner_user_id, t.status, t.created_at, t.updated_at,
                   u.username AS owner_username, u.email AS owner_email
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            WHERE t.id = ?::uuid
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTenant(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding tenant by id: {}", tenantId, e);
        }
        return null;
    }

    public Tenant createTenant(String displayName, String ownerUserId, String status) {
        String newId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO tenants (id, display_name, owner_user_id, status, created_at, updated_at)
            VALUES (?::uuid, ?, ?::uuid, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newId);
            ps.setString(2, displayName);
            if (ownerUserId != null && !ownerUserId.isBlank()) {
                ps.setString(3, ownerUserId);
            } else {
                ps.setNull(3, java.sql.Types.OTHER);
            }
            ps.setString(4, status != null ? status : "ACTIVE");
            ps.executeUpdate();

            return findById(newId);
        } catch (SQLException e) {
            logger.error("Error creating tenant: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean updateTenant(String tenantId, String displayName, String ownerUserId, String status) {
        String sql = """
            UPDATE tenants
            SET display_name = ?, owner_user_id = ?::uuid, status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, displayName);
            if (ownerUserId != null && !ownerUserId.isBlank()) {
                ps.setString(2, ownerUserId);
            } else {
                ps.setNull(2, java.sql.Types.OTHER);
            }
            ps.setString(3, status);
            ps.setString(4, tenantId);
            boolean updated = ps.executeUpdate() > 0;

            return updated;
        } catch (SQLException e) {
            logger.error("Error updating tenant {}: {}", tenantId, e.getMessage(), e);
            return false;
        }
    }

    public boolean deleteTenant(String tenantId) {
        String sql = "DELETE FROM tenants WHERE id = ?::uuid";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting tenant {}: {}", tenantId, e.getMessage(), e);
            return false;
        }
    }

    private Tenant mapTenant(ResultSet rs) throws SQLException {
        Tenant t = new Tenant();
        t.setId(rs.getString("id"));
        t.setDisplayName(rs.getString("display_name"));
        t.setOwnerUserId(rs.getString("owner_user_id"));
        t.setOwnerUsername(rs.getString("owner_username"));
        t.setOwnerEmail(rs.getString("owner_email"));
        t.setStatus(rs.getString("status"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setUpdatedAt(rs.getTimestamp("updated_at"));
        return t;
    }
}
