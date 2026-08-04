package com.nexusivr.ai.dao;

import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.model.SessionStatus;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure JDBC DAO for managing {@link AiSession} records in PostgreSQL table {@code ai_sessions}.
 * Enforces strict multi-tenancy by filtering every query by {@code tenant_id}.
 */
public class AiSessionDao {

    private static final Logger logger = LoggerFactory.getLogger(AiSessionDao.class);

    private static final String INSERT_SQL = """
        INSERT INTO ai_sessions (id, tenant_id, channel, external_reference_id, customer_identifier, status, started_at, ended_at, metadata, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT id, tenant_id, channel, external_reference_id, customer_identifier, status, started_at, ended_at, metadata, created_at, updated_at
        FROM ai_sessions
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String FIND_BY_EXT_REF_SQL = """
        SELECT id, tenant_id, channel, external_reference_id, customer_identifier, status, started_at, ended_at, metadata, created_at, updated_at
        FROM ai_sessions
        WHERE external_reference_id = ? AND tenant_id = ?
        """;

    private static final String FIND_ACTIVE_SESSIONS_SQL = """
        SELECT id, tenant_id, channel, external_reference_id, customer_identifier, status, started_at, ended_at, metadata, created_at, updated_at
        FROM ai_sessions
        WHERE tenant_id = ? AND status = 'ACTIVE'
        ORDER BY started_at DESC
        """;

    private static final String FIND_ALL_SESSIONS_SQL = """
        SELECT id, tenant_id, channel, external_reference_id, customer_identifier, status, started_at, ended_at, metadata, created_at, updated_at
        FROM ai_sessions
        WHERE tenant_id = ?
        ORDER BY updated_at DESC
        """;

    private static final String UPDATE_SESSION_TITLE_SQL = """
        UPDATE ai_sessions
        SET customer_identifier = ?, updated_at = now()
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String UPDATE_STATUS_SQL = """
        UPDATE ai_sessions
        SET status = ?, updated_at = now()
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String END_SESSION_SQL = """
        UPDATE ai_sessions
        SET status = 'ENDED', ended_at = now(), updated_at = now()
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String DELETE_SQL = """
        DELETE FROM ai_sessions
        WHERE id = ? AND tenant_id = ?
        """;

    public AiSession create(AiSession session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID());
        }
        Instant now = Instant.now();
        if (session.getStartedAt() == null) {
            session.setStartedAt(now);
        }
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(now);
        }
        if (session.getUpdatedAt() == null) {
            session.setUpdatedAt(now);
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, session.getId());
            ps.setObject(2, session.getTenantId());
            ps.setString(3, session.getChannel() != null ? session.getChannel().name() : Channel.VOICE.name());
            ps.setString(4, session.getExternalReferenceId());
            ps.setString(5, session.getCustomerIdentifier());
            ps.setString(6, session.getStatus() != null ? session.getStatus().name() : SessionStatus.ACTIVE.name());
            ps.setTimestamp(7, Timestamp.from(session.getStartedAt()));
            ps.setTimestamp(8, session.getEndedAt() != null ? Timestamp.from(session.getEndedAt()) : null);

            String metaJson = session.getMetadata() != null ? session.getMetadata().toString() : "{}";
            ps.setString(9, metaJson.startsWith("{") ? metaJson : "{}");

            ps.setTimestamp(10, Timestamp.from(session.getCreatedAt()));
            ps.setTimestamp(11, Timestamp.from(session.getUpdatedAt()));

            ps.executeUpdate();
            logger.debug("Created AiSession: {} for tenant: {}", session.getId(), session.getTenantId());
            return session;
        } catch (SQLException e) {
            logger.error("Failed to create AiSession for tenant {}", session.getTenantId(), e);
            throw new DataAccessException("Error creating AiSession", e);
        }
    }

    public Optional<AiSession> findById(UUID id, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {

            ps.setObject(1, id);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAiSession(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find AiSession by id {} for tenant {}", id, tenantId, e);
            throw new DataAccessException("Error finding AiSession by ID", e);
        }
    }

    public Optional<AiSession> findByExternalReference(String externalReferenceId, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_EXT_REF_SQL)) {

            ps.setString(1, externalReferenceId);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAiSession(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find AiSession by extRef {} for tenant {}", externalReferenceId, tenantId, e);
            throw new DataAccessException("Error finding AiSession by external reference", e);
        }
    }

    public List<AiSession> findActiveSessions(UUID tenantId) {
        List<AiSession> sessions = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ACTIVE_SESSIONS_SQL)) {

            ps.setObject(1, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToAiSession(rs));
                }
            }
            return sessions;
        } catch (SQLException e) {
            logger.error("Failed to find active AiSessions for tenant {}", tenantId, e);
            throw new DataAccessException("Error finding active AiSessions", e);
        }
    }

    public List<AiSession> findAllSessions(UUID tenantId) {
        List<AiSession> sessions = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_SESSIONS_SQL)) {

            ps.setObject(1, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToAiSession(rs));
                }
            }
            return sessions;
        } catch (SQLException e) {
            logger.error("Failed to find all AiSessions for tenant {}", tenantId, e);
            throw new DataAccessException("Error finding all AiSessions", e);
        }
    }

    public boolean updateTitle(UUID id, UUID tenantId, String title) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SESSION_TITLE_SQL)) {

            ps.setString(1, title);
            ps.setObject(2, id);
            ps.setObject(3, tenantId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update title for AiSession {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error updating AiSession title", e);
        }
    }

    public boolean updateStatus(UUID id, UUID tenantId, SessionStatus status) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {

            ps.setString(1, status.name());
            ps.setObject(2, id);
            ps.setObject(3, tenantId);

            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Failed to update status for AiSession {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error updating AiSession status", e);
        }
    }

    public boolean endSession(UUID id, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(END_SESSION_SQL)) {

            ps.setObject(1, id);
            ps.setObject(2, tenantId);

            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Failed to end AiSession {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error ending AiSession", e);
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
            logger.error("Failed to delete AiSession {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error deleting AiSession", e);
        }
    }

    private AiSession mapResultSetToAiSession(ResultSet rs) throws SQLException {
        AiSession session = new AiSession();
        session.setId(rs.getObject("id", UUID.class));
        session.setTenantId(rs.getObject("tenant_id", UUID.class));
        
        String channelStr = rs.getString("channel");
        if (channelStr != null) {
            session.setChannel(Channel.valueOf(channelStr));
        }

        session.setExternalReferenceId(rs.getString("external_reference_id"));
        session.setCustomerIdentifier(rs.getString("customer_identifier"));

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            session.setStatus(SessionStatus.valueOf(statusStr));
        }

        Timestamp started = rs.getTimestamp("started_at");
        if (started != null) {
            session.setStartedAt(started.toInstant());
        }

        Timestamp ended = rs.getTimestamp("ended_at");
        if (ended != null) {
            session.setEndedAt(ended.toInstant());
        }

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            session.setCreatedAt(created.toInstant());
        }

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            session.setUpdatedAt(updated.toInstant());
        }

        return session;
    }
}
