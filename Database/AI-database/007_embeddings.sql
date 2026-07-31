-- =====================================================================
-- Migration 007: embeddings
-- Purpose: Vector representation of each knowledge_chunk, used by the
--          RAG module for similarity search via pgvector. Kept as its
--          own table (rather than a column on knowledge_chunks) so the
--          embedding model/dimension can evolve independently, and so
--          re-embedding (e.g. after a model upgrade) is a targeted
--          operation on a small, hot table.
-- =====================================================================

-- Dimension below (1536) matches common OpenAI/Anthropic-compatible
-- embedding models (e.g. text-embedding-3-small). Adjust to match
-- whichever embedding model is actually configured; changing the
-- dimension requires a new migration + full re-embed, so pin it
-- deliberately per deployment.
CREATE TABLE embeddings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id         UUID NOT NULL UNIQUE REFERENCES knowledge_chunks(id) ON DELETE CASCADE,
    tenant_id        UUID NOT NULL,
    embedding_model  VARCHAR(100) NOT NULL,
    embedding        VECTOR(1536) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_embeddings_tenant
    ON embeddings (tenant_id);

CREATE INDEX idx_embeddings_vector_hnsw
    ON embeddings USING hnsw (embedding vector_cosine_ops);


-- ---------------------------------------------------------------------
-- Example tenant-scoped similarity query (for reference only — not
-- executed by this migration):
--
-- SELECT kc.content, e.embedding <=> :query_vector AS distance
-- FROM embeddings e
-- JOIN knowledge_chunks kc ON kc.id = e.chunk_id
-- WHERE e.tenant_id = :tenant_id
-- ORDER BY e.embedding <=> :query_vector
-- LIMIT 8;
-- ---------------------------------------------------------------------
