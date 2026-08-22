-- ============================================================================
-- NexusIVR Authentication & Multi-Tenant Schema
-- PostgreSQL 15+
-- Tables: tenants, users
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- 1. tenants
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name  VARCHAR(255),
    owner_user_id UUID,
    status        VARCHAR(20) NOT NULL DEFAULT 'INACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active_tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL DEFAULT NULL,
    email            VARCHAR(255) UNIQUE NOT NULL,
    password         TEXT NOT NULL,
    is_superadmin    BOOLEAN NOT NULL DEFAULT false,
    username         VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Foreign key back to users for owner_user_id (circular dependency handled)
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS fk_tenants_owner_user;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_active_tenant ON users(active_tenant_id);

-- ---------------------------------------------------------------------------
-- Seed Data
-- ---------------------------------------------------------------------------
INSERT INTO tenants (id, display_name, status, created_at, updated_at)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'Default Enterprise Tenant', 'ACTIVE', now(), now()),
  ('00000000-0000-0000-0000-000000000001', 'Demo Test Tenant', 'ACTIVE', now(), now())
ON CONFLICT (id) DO UPDATE SET display_name = COALESCE(tenants.display_name, EXCLUDED.display_name);

-- SuperAdmin user
INSERT INTO users (id, active_tenant_id, email, password, is_superadmin, username, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    NULL,
    'admin@nexusivr.com',
    'admin',
    true,
    'Admin',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (email) DO UPDATE SET active_tenant_id = NULL, password = 'admin';

-- Tenant User
INSERT INTO users (id, active_tenant_id, email, password, is_superadmin, username, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'user@nexusivr.com',
    'user',
    false,
    'User',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (email) DO UPDATE SET active_tenant_id = EXCLUDED.active_tenant_id, password = 'user';

-- Link owner user to tenant
UPDATE tenants SET owner_user_id = 'a0000000-0000-0000-0000-000000000002' WHERE id = '11111111-1111-1111-1111-111111111111';

-- ---------------------------------------------------------------------------
-- 3. notifications
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    message     TEXT NOT NULL,
    link_url    VARCHAR(255),
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    type        VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant ON notifications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
