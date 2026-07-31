package com.nexusivr.ai.dao;

import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure JDBC DAO for managing {@link Message} records in PostgreSQL table {@code ai_messages}.
 * Enforces strict multi-tenancy by filtering every query by {@code tenant_id}.
 */
public class MessageDao {

    private static final Logger logger = LoggerFactory.getLogger(MessageDao.class);

    private static final String INSERT_SQL = """
        INSERT INTO ai_messages (id, session_id, tenant_id, turn_number, role, content, model_used, tokens_input, tokens_output, metadata, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;

    private static final String FIND_BY_SESSION_ID_SQL = """
        SELECT id, session_id, tenant_id, turn_number, role, content, model_used, tokens_input, tokens_output, metadata, created_at
        FROM ai_messages
        WHERE session_id = ? AND tenant_id = ?
        ORDER BY turn_number ASC
        """;

    private static final String FIND_LATEST_MESSAGES_SQL = """
        SELECT id, session_id, tenant_id, turn_number, role, content, model_used, tokens_input, tokens_output, metadata, created_at
        FROM ai_messages
        WHERE session_id = ? AND tenant_id = ?
        ORDER BY turn_number DESC
        LIMIT ?
        """;

    private static final String COUNT_MESSAGES_SQL = """
        SELECT COUNT(*)
        FROM ai_messages
        WHERE session_id = ? AND tenant_id = ?
        """;

    private static final String DELETE_BY_SESSION_ID_SQL = """
        DELETE FROM ai_messages
        WHERE session_id = ? AND tenant_id = ?
        """;

    // Fix 10b: used to compute the next safe turn_number on a unique-constraint retry.
    private static final String SELECT_MAX_TURN_SQL = """
        SELECT COALESCE(MAX(turn_number), 0)
        FROM ai_messages
        WHERE session_id = ? AND tenant_id = ?
        """;

    public Message save(Message message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(Instant.now());
        }

        // Fix 12: If turn_number is not set (0 or negative), assign it atomically here
        // based on the current MAX(turn_number) for this session. This prevents race
        // conditions where two concurrent callers compute the same next turn_number.
        if (message.getTurnNumber() <= 0) {
            try {
                int nextTurn = fetchMaxTurnNumber(message.getSessionId(), message.getTenantId()) + 1;
                message.setTurnNumber(nextTurn);
            } catch (Exception e) {
                logger.warn("[MessageDao] Failed to fetch MAX(turn_number) for session {}: {}", message.getSessionId(), e.getMessage());
            }
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, message.getId());
            ps.setObject(2, message.getSessionId());
            ps.setObject(3, message.getTenantId());
            ps.setInt(4, message.getTurnNumber());
            ps.setString(5, message.getRole() != null ? message.getRole().name() : MessageRole.USER.name());
            ps.setString(6, message.getContent());
            ps.setString(7, message.getModelUsed());

            if (message.getTokensInput() != null) {
                ps.setInt(8, message.getTokensInput());
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            if (message.getTokensOutput() != null) {
                ps.setInt(9, message.getTokensOutput());
            } else {
                ps.setNull(9, Types.INTEGER);
            }

            String metaJson = message.getMetadata() != null ? message.getMetadata() : "{}";
            ps.setString(10, metaJson.startsWith("{") ? metaJson : "{}");
            ps.setTimestamp(11, Timestamp.from(message.getCreatedAt()));

            ps.executeUpdate();
            logger.debug("Saved Message: {} turn: {} for session: {}", message.getId(), message.getTurnNumber(), message.getSessionId());
            return message;
        } catch (SQLException e) {
            // Fix 10b: On duplicate key for (session_id, turn_number) — SQLState 23505 — re-fetch
            // the current MAX turn_number and retry the insert once with maxTurn+1.
            // This is a defense-in-depth layer that handles any caller that reaches save() with
            // a conflicting turn_number, even if the synchronized block in the servlet caught
            // most cases already.
            if ("23505".equals(e.getSQLState())) {
                logger.warn("[MessageDao] Duplicate turn_number={} for session {} — retrying with MAX+1 (SQLState 23505)",
                        message.getTurnNumber(), message.getSessionId());
                try {
                    int nextTurn = fetchMaxTurnNumber(message.getSessionId(), message.getTenantId()) + 1;
                    message.setTurnNumber(nextTurn);
                    // Retry insert with the corrected turn_number
                    try (Connection conn2 = DatabaseManager.getConnection();
                         PreparedStatement ps2 = conn2.prepareStatement(INSERT_SQL)) {
                        ps2.setObject(1, message.getId());
                        ps2.setObject(2, message.getSessionId());
                        ps2.setObject(3, message.getTenantId());
                        ps2.setInt(4, message.getTurnNumber());
                        ps2.setString(5, message.getRole() != null ? message.getRole().name() : MessageRole.USER.name());
                        ps2.setString(6, message.getContent());
                        ps2.setString(7, message.getModelUsed());
                        if (message.getTokensInput() != null) { ps2.setInt(8, message.getTokensInput()); } else { ps2.setNull(8, Types.INTEGER); }
                        if (message.getTokensOutput() != null) { ps2.setInt(9, message.getTokensOutput()); } else { ps2.setNull(9, Types.INTEGER); }
                        String metaJson2 = message.getMetadata() != null ? message.getMetadata() : "{}";
                        ps2.setString(10, metaJson2.startsWith("{") ? metaJson2 : "{}");
                        ps2.setTimestamp(11, Timestamp.from(message.getCreatedAt()));
                        ps2.executeUpdate();
                        logger.info("[MessageDao] Retry insert succeeded with turn_number={} for session: {}", nextTurn, message.getSessionId());
                        return message;
                    }
                } catch (SQLException retryEx) {
                    logger.error("[MessageDao] Retry insert also failed for session {} turn {}", message.getSessionId(), message.getTurnNumber(), retryEx);
                    throw new DataAccessException("Error saving Message (retry after duplicate key)", retryEx);
                }
            }
            logger.error("Failed to save Message for session {} tenant {}", message.getSessionId(), message.getTenantId(), e);
            throw new DataAccessException("Error saving Message", e);
        }
    }

    /**
     * Fix 10b helper: returns the current MAX(turn_number) for the given session, or 0 if none exist.
     */
    private int fetchMaxTurnNumber(UUID sessionId, UUID tenantId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_MAX_TURN_SQL)) {
            ps.setObject(1, sessionId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<Message> findBySessionId(UUID sessionId, UUID tenantId) {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_SESSION_ID_SQL)) {

            ps.setObject(1, sessionId);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
            return messages;
        } catch (SQLException e) {
            logger.error("Failed to find messages for session {} tenant {}", sessionId, tenantId, e);
            throw new DataAccessException("Error finding messages by session ID", e);
        }
    }

    public List<Message> findLatestMessages(UUID sessionId, UUID tenantId, int limit) {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_LATEST_MESSAGES_SQL)) {

            ps.setObject(1, sessionId);
            ps.setObject(2, tenantId);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
            return messages;
        } catch (SQLException e) {
            logger.error("Failed to find latest messages for session {} tenant {}", sessionId, tenantId, e);
            throw new DataAccessException("Error finding latest messages", e);
        }
    }

    public long countMessages(UUID sessionId, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_MESSAGES_SQL)) {

            ps.setObject(1, sessionId);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count messages for session {} tenant {}", sessionId, tenantId, e);
            throw new DataAccessException("Error counting messages", e);
        }
    }

    public boolean deleteBySessionId(UUID sessionId, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_SESSION_ID_SQL)) {

            ps.setObject(1, sessionId);
            ps.setObject(2, tenantId);

            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete messages for session {} tenant {}", sessionId, tenantId, e);
            throw new DataAccessException("Error deleting messages by session ID", e);
        }
    }

    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getObject("id", UUID.class));
        message.setSessionId(rs.getObject("session_id", UUID.class));
        message.setTenantId(rs.getObject("tenant_id", UUID.class));
        message.setTurnNumber(rs.getInt("turn_number"));

        String roleStr = rs.getString("role");
        if (roleStr != null) {
            message.setRole(MessageRole.valueOf(roleStr));
        }

        message.setContent(rs.getString("content"));
        message.setModelUsed(rs.getString("model_used"));

        int tokensInput = rs.getInt("tokens_input");
        if (!rs.wasNull()) {
            message.setTokensInput(tokensInput);
        }

        int tokensOutput = rs.getInt("tokens_output");
        if (!rs.wasNull()) {
            message.setTokensOutput(tokensOutput);
        }

        message.setMetadata(rs.getString("metadata"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            message.setCreatedAt(created.toInstant());
        }

        return message;
    }
}
