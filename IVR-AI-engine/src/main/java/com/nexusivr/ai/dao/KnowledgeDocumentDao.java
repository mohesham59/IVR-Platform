package com.nexusivr.ai.dao;

import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.model.DocumentStatus;
import com.nexusivr.ai.model.KnowledgeDocument;
import com.nexusivr.ai.model.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure JDBC DAO for managing {@link KnowledgeDocument} records in PostgreSQL table {@code knowledge_documents}.
 * Enforces strict multi-tenancy by filtering every query by {@code tenant_id}.
 */
public class KnowledgeDocumentDao {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeDocumentDao.class);

    private static final String INSERT_SQL = """
        INSERT INTO knowledge_documents (id, tenant_id, title, source_type, source_uri, status, version, checksum, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT id, tenant_id, title, source_type, source_uri, status, version, checksum, created_at, updated_at
        FROM knowledge_documents
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String FIND_ALL_SQL = """
        SELECT id, tenant_id, title, source_type, source_uri, status, version, checksum, created_at, updated_at
        FROM knowledge_documents
        WHERE tenant_id = ?
        ORDER BY created_at DESC
        """;

    private static final String FIND_BY_STATUS_SQL = """
        SELECT id, tenant_id, title, source_type, source_uri, status, version, checksum, created_at, updated_at
        FROM knowledge_documents
        WHERE tenant_id = ? AND status = ?
        ORDER BY created_at DESC
        """;

    private static final String UPDATE_STATUS_SQL = """
        UPDATE knowledge_documents
        SET status = ?, updated_at = now()
        WHERE id = ? AND tenant_id = ?
        """;

    private static final String DELETE_SQL = """
        DELETE FROM knowledge_documents
        WHERE id = ? AND tenant_id = ?
        """;

    public KnowledgeDocument create(KnowledgeDocument document) {
        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
        }
        Instant now = Instant.now();
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
        if (document.getUpdatedAt() == null) {
            document.setUpdatedAt(now);
        }
        if (document.getVersion() == 0) {
            document.setVersion(1);
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, document.getId());
            ps.setObject(2, document.getTenantId());
            ps.setString(3, document.getTitle());
            ps.setString(4, document.getSourceType() != null ? document.getSourceType().name() : SourceType.MANUAL.name());
            ps.setString(5, document.getSourceUri());
            ps.setString(6, document.getStatus() != null ? document.getStatus().name() : DocumentStatus.PENDING.name());
            ps.setInt(7, document.getVersion());
            ps.setString(8, document.getChecksum());
            ps.setTimestamp(9, Timestamp.from(document.getCreatedAt()));
            ps.setTimestamp(10, Timestamp.from(document.getUpdatedAt()));

            ps.executeUpdate();
            logger.debug("Created KnowledgeDocument: {} title: '{}' tenant: {}", document.getId(), document.getTitle(), document.getTenantId());
            return document;
        } catch (SQLException e) {
            logger.error("Failed to create KnowledgeDocument for tenant {}", document.getTenantId(), e);
            throw new DataAccessException("Error creating KnowledgeDocument", e);
        }
    }

    public Optional<KnowledgeDocument> findById(UUID id, UUID tenantId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {

            ps.setObject(1, id);
            ps.setObject(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToKnowledgeDocument(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            logger.error("Failed to find KnowledgeDocument by id {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error finding KnowledgeDocument by ID", e);
        }
    }

    public List<KnowledgeDocument> findAll(UUID tenantId) {
        List<KnowledgeDocument> docs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL)) {

            ps.setObject(1, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(mapResultSetToKnowledgeDocument(rs));
                }
            }
            return docs;
        } catch (SQLException e) {
            logger.error("Failed to find all KnowledgeDocuments for tenant {}", tenantId, e);
            throw new DataAccessException("Error finding KnowledgeDocuments", e);
        }
    }

    public List<KnowledgeDocument> findByStatus(UUID tenantId, DocumentStatus status) {
        List<KnowledgeDocument> docs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_STATUS_SQL)) {

            ps.setObject(1, tenantId);
            ps.setString(2, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(mapResultSetToKnowledgeDocument(rs));
                }
            }
            return docs;
        } catch (SQLException e) {
            logger.error("Failed to find KnowledgeDocuments by status {} for tenant {}", status, tenantId, e);
            throw new DataAccessException("Error finding KnowledgeDocuments by status", e);
        }
    }

    public boolean updateStatus(UUID id, UUID tenantId, DocumentStatus status) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {

            ps.setString(1, status.name());
            ps.setObject(2, id);
            ps.setObject(3, tenantId);

            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            logger.error("Failed to update status for KnowledgeDocument {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error updating KnowledgeDocument status", e);
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
            logger.error("Failed to delete KnowledgeDocument {} tenant {}", id, tenantId, e);
            throw new DataAccessException("Error deleting KnowledgeDocument", e);
        }
    }

    private KnowledgeDocument mapResultSetToKnowledgeDocument(ResultSet rs) throws SQLException {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(rs.getObject("id", UUID.class));
        doc.setTenantId(rs.getObject("tenant_id", UUID.class));
        doc.setTitle(rs.getString("title"));

        String sourceStr = rs.getString("source_type");
        if (sourceStr != null) {
            doc.setSourceType(SourceType.valueOf(sourceStr));
        }

        doc.setSourceUri(rs.getString("source_uri"));

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            doc.setStatus(DocumentStatus.valueOf(statusStr));
        }

        doc.setVersion(rs.getInt("version"));
        doc.setChecksum(rs.getString("checksum"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            doc.setCreatedAt(created.toInstant());
        }

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            doc.setUpdatedAt(updated.toInstant());
        }

        return doc;
    }
}
