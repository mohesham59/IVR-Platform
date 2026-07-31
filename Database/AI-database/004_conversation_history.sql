-- =====================================================================
-- Migration 004: conversation_history
-- Purpose: Long-term, cross-session memory per customer. Distinct from
--          ai_messages (which is per-session, turn-level) and from
--          call_summary (which is a post-call deliverable for a single
--          session). This table is what lets the Assistant say
--          "last time you called about X..." across multiple sessions.
-- =====================================================================

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
