package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code knowledge_chunks}.
 *
 * The unit of retrieval for RAG — a document is split into ordered
 * chunks, and it is chunks (not whole documents) that get embedded
 * and searched. {@code documentId} points back to {@link KnowledgeDocument};
 * {@link Embedding} points forward to this class by {@code chunkId}.
 */
public class KnowledgeChunk {

    private UUID id;
    private UUID documentId;
    private UUID tenantId;
    private int chunkIndex;
    private String content;
    private Integer tokenCount;
    private Instant createdAt;

    public KnowledgeChunk() {
    }

    public KnowledgeChunk(UUID id,
                           UUID documentId,
                           UUID tenantId,
                           int chunkIndex,
                           String content,
                           Integer tokenCount,
                           Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeChunk)) {
            return false;
        }
        KnowledgeChunk that = (KnowledgeChunk) o;
        return chunkIndex == that.chunkIndex
                && Objects.equals(id, that.id)
                && Objects.equals(documentId, that.documentId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(content, that.content)
                && Objects.equals(tokenCount, that.tokenCount)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, documentId, tenantId, chunkIndex, content,
                tokenCount, createdAt);
    }

    @Override
    public String toString() {
        return "KnowledgeChunk{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", tenantId=" + tenantId +
                ", chunkIndex=" + chunkIndex +
                ", content='" + content + '\'' +
                ", tokenCount=" + tokenCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
