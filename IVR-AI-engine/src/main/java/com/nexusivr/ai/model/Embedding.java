package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code embeddings}.
 *
 * The vector representation of exactly one {@link KnowledgeChunk}
 * (1:1 — enforced at the database level by a UNIQUE constraint on
 * {@code chunk_id}). Kept as its own class/table rather than a field
 * on {@code KnowledgeChunk} so the embedding model and vector
 * dimension can change independently of chunk content.
 *
 * {@code embedding} is a {@code float[]} because it maps directly to a
 * pgvector column — this class does not depend on any pgvector/JDBC
 * driver type, keeping it a genuinely plain, persistence-agnostic model.
 */
public class Embedding {

    private UUID id;
    private UUID chunkId;
    private UUID tenantId;
    private String embeddingModel;
    private float[] embedding;
    private Instant createdAt;

    public Embedding() {
    }

    public Embedding(UUID id,
                      UUID chunkId,
                      UUID tenantId,
                      String embeddingModel,
                      float[] embedding,
                      Instant createdAt) {
        this.id = id;
        this.chunkId = chunkId;
        this.tenantId = tenantId;
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
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
        if (!(o instanceof Embedding)) {
            return false;
        }
        Embedding that = (Embedding) o;
        return Objects.equals(id, that.id)
                && Objects.equals(chunkId, that.chunkId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(embeddingModel, that.embeddingModel)
                && Arrays.equals(embedding, that.embedding)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, chunkId, tenantId, embeddingModel, createdAt);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }

    @Override
    public String toString() {
        return "Embedding{" +
                "id=" + id +
                ", chunkId=" + chunkId +
                ", tenantId=" + tenantId +
                ", embeddingModel='" + embeddingModel + '\'' +
                ", embedding=" + (embedding == null ? "null" : "float[" + embedding.length + "]") +
                ", createdAt=" + createdAt +
                '}';
    }
}
