-- ============================================================================
-- NexusIVR AI Module — MVP Database Schema (combined)
-- PostgreSQL 15+, pgvector 0.5+
-- Tables: ai_sessions, ai_messages, conversation_history,
--         knowledge_documents, knowledge_chunks, embeddings, prompt_templates
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 000: Extensions & shared helper
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "vector";     -- pgvector

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- 001: ai_sessions
-- ---------------------------------------------------------------------------
CREATE TABLE ai_sessions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    channel                VARCHAR(20) NOT NULL
        CHECK (channel IN ('VOICE','CHAT','WHATSAPP','WEB_WIDGET','SMS')),
    external_reference_id  VARCHAR(255),
    customer_identifier    VARCHAR(255),
    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','ENDED','ABANDONED','ERROR')),
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at               TIMESTAMPTZ,
    metadata               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_sessions_ended_after_started
        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_ai_sessions_tenant_status
    ON ai_sessions (tenant_id, status);

CREATE INDEX idx_ai_sessions_tenant_started_at
    ON ai_sessions (tenant_id, started_at DESC);

CREATE INDEX idx_ai_sessions_tenant_customer
    ON ai_sessions (tenant_id, customer_identifier);

CREATE UNIQUE INDEX uq_ai_sessions_tenant_external_ref
    ON ai_sessions (tenant_id, external_reference_id)
    WHERE external_reference_id IS NOT NULL;

CREATE TRIGGER trg_ai_sessions_updated_at
    BEFORE UPDATE ON ai_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 002: ai_messages
-- ---------------------------------------------------------------------------
CREATE TABLE ai_messages (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     UUID NOT NULL REFERENCES ai_sessions(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL,
    turn_number    INT NOT NULL,
    role           VARCHAR(20) NOT NULL
        CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
    content        TEXT NOT NULL,
    model_used     VARCHAR(100),
    tokens_input   INT,
    tokens_output  INT,
    metadata       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_messages_session_turn UNIQUE (session_id, turn_number)
);

CREATE INDEX idx_ai_messages_session_turn
    ON ai_messages (session_id, turn_number);

CREATE INDEX idx_ai_messages_tenant_created_at
    ON ai_messages (tenant_id, created_at DESC);

CREATE INDEX idx_ai_messages_content_fts
    ON ai_messages USING GIN (to_tsvector('english', content));

-- ---------------------------------------------------------------------------
-- 003: conversation_history
-- ---------------------------------------------------------------------------
CREATE TABLE conversation_history (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    customer_identifier   VARCHAR(255) NOT NULL,
    session_id            UUID REFERENCES ai_sessions(id) ON DELETE SET NULL,
    summary_text          TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_history_tenant_customer
    ON conversation_history (tenant_id, customer_identifier, created_at DESC);

-- ---------------------------------------------------------------------------
-- 004: knowledge_documents
-- ---------------------------------------------------------------------------
CREATE TABLE knowledge_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,
    title        VARCHAR(500) NOT NULL,
    source_type  VARCHAR(20) NOT NULL
        CHECK (source_type IN ('UPLOAD','URL','API','MANUAL')),
    source_uri   TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','INGESTING','INGESTED','FAILED')),
    version      INT NOT NULL DEFAULT 1,
    checksum     VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_documents_tenant_status
    ON knowledge_documents (tenant_id, status);

CREATE UNIQUE INDEX uq_knowledge_documents_tenant_checksum
    ON knowledge_documents (tenant_id, checksum)
    WHERE checksum IS NOT NULL;

CREATE TRIGGER trg_knowledge_documents_updated_at
    BEFORE UPDATE ON knowledge_documents
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 005: knowledge_chunks
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 006: embeddings (pgvector)
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 007: prompt_templates
-- ---------------------------------------------------------------------------
CREATE TABLE prompt_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID,  -- NULL = global default
    module        VARCHAR(50) NOT NULL
        CHECK (module IN ('ASSISTANT','RAG','SUMMARY')),
    template_key  VARCHAR(150) NOT NULL,
    version       INT NOT NULL DEFAULT 1,
    content       TEXT NOT NULL,
    variables     JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial unique indexes because a plain UNIQUE constraint treats every
-- NULL tenant_id as distinct, which would allow duplicate global templates.
CREATE UNIQUE INDEX uq_prompt_templates_tenant_scoped
    ON prompt_templates (tenant_id, module, template_key, version)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_prompt_templates_global
    ON prompt_templates (module, template_key, version)
    WHERE tenant_id IS NULL;

CREATE INDEX idx_prompt_templates_active
    ON prompt_templates (module, template_key)
    WHERE is_active = true;

CREATE TRIGGER trg_prompt_templates_updated_at
    BEFORE UPDATE ON prompt_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
