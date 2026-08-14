-- =============================================================================
-- Migration: 014_voice_prompts.sql
-- Description: Create voice_prompts table and seed initial audio prompt records
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS voice_prompts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    language    VARCHAR(50) NOT NULL DEFAULT 'en-US',
    duration    VARCHAR(20) DEFAULT '0:15',
    type        VARCHAR(50) NOT NULL DEFAULT 'Uploaded',
    created_by  VARCHAR(255) NOT NULL DEFAULT 'Admin',
    file_path   TEXT NOT NULL,
    size_bytes  BIGINT DEFAULT 102400,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_voice_prompts_tenant ON voice_prompts(tenant_id);

-- Seed initial voice prompts for default tenant
INSERT INTO voice_prompts (id, tenant_id, name, language, duration, type, created_by, file_path, size_bytes)
VALUES
    ('e0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Welcome Greeting', 'en-US', '0:14', 'AI Generated', 'Admin', '/prompts/welcome.wav', 142000),
    ('e0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Main Menu Prompt', 'en-US', '0:22', 'Uploaded', 'Admin', '/prompts/main_menu.wav', 220000),
    ('e0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'After-Hours Announcement', 'en-US', '0:18', 'AI Generated', 'Admin', '/prompts/after_hours.wav', 185000)
ON CONFLICT (id) DO NOTHING;
