-- =============================================================================
-- Migration: 012_sip_extensions.sql
-- Description: Create sip_extensions table for tenant SIP extension management
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS sip_extensions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    extension_number    VARCHAR(20) NOT NULL,
    display_name        VARCHAR(100) NOT NULL,
    assigned_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    sip_password        VARCHAR(255) NOT NULL,
    tls_enabled         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_extension UNIQUE (tenant_id, extension_number)
);

CREATE INDEX IF NOT EXISTS idx_sip_extensions_tenant ON sip_extensions(tenant_id);

CREATE OR REPLACE FUNCTION update_sip_extensions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sip_extensions_updated_at ON sip_extensions;
CREATE TRIGGER trg_sip_extensions_updated_at
BEFORE UPDATE ON sip_extensions
FOR EACH ROW
EXECUTE FUNCTION update_sip_extensions_updated_at();

-- Seed initial extensions for default tenant if they don't exist
INSERT INTO sip_extensions (id, tenant_id, extension_number, display_name, sip_password, tls_enabled)
VALUES
    ('c0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '1001', 'Alex Rivera', '1234', false),
    ('c0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '1002', 'Sarah Chen', '1234', true)
ON CONFLICT (tenant_id, extension_number) DO NOTHING;
