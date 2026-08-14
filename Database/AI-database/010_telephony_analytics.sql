-- ============================================================================
-- NexusIVR Telephony Analytics Schema
-- PostgreSQL 15+
-- Tables: call_logs, call_events
-- ============================================================================

-- Track telephony calls and outcomes
CREATE TABLE IF NOT EXISTS call_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    VARCHAR(255) NOT NULL UNIQUE,       -- Links to agi_session_id
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    caller_id     VARCHAR(50) NOT NULL,              -- Caller phone number / SIP extension
    scenario_name VARCHAR(100) NOT NULL,             -- The VXML scenario being executed
    status        VARCHAR(20) NOT NULL                -- 'ANSWERED', 'MISSED', 'BUSY', 'FAILED', 'IN_PROGRESS'
        CHECK (status IN ('ANSWERED', 'MISSED', 'BUSY', 'FAILED', 'IN_PROGRESS')),
    start_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_time      TIMESTAMPTZ,
    duration      INT DEFAULT 0,                      -- Duration in seconds
    last_node     VARCHAR(100)                        -- Last menu node visited before hangup
);

-- Track path choices for the "Call Distribution" pie chart
CREATE TABLE IF NOT EXISTS call_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  VARCHAR(255) NOT NULL REFERENCES call_logs(session_id) ON DELETE CASCADE,
    event_type  VARCHAR(50) NOT NULL,               -- 'MENU_SELECTION', 'FORM_SUBMIT', etc.
    node_name   VARCHAR(100) NOT NULL,              -- E.g., 'Support', 'Sales', 'Billing'
    event_time  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_call_logs_tenant_start ON call_logs (tenant_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_call_logs_status ON call_logs (status);
