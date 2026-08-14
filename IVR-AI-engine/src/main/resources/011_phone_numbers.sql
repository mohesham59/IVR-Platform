-- ============================================================================
-- NexusIVR Phone Numbers (DID) Management Schema
-- PostgreSQL 15+
-- Table: phone_numbers
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS phone_numbers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    phone_number       VARCHAR(50) NOT NULL,
    country            VARCHAR(10) NOT NULL DEFAULT 'US',
    provider           VARCHAR(50) NOT NULL DEFAULT 'Twilio',
    assigned_flow_id   VARCHAR(255),
    assigned_flow_name VARCHAR(255),
    status             VARCHAR(20) NOT NULL DEFAULT 'UNASSIGNED'
        CHECK (status IN ('ACTIVE', 'UNASSIGNED', 'DISABLED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_phone_numbers_tenant_number UNIQUE (tenant_id, phone_number)
);

CREATE INDEX IF NOT EXISTS idx_phone_numbers_tenant ON phone_numbers(tenant_id);
CREATE INDEX IF NOT EXISTS idx_phone_numbers_status ON phone_numbers(status);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION set_phone_numbers_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_phone_numbers_updated_at ON phone_numbers;
CREATE TRIGGER trg_phone_numbers_updated_at
    BEFORE UPDATE ON phone_numbers
    FOR EACH ROW EXECUTE FUNCTION set_phone_numbers_updated_at();

-- Seed initial phone numbers for Default Enterprise Tenant
INSERT INTO phone_numbers (id, tenant_id, phone_number, country, provider, assigned_flow_id, assigned_flow_name, status)
VALUES
  ('b0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '+1 (415) 882-3301', 'US', 'Twilio', NULL, NULL, 'UNASSIGNED'),
  ('b0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '+1 (312) 445-9921', 'US', 'Twilio', NULL, NULL, 'UNASSIGNED'),
  ('b0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '+1 (617) 230-0084', 'US', 'Vonage', NULL, NULL, 'UNASSIGNED'),
  ('b0000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', '+44 20 7946 0841', 'UK', 'Twilio', NULL, NULL, 'UNASSIGNED')
ON CONFLICT (tenant_id, phone_number) DO NOTHING;
