-- =====================================================================
-- Migration 006: knowledge_chunks
-- Purpose: A document split into retrieval-sized pieces. RAG never
--          embeds/searches a whole document at once — this table is
--          the unit of retrieval, and the parent of embeddings (1:1).
-- =====================================================================

CREATE TABLE knowledge_chunks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    tenant_id     UUID NOT NULL,
    chunk_index   INT NOT NULL,
    content       TEXT NOT NULL,
    token_count   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_knowledge_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_knowledge_chunks_tenant
    ON knowledge_chunks (tenant_id);

CREATE INDEX idx_knowledge_chunks_content_fts
    ON knowledge_chunks USING GIN (to_tsvector('english', content));
