-- =====================================================================
-- Migration 005: knowledge_documents
-- Purpose: Source-of-truth registry for RAG source material (FAQs,
--          policy PDFs, product docs) before it's chunked/embedded.
--          Tracks ingestion lifecycle independently of the chunks
--          themselves, so re-ingestion / versioning is straightforward.
-- =====================================================================

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

