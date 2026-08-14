-- =============================================================================
-- Migration: 013_queue_management.sql
-- Description: Create queues, queue_members, and agent_states tables for Queue Management
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. queues table
CREATE TABLE IF NOT EXISTS queues (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                 VARCHAR(100) NOT NULL,
    strategy             VARCHAR(30) NOT NULL DEFAULT 'round_robin'
        CHECK (strategy IN ('round_robin', 'least_recent', 'ring_all', 'linear')),
    wrap_up_time_seconds INT NOT NULL DEFAULT 15,
    max_wait_seconds     INT NOT NULL DEFAULT 300,
    music_on_hold        VARCHAR(50) NOT NULL DEFAULT 'default',
    overflow_action      VARCHAR(100) NOT NULL DEFAULT 'voicemail',
    business_hours       JSONB DEFAULT '{"mon_fri": {"open": "08:00", "close": "18:00"}}'::jsonb,
    status               VARCHAR(20) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'inactive')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_queue_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_queues_tenant ON queues(tenant_id);

-- 2. queue_members table
CREATE TABLE IF NOT EXISTS queue_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id   UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    agent_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    penalty    INT NOT NULL DEFAULT 0,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_queue_agent UNIQUE (queue_id, agent_id)
);

CREATE INDEX IF NOT EXISTS idx_queue_members_queue ON queue_members(queue_id);
CREATE INDEX IF NOT EXISTS idx_queue_members_agent ON queue_members(agent_id);

-- 3. agent_states table
CREATE TABLE IF NOT EXISTS agent_states (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    current_state    VARCHAR(20) NOT NULL DEFAULT 'available'
        CHECK (current_state IN ('available', 'in_call', 'paused', 'offline')),
    state_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_queue_id UUID REFERENCES queues(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_states_agent ON agent_states(agent_id);

-- Updated_at trigger for queues
CREATE OR REPLACE FUNCTION update_queues_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_queues_updated_at ON queues;
CREATE TRIGGER trg_queues_updated_at
BEFORE UPDATE ON queues
FOR EACH ROW
EXECUTE FUNCTION update_queues_updated_at();

-- Seed default queue for default tenant
INSERT INTO queues (id, tenant_id, name, strategy, wrap_up_time_seconds, max_wait_seconds, music_on_hold, overflow_action, status)
VALUES
    ('d0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Support L1', 'round_robin', 15, 300, 'default', 'voicemail', 'active'),
    ('d0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Sales Queue', 'least_recent', 10, 180, 'default', 'voicemail', 'active')
ON CONFLICT (tenant_id, name) DO NOTHING;

-- Seed default agent state for tenant user
INSERT INTO agent_states (agent_id, current_state, state_changed_at)
VALUES
    ('a0000000-0000-0000-0000-000000000002', 'available', now())
ON CONFLICT (agent_id) DO NOTHING;

-- Seed queue member
INSERT INTO queue_members (queue_id, agent_id, penalty)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 0)
ON CONFLICT (queue_id, agent_id) DO NOTHING;
