package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code knowledge_documents}.
 *
 * The source-of-truth registry for material ingested for RAG, before
 * it is split into chunks. {@link KnowledgeChunk} rows reference this
 * class by {@code documentId}.
 */
public class KnowledgeDocument {

    private UUID id;
    private UUID tenantId;
    private String title;
    private SourceType sourceType;
    private String sourceUri;
    private DocumentStatus status;
    private int version;
    private String checksum;
    private Instant createdAt;
    private Instant updatedAt;

    public KnowledgeDocument() {
    }

    public KnowledgeDocument(UUID id,
                              UUID tenantId,
                              String title,
                              SourceType sourceType,
                              String sourceUri,
                              DocumentStatus status,
                              int version,
                              String checksum,
                              Instant createdAt,
                              Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.sourceType = sourceType;
        this.sourceUri = sourceUri;
        this.status = status;
        this.version = version;
        this.checksum = checksum;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeDocument)) {
            return false;
        }
        KnowledgeDocument that = (KnowledgeDocument) o;
        return version == that.version
                && Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(title, that.title)
                && sourceType == that.sourceType
                && Objects.equals(sourceUri, that.sourceUri)
                && status == that.status
                && Objects.equals(checksum, that.checksum)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, title, sourceType, sourceUri,
                status, version, checksum, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "KnowledgeDocument{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", title='" + title + '\'' +
                ", sourceType=" + sourceType +
                ", sourceUri='" + sourceUri + '\'' +
                ", status=" + status +
                ", version=" + version +
                ", checksum='" + checksum + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
