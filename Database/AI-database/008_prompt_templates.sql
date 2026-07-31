-- =====================================================================
-- Migration 008: prompt_templates
-- Purpose: Versioned, tenant-customizable prompt storage. Treats
--          prompts as managed, auditable data rather than hardcoded
--          strings, so brand voice / language / compliance disclaimers
--          can differ per tenant without a code change.
-- =====================================================================

CREATE TABLE prompt_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID,  -- NULL = global default
    module        VARCHAR(50) NOT NULL
        CHECK (module IN ('ASSISTANT','RAG','SUMMARY')),
    template_key  VARCHAR(150) NOT NULL,
    version       INT NOT NULL DEFAULT 1,
    content       TEXT NOT NULL,
    variables     JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial unique indexes because a plain UNIQUE constraint treats every
-- NULL tenant_id as distinct, which would allow duplicate global templates.
CREATE UNIQUE INDEX uq_prompt_templates_tenant_scoped
    ON prompt_templates (tenant_id, module, template_key, version)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_prompt_templates_global
    ON prompt_templates (module, template_key, version)
    WHERE tenant_id IS NULL;

CREATE INDEX idx_prompt_templates_active
    ON prompt_templates (module, template_key)
    WHERE is_active = true;

CREATE TRIGGER trg_prompt_templates_updated_at
    BEFORE UPDATE ON prompt_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
