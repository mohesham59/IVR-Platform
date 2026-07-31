-- =====================================================================
-- Migration 003: ai_messages
-- Purpose: Turn-by-turn record of everything said in a session, by
--          both the caller and the assistant (and system/function
--          entries). This is the short-term "working memory" ledger
--          the Conversation Memory module reads/writes, and the raw
--          material Call Summary and Analytics are built from.
-- =====================================================================

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

-- NOTE ON DELETE behavior: CASCADE from ai_sessions keeps referential
-- simplicity for a dev/staging environment. In production, prefer a
-- retention/archival job over hard deletes so historical transcripts
-- remain available for audit even after a session record is purged.
