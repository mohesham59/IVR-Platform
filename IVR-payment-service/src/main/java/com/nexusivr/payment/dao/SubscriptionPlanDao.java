package com.nexusivr.payment.dao;

import com.nexusivr.payment.model.SubscriptionPlan;
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

/**
 * JDBC Data Access Object for subscription_plans table.
 */
public class SubscriptionPlanDao {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionPlanDao.class);

    public SubscriptionPlan createPlan(SubscriptionPlan plan) throws SQLException {
        if (plan.getId() == null) {
            plan.setId(UUID.randomUUID());
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);

        String sql = "INSERT INTO subscription_plans (id, name, price_piasters, billing_interval, integration_ids, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, plan.getId());
            stmt.setString(2, plan.getName());
            stmt.setLong(3, plan.getPricePiasters());
            stmt.setString(4, plan.getBillingInterval());
            stmt.setString(5, plan.getIntegrationIds());
            stmt.setTimestamp(6, now);
            stmt.setTimestamp(7, now);

            stmt.executeUpdate();
            logger.info("Created subscription plan: {} ({})", plan.getName(), plan.getId());
            return plan;
        }
    }

    public List<SubscriptionPlan> findAllPlans() throws SQLException {
        List<SubscriptionPlan> list = new ArrayList<>();
        String sql = "SELECT id, name, price_piasters, billing_interval, integration_ids, created_at, updated_at " +
                     "FROM subscription_plans ORDER BY price_piasters ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToPlan(rs));
            }
        }
        return list;
    }

    public SubscriptionPlan findPlanById(UUID id) throws SQLException {
        String sql = "SELECT id, name, price_piasters, billing_interval, integration_ids, created_at, updated_at " +
                     "FROM subscription_plans WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToPlan(rs);
                }
            }
        }
        return null;
    }

    public boolean updatePlan(SubscriptionPlan plan) throws SQLException {
        String sql = "UPDATE subscription_plans SET name = ?, price_piasters = ?, billing_interval = ?, " +
                     "integration_ids = ?, updated_at = now() WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, plan.getName());
            stmt.setLong(2, plan.getPricePiasters());
            stmt.setString(3, plan.getBillingInterval());
            stmt.setString(4, plan.getIntegrationIds());
            stmt.setObject(5, plan.getId());

            int updated = stmt.executeUpdate();
            return updated > 0;
        }
    }

    public boolean deletePlan(UUID id) throws SQLException {
        String sql = "DELETE FROM subscription_plans WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            int deleted = stmt.executeUpdate();
            return deleted > 0;
        }
    }

    private SubscriptionPlan mapRowToPlan(ResultSet rs) throws SQLException {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId((UUID) rs.getObject("id"));
        plan.setName(rs.getString("name"));
        plan.setPricePiasters(rs.getLong("price_piasters"));
        plan.setBillingInterval(rs.getString("billing_interval"));
        plan.setIntegrationIds(rs.getString("integration_ids"));
        plan.setCreatedAt(rs.getTimestamp("created_at"));
        plan.setUpdatedAt(rs.getTimestamp("updated_at"));
        return plan;
    }
}
