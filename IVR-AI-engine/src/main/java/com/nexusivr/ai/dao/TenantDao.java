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
        private String subscriptionPlanId;
        private String subscriptionStatus;
        private Timestamp subscriptionExpiresAt;
        private String subscriptionPlanName;
        private Long subscriptionPlanPrice;
        private String subscriptionPlanInterval;

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

        public String getSubscriptionPlanId() { return subscriptionPlanId; }
        public void setSubscriptionPlanId(String subscriptionPlanId) { this.subscriptionPlanId = subscriptionPlanId; }

        public String getSubscriptionStatus() { return subscriptionStatus; }
        public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

        public Timestamp getSubscriptionExpiresAt() { return subscriptionExpiresAt; }
        public void setSubscriptionExpiresAt(Timestamp subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; }

        public String getSubscriptionPlanName() { return subscriptionPlanName; }
        public void setSubscriptionPlanName(String subscriptionPlanName) { this.subscriptionPlanName = subscriptionPlanName; }

        public Long getSubscriptionPlanPrice() { return subscriptionPlanPrice; }
        public void setSubscriptionPlanPrice(Long subscriptionPlanPrice) { this.subscriptionPlanPrice = subscriptionPlanPrice; }

        public String getSubscriptionPlanInterval() { return subscriptionPlanInterval; }
        public void setSubscriptionPlanInterval(String subscriptionPlanInterval) { this.subscriptionPlanInterval = subscriptionPlanInterval; }
    }

    public List<Tenant> findAllTenants() {
        List<Tenant> list = new ArrayList<>();
        String sql = """
            SELECT t.id, t.display_name, t.owner_user_id, t.status, t.created_at, t.updated_at,
                   t.subscription_plan_id, t.subscription_status, t.subscription_expires_at,
                   u.username AS owner_username, u.email AS owner_email,
                   sp.name AS subscription_plan_name, sp.price_piasters AS subscription_plan_price, sp.billing_interval AS subscription_plan_interval
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            LEFT JOIN subscription_plans sp ON t.subscription_plan_id = sp.id
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
                   t.subscription_plan_id, t.subscription_status, t.subscription_expires_at,
                   u.username AS owner_username, u.email AS owner_email,
                   sp.name AS subscription_plan_name, sp.price_piasters AS subscription_plan_price, sp.billing_interval AS subscription_plan_interval
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            LEFT JOIN subscription_plans sp ON t.subscription_plan_id = sp.id
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
                   t.subscription_plan_id, t.subscription_status, t.subscription_expires_at,
                   u.username AS owner_username, u.email AS owner_email,
                   sp.name AS subscription_plan_name, sp.price_piasters AS subscription_plan_price, sp.billing_interval AS subscription_plan_interval
            FROM tenants t
            LEFT JOIN users u ON t.owner_user_id = u.id
            LEFT JOIN subscription_plans sp ON t.subscription_plan_id = sp.id
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

    public Tenant createTenant(String displayName, String ownerUserId, String status, String subscriptionPlanId) {
        String newId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO tenants (id, display_name, owner_user_id, status, subscription_plan_id, subscription_status, subscription_expires_at, created_at, updated_at)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
            if (subscriptionPlanId != null && !subscriptionPlanId.isBlank()) {
                ps.setString(5, subscriptionPlanId);
                ps.setString(6, "ACTIVE");
                ps.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)); // 30 days
            } else {
                ps.setNull(5, java.sql.Types.OTHER);
                ps.setString(6, "INACTIVE");
                ps.setNull(7, java.sql.Types.TIMESTAMP);
            }
            ps.executeUpdate();

            if (subscriptionPlanId != null && !subscriptionPlanId.isBlank()) {
                createInitialTransaction(newId, subscriptionPlanId);
            }

            return findById(newId);
        } catch (SQLException e) {
            logger.error("Error creating tenant: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean updateTenant(String tenantId, String displayName, String ownerUserId, String status, String subscriptionPlanId) {
        Tenant existing = findById(tenantId);
        String existingPlanId = existing != null ? existing.getSubscriptionPlanId() : null;
        java.sql.Timestamp existingExpiresAt = existing != null ? existing.getSubscriptionExpiresAt() : null;

        String sql = """
            UPDATE tenants
            SET display_name = ?, owner_user_id = ?::uuid, status = ?, subscription_plan_id = ?::uuid, subscription_status = ?, subscription_expires_at = ?, updated_at = CURRENT_TIMESTAMP
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
            
            boolean planChanged = false;
            if (subscriptionPlanId != null && !subscriptionPlanId.isBlank()) {
                ps.setString(4, subscriptionPlanId);
                ps.setString(5, "ACTIVE");
                
                if (existingPlanId == null || !existingPlanId.equals(subscriptionPlanId)) {
                    planChanged = true;
                    ps.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
                } else {
                    ps.setTimestamp(6, existingExpiresAt);
                }
            } else {
                ps.setNull(4, java.sql.Types.OTHER);
                ps.setString(5, "INACTIVE");
                ps.setNull(6, java.sql.Types.TIMESTAMP);
            }
            ps.setString(7, tenantId);
            
            boolean updated = ps.executeUpdate() > 0;
            if (updated && planChanged) {
                createInitialTransaction(tenantId, subscriptionPlanId);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("Error updating tenant {}: {}", tenantId, e.getMessage(), e);
            return false;
        }
    }

    private void createInitialTransaction(String tenantId, String planId) {
        String sql = """
            INSERT INTO transactions (id, tenant_id, type, amount_piasters, currency, status, plan_id, created_at, updated_at)
            VALUES (gen_random_uuid(), ?::uuid, 'SUBSCRIPTION', ?, 'EGP', 'SUCCESS', ?::uuid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
        long pricePiasters = 0;
        String priceSql = "SELECT price_piasters FROM subscription_plans WHERE id = ?::uuid";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(priceSql)) {
                ps.setString(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pricePiasters = rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setLong(2, pricePiasters);
                ps.setString(3, planId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Error creating initial transaction: {}", e.getMessage(), e);
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
        
        t.setSubscriptionPlanId(rs.getString("subscription_plan_id"));
        t.setSubscriptionStatus(rs.getString("subscription_status"));
        t.setSubscriptionExpiresAt(rs.getTimestamp("subscription_expires_at"));
        t.setSubscriptionPlanName(rs.getString("subscription_plan_name"));
        t.setSubscriptionPlanPrice(rs.getObject("subscription_plan_price") != null ? rs.getLong("subscription_plan_price") : null);
        t.setSubscriptionPlanInterval(rs.getString("subscription_plan_interval"));
        return t;
    }

    public String getPlanNameById(String planId) {
        if (planId == null || planId.isBlank()) return "No Plan (None)";
        String sql = "SELECT name FROM subscription_plans WHERE id = ?::uuid";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.error("Error looking up plan name by id {}: {}", planId, e.getMessage());
        }
        return "No Plan (None)";
    }
}
