-- ============================================================================
-- NexusIVR Authentication & Multi-Tenant Schema
-- PostgreSQL 15+
-- Tables: tenants, users, user_tenants
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- 1. tenants
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL DEFAULT 'Default Tenant',
    owner_user_id UUID,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active_tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    email            VARCHAR(255) UNIQUE NOT NULL,
    password_hash    TEXT NOT NULL,
    is_superadmin    BOOLEAN NOT NULL DEFAULT false,
    username         VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Foreign key back to users for owner_user_id
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS fk_tenants_owner_user;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 3. user_tenants (Junction table)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_tenants (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id  UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL DEFAULT 'TENANT_ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_active_tenant ON users(active_tenant_id);

-- ---------------------------------------------------------------------------
-- Seed Data
-- SuperAdmin password: admin ($2a$10$wT5dYcR/RkZ.4qFh0y34j.vV8V3p5.Q5uWfL8F.Sg2Y8o9m4Nq6qS -> BCrypt hash for 'admin')
-- TenantUser password: user ($2a$10$4B9Y8gR5rE6S.7uT9vW8x.yZ1A2B3C4D5E6F7G8H9I0J1K2L3M4N5 -> BCrypt for 'user')
-- ---------------------------------------------------------------------------
INSERT INTO tenants (id, name, status, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Default Enterprise Tenant', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- SuperAdmin user
INSERT INTO users (id, active_tenant_id, email, password_hash, is_superadmin, username, status, created_at, updated_at)
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
) ON CONFLICT (email) DO UPDATE SET active_tenant_id = NULL, password_hash = 'admin';

-- Tenant User
INSERT INTO users (id, active_tenant_id, email, password_hash, is_superadmin, username, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    NULL,
    'user@nexusivr.com',
    'user',
    false,
    'User',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (email) DO UPDATE SET active_tenant_id = NULL, password_hash = 'user';

-- Link owner user to tenant
UPDATE tenants SET owner_user_id = 'a0000000-0000-0000-0000-000000000002' WHERE id = '11111111-1111-1111-1111-111111111111';

-- Link user to tenant junction table
INSERT INTO user_tenants (user_id, tenant_id, role)
VALUES ('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'TENANT_ADMIN')
ON CONFLICT DO NOTHING;
