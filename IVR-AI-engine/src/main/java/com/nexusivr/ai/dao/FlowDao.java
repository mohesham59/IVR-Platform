package com.nexusivr.ai.dao;

import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.model.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure JDBC DAO for managing {@link Flow} records in PostgreSQL table {@code flows}.
 * Enforces strict multi-tenancy by filtering every query by {@code tenant_id}.
 */
public class FlowDao {

    private static final Logger logger = LoggerFactory.getLogger(FlowDao.class);

    private static final String INSERT_SQL = """
        INSERT INTO flows (id, tenant_id, name, description, flow_json, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT id, tenant_id, name, description, flow_json, status, created_at, updated_at
        FROM flows
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String FIND_ALL_SQL = """
        SELECT id, tenant_id, name, description, flow_json, status, created_at, updated_at
        FROM flows
        WHERE tenant_id = ?
        ORDER BY updated_at DESC
        """;

    private static final String UPDATE_SQL = """
        UPDATE flows
        SET name = ?, description = ?, flow_json = ?::jsonb, status = ?, updated_at = now()
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String DELETE_SQL = """
        DELETE FROM flows
        WHERE id = ? AND tenant_id = ?
        """;

    public Flow create(Flow flow) {
        if (flow.getId() == null) {
            flow.setId(UUID.randomUUID());
        }
        Instant now = Instant.now();
        if (flow.getCreatedAt() == null) {
            flow.setCreatedAt(now);
        }
        if (flow.getUpdatedAt() == null) {
            flow.setUpdatedAt(now);
        }
        if (flow.getFlowJson() == null || flow.getFlowJson().trim().isEmpty()) {
            flow.setFlowJson("{}");
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, flow.getId());
            ps.setObject(2, flow.getTenantId());
            ps.setString(3, flow.getName());
            ps.setString(4, flow.getDescription());
            ps.setString(5, flow.getFlowJson());
            ps.setString(6, flow.getStatus() != null ? flow.getStatus() : "DRAFT");
            ps.setTimestamp(7, Timestamp.from(flow.getCreatedAt()));
            ps.setTimestamp(8, Timestamp.from(flow.getUpdatedAt()));

            ps.executeUpdate();
            logger.debug("Created Flow: {} name: '{}' tenant: {}", flow.getId(), flow.getName(), flow.getTenantId());
            return flow;
        } catch (SQLException e) {
            logger.error("Failed to create Flow for tenant {}", flow.getTenantId(), e);
            throw new DataAccessException("Error creating Flow", e);
        }
    }

    public Optional<Flow> findById(UUID id, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {

            ps.setObject(1, id);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToFlow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find Flow by id {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error finding Flow by ID", e);
        }
    }

    public List<Flow> findAll(UUID tenantId) {
        List<Flow> flows = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL)) {

            ps.setObject(1, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    flows.add(mapResultSetToFlow(rs));
                }
            }
            return flows;
        } catch (SQLException e) {
            logger.error("Failed to find all Flows for tenant {}", tenantId, e);
            throw new DataAccessException("Error finding Flows", e);
        }
    }

    public boolean update(UUID id, UUID tenantId, Flow flow) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {

            ps.setString(1, flow.getName());
            ps.setString(2, flow.getDescription());
            ps.setString(3, flow.getFlowJson() != null ? flow.getFlowJson() : "{}");
            ps.setString(4, flow.getStatus() != null ? flow.getStatus() : "DRAFT");
            ps.setObject(5, id);
            ps.setObject(6, tenantId);

            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Failed to update Flow {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error updating Flow", e);
        }
    }

    public boolean delete(UUID id, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {

            ps.setObject(1, id);
            ps.setObject(2, tenantId);

            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete Flow {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error deleting Flow", e);
        }
    }

    private Flow mapResultSetToFlow(ResultSet rs) throws SQLException {
        Flow flow = new Flow();
        flow.setId(rs.getObject("id", UUID.class));
        flow.setTenantId(rs.getObject("tenant_id", UUID.class));
        flow.setName(rs.getString("name"));
        flow.setDescription(rs.getString("description"));
        flow.setFlowJson(rs.getString("flow_json"));
        flow.setStatus(rs.getString("status"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            flow.setCreatedAt(created.toInstant());
        }

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            flow.setUpdatedAt(updated.toInstant());
        }

        return flow;
    }
}
