-- ============================================================================
-- NexusIVR Telephony Analytics Schema
-- PostgreSQL 15+
-- Tables: call_logs, call_events
-- ============================================================================

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

CREATE TABLE IF NOT EXISTS call_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  VARCHAR(255) NOT NULL REFERENCES call_logs(session_id) ON DELETE CASCADE,
    event_type  VARCHAR(50) NOT NULL,               -- 'MENU_SELECTION', 'FORM_SUBMIT', etc.
    node_name   VARCHAR(100) NOT NULL,              -- E.g., 'Support', 'Sales', 'Billing'
    event_time  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_call_logs_tenant_start ON call_logs (tenant_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_call_logs_status ON call_logs (status);

-- Seed initial call history records for default tenant
INSERT INTO call_logs (id, session_id, tenant_id, caller_id, scenario_name, status, start_time, end_time, duration, last_node)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'sess-1001', '11111111-1111-1111-1111-111111111111', '+1 (555) 234-5678', 'Support L1', 'ANSWERED', now() - INTERVAL '12 minutes', now() - INTERVAL '8 minutes', 240, 'Support_Agent'),
    ('f0000000-0000-0000-0000-000000000002', 'sess-1002', '11111111-1111-1111-1111-111111111111', '+1 (555) 876-5432', 'Sales Queue', 'MISSED', now() - INTERVAL '25 minutes', now() - INTERVAL '25 minutes', 0, 'Timeout'),
    ('f0000000-0000-0000-0000-000000000003', 'sess-1003', '11111111-1111-1111-1111-111111111111', '+1 (555) 345-6789', 'Support L1', 'ANSWERED', now() - INTERVAL '40 minutes', now() - INTERVAL '35 minutes', 300, 'Resolved'),
    ('f0000000-0000-0000-0000-000000000004', 'sess-1004', '11111111-1111-1111-1111-111111111111', '+1 (555) 987-6543', 'Billing', 'ANSWERED', now() - INTERVAL '1 hour', now() - INTERVAL '58 minutes', 120, 'Payment_Success'),
    ('f0000000-0000-0000-0000-000000000005', 'sess-1005', '11111111-1111-1111-1111-111111111111', '+1 (555) 456-7890', 'Support L1', 'MISSED', now() - INTERVAL '2 hours', now() - INTERVAL '2 hours', 0, 'Abandon'),
    ('f0000000-0000-0000-0000-000000000006', 'sess-1006', '11111111-1111-1111-1111-111111111111', '+1 (555) 654-3210', 'Sales Queue', 'ANSWERED', now() - INTERVAL '3 hours', now() - INTERVAL '2 hours 55 minutes', 300, 'Sales_Closed'),
    ('f0000000-0000-0000-0000-000000000007', 'sess-1007', '11111111-1111-1111-1111-111111111111', '+1 (555) 111-2233', 'Support L1', 'ANSWERED', now() - INTERVAL '4 hours', now() - INTERVAL '3 hours 56 minutes', 240, 'Support_Agent')
ON CONFLICT (id) DO NOTHING;

-- Seed call_events for distribution chart
INSERT INTO call_events (id, session_id, event_type, node_name, event_time)
VALUES
    ('f1000000-0000-0000-0000-000000000001', 'sess-1001', 'MENU_SELECTION', 'Support', now() - INTERVAL '12 minutes'),
    ('f1000000-0000-0000-0000-000000000002', 'sess-1002', 'MENU_SELECTION', 'Sales', now() - INTERVAL '25 minutes'),
    ('f1000000-0000-0000-0000-000000000003', 'sess-1003', 'MENU_SELECTION', 'Support', now() - INTERVAL '40 minutes'),
    ('f1000000-0000-0000-0000-000000000004', 'sess-1004', 'MENU_SELECTION', 'Billing', now() - INTERVAL '1 hour'),
    ('f1000000-0000-0000-0000-000000000005', 'sess-1006', 'MENU_SELECTION', 'Sales', now() - INTERVAL '3 hours')
ON CONFLICT (id) DO NOTHING;
