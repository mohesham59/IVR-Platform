-- ============================================================================
-- Migration 015: audit_logs
-- Purpose: Security & compliance audit trail tracking platform-wide and
--          tenant-specific events (logins, company creation, IVR publishes,
--          subscription upgrades, role changes).
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID REFERENCES tenants(id) ON DELETE CASCADE,
    actor_user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_email        VARCHAR(255),
    action_type        VARCHAR(100) NOT NULL,
    target_entity_type VARCHAR(100),
    target_entity_id   VARCHAR(255),
    details            JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address         VARCHAR(45),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- Seed Data for Initial Audit View
INSERT INTO audit_logs (id, tenant_id, actor_email, action_type, target_entity_type, target_entity_id, details, ip_address, created_at)
VALUES 
(
    'e0000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'admin@nexusivr.com',
    'COMPANY_CREATED',
    'TENANT',
    '11111111-1111-1111-1111-111111111111',
    '{"name": "Default Enterprise Tenant", "plan": "Enterprise"}'::jsonb,
    '127.0.0.1',
    now() - INTERVAL '2 hours'
),
(
    'e0000000-0000-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'user@nexusivr.com',
    'USER_LOGIN_SUCCESS',
    'USER',
    'a0000000-0000-0000-0000-000000000002',
    '{"browser": "Chrome/Linux"}'::jsonb,
    '192.168.1.50',
    now() - INTERVAL '1 hour'
),
(
    'e0000000-0000-0000-0000-000000000003',
    '11111111-1111-1111-1111-111111111111',
    'user@nexusivr.com',
    'IVR_PUBLISHED',
    'FLOW',
    'support_flow_v3',
    '{"version": 3, "nodes": 12}'::jsonb,
    '192.168.1.50',
    now() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO NOTHING;
