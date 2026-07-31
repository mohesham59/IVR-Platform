-- =====================================================================
-- Migration 002: ai_sessions
-- Purpose: One row per live interaction (a voice call, chat session,
--          WhatsApp thread). This is the root aggregate every other
--          conversational table hangs off of via session_id.
-- =====================================================================

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
