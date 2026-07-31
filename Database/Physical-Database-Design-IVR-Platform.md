PHYSICAL DATABASE DESIGN
AI-Powered Multi-Tenant IVR Platform
=====================================

**Target RDBMS:** PostgreSQL 16
**Document Version:** 1.0
**Date:** July 16, 2026
**Status:** Draft for Review — implements the approved Logical Database Design (v1.0) verbatim
**Prepared as part of a Graduation Project**

This document converts the approved Logical Database Design into physical PostgreSQL DDL. It introduces **no** new
entities, relationships, cardinalities, normalization decisions, or lookup tables beyond what the logical design
specifies (with the sole exception of implementation-only objects — sequences, triggers, indexes, partitions, and
roles — which carry no business meaning of their own). Every naming, key-strategy, data-type, and constraint
decision below is a direct, literal execution of Steps 5–10 of the logical design. Where the logical design left an
attribute unnamed (it enumerated only "Est. Attrs" counts), a reasonable, clearly-scoped physical column list is
supplied so the DDL is executable; these additions are structural completions, not new business concepts.

---

## 1. Physical Design Conventions

### 1.1 Naming conventions
- Tables: `snake_case`, plural (`call_sessions`, `flow_versions`), exactly as named in Logical Design Step 3.
- Primary key columns: `<singular_entity>_id` (e.g., `tenant_id`, `call_session_id`), not the generic `id`, so that
  every foreign key column is self-describing without a join (`call_sessions.tenant_id` reads the same in both
  tables it appears in).
- Foreign key columns: named after the column they reference (`tenant_id`, `flow_version_id`, `recipient_employee_id`).
- Lookup foreign keys: `<attribute>_code` (e.g., `status_code`, `node_type_code`) referencing the lookup table's
  natural-key `code` column.
- Indexes: `ix_<table>_<column(s)>`. Unique indexes: `uq_<table>_<column(s)>`. Partial unique indexes:
  `uq_<table>_<column(s)>_partial`. Check constraints: `ck_<table>_<rule>`. Foreign keys:
  `fk_<table>_<referenced_table>`.

### 1.2 Standard audit columns
Every business table (all tables except lookup tables, which carry only `sort_order`/`is_active`) carries, per
Logical Design Step 3's note that these are assumed but not enumerated per-entity:

```sql
created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by   UUID NULL REFERENCES users(user_id)   -- omitted on users, tenants, and lookup-adjacent seed tables
```

`updated_at` is maintained by a single shared trigger (Section 3.2), not by application code, so it cannot be
forgotten on any write path.

### 1.3 Data type mapping (Logical Design Step 9 → PostgreSQL)

| Logical decision | PostgreSQL type |
|---|---|
| UUID surrogate key | `UUID DEFAULT gen_random_uuid()` |
| BIGINT identity key (audit_log_entries, analytics_events) | `BIGINT GENERATED ALWAYS AS IDENTITY` |
| Lookup natural key | `VARCHAR(64) PRIMARY KEY` |
| Lookup-backed status/type column | `VARCHAR(64) NOT NULL REFERENCES lkp_x(code)` |
| JSON / JSONB | `JSONB` (always JSONB, never JSON, for indexability) |
| Structured long text | `TEXT` |
| TIMESTAMP WITH TIME ZONE | `TIMESTAMPTZ` |
| BOOLEAN | `BOOLEAN NOT NULL DEFAULT false` |
| Money (amount) | `NUMERIC(14,2)` |
| Money (currency) | `VARCHAR(3) REFERENCES lkp_currency(code)` — ISO 4217; a lookup table implied by Logical Design §9.7's "currency_code lookup-backed column" but not separately enumerated in Step 8, added here for physical completeness only |
| Phone number (DID) | `VARCHAR(20)` (E.164) |
| IETF language tag | `VARCHAR(10)` |

### 1.4 Extensions required

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS btree_gin;  -- composite indexes mixing scalar + jsonb where needed
CREATE EXTENSION IF NOT EXISTS pg_partman; -- optional, for automated partition maintenance on analytics_events
```

### 1.5 Shared trigger function

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```
Attached as `BEFORE UPDATE FOR EACH ROW` on every business table below (omitted per-table for brevity; applies
uniformly).

### 1.6 Schemas
All tables live in a single `public` schema, matching the logical design's flat module grouping (modules are a
documentation/DAO-ownership concept per Logical Design Step 2, not a PostgreSQL schema boundary — introducing 16
Postgres schemas would fragment cross-module foreign keys, e.g. `tenant_id`, for no relational benefit).

---

## 2. Lookup Tables

One shared shape for every `lkp_*` table (Logical Design Step 6.4 / Step 8):

```sql
CREATE TABLE lkp_<name> (
  code        VARCHAR(64)  PRIMARY KEY,
  label       VARCHAR(128) NOT NULL,
  description TEXT         NULL,
  sort_order  SMALLINT     NOT NULL DEFAULT 0,
  is_active   BOOLEAN      NOT NULL DEFAULT true
);
```

The 38 lookup tables instantiated below are exactly those enumerated in Logical Design Step 8, plus `lkp_currency`
(implied by Step 9.7, see 1.3 above):

```sql
CREATE TABLE lkp_user_status              (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_role_scope                (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_tenant_status             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_industry_type             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_department_status         (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_employee_status           (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_extension_status          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_registration_status       (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_trunk_status              (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_did_status                (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_flow_status               (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_version_status            (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_node_type                 (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_call_disposition          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_call_session_state        (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_prompt_source             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_prompt_status             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_language                  (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_queue_strategy            (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_queue_status              (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_overflow_action           (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_recording_status          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_consent_status            (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_consent_method            (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_voicemail_status          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_report_type               (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_report_format             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_ai_request_type           (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_ai_job_status             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_notification_channel      (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_notification_status       (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_environment_type          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_deployment_status         (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_health_component          (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_health_status             (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_alert_severity            (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_usage_metric_type         (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_invoice_payment_status    (LIKE lkp_template INCLUDING ALL);
CREATE TABLE lkp_currency                  (LIKE lkp_template INCLUDING ALL);
```
(`lkp_template` is a shorthand for the shape above; in the physical migration script each is created explicitly
from the full DDL, not via `LIKE`, since Postgres does not support `LIKE` against a table that isn't already
created — shown compactly here to avoid repeating 38 identical bodies.)

`lkp_permission` and `lkp_skill` are **not** generic status/type lookups — they are the governed catalogs from
Logical Design Step 1.5 and carry their own shape:

```sql
CREATE TABLE lkp_permission (
  code        VARCHAR(128) PRIMARY KEY,   -- e.g. 'ivr.flow.publish', 'tenant.suspend'
  label       VARCHAR(128) NOT NULL,
  description TEXT NULL,
  module      VARCHAR(64)  NOT NULL,      -- which of the 16 modules this capability governs
  sort_order  SMALLINT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE lkp_skill (
  code        VARCHAR(64) PRIMARY KEY,    -- e.g. 'ARABIC', 'BILLING', 'TECHNICAL', 'VIP'
  label       VARCHAR(128) NOT NULL,
  description TEXT NULL,
  sort_order  SMALLINT NOT NULL DEFAULT 0,
  is_active   BOOLEAN NOT NULL DEFAULT true
);
```

---

## 3. Module DDL

Each module below implements exactly the tables assigned to it in Logical Design Step 2, with the entities,
attributes, keys, and relationships from Steps 3–7.

### 3.1 Identity & Access (IAM)
`users, roles, permissions(→lkp_permission), role_permissions, user_roles, sessions, api_keys`

```sql
CREATE TABLE users (
  user_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NULL REFERENCES tenants(tenant_id),         -- nullable: Super Admin (Step 7.1)
  email             VARCHAR(320) NOT NULL,
  password_hash     TEXT NOT NULL,
  first_name        VARCHAR(100) NOT NULL,
  last_name         VARCHAR(100) NOT NULL,
  status_code       VARCHAR(64) NOT NULL REFERENCES lkp_user_status(code),
  mfa_enabled       BOOLEAN NOT NULL DEFAULT false,                   -- true binary flag, Step 9.4
  last_login_at     TIMESTAMPTZ NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_tenant_email ON users (tenant_id, lower(email));
CREATE INDEX ix_users_tenant_id ON users (tenant_id);

CREATE TABLE roles (
  role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NULL REFERENCES tenants(tenant_id),                -- nullable: platform-defined role, Step 7.1
  name        VARCHAR(100) NOT NULL,
  scope_code  VARCHAR(64) NOT NULL REFERENCES lkp_role_scope(code),
  description TEXT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID NULL REFERENCES users(user_id)
);
CREATE UNIQUE INDEX uq_roles_tenant_name ON roles (tenant_id, name) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_roles_name_platform ON roles (name) WHERE tenant_id IS NULL;

-- 'permissions' realized as the lkp_permission governed catalog (Step 1.5); no separate permissions table.

CREATE TABLE role_permissions (
  role_id         UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
  permission_code VARCHAR(128) NOT NULL REFERENCES lkp_permission(code),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, permission_code)
);
CREATE INDEX ix_role_permissions_permission_code ON role_permissions (permission_code);

CREATE TABLE user_roles (
  user_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  role_id     UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE sessions (
  session_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  token_hash   TEXT NOT NULL,
  ip_address   INET NULL,
  user_agent   TEXT NULL,
  revoked      BOOLEAN NOT NULL DEFAULT false,                        -- true binary flag, Step 9.4
  expires_at   TIMESTAMPTZ NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sessions_user_id ON sessions (user_id);
CREATE INDEX ix_sessions_expires_at ON sessions (expires_at);

CREATE TABLE api_keys (
  api_key_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(tenant_id),
  key_hash      TEXT NOT NULL,
  label         VARCHAR(100) NOT NULL,
  scopes        JSONB NOT NULL DEFAULT '[]',
  last_used_at  TIMESTAMPTZ NULL,
  revoked       BOOLEAN NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID NULL REFERENCES users(user_id)                   -- Step 4: "association, createdBy"
);
CREATE INDEX ix_api_keys_tenant_id ON api_keys (tenant_id);
```

### 3.2 Tenant
`tenants, subscriptions`

```sql
CREATE TABLE tenants (
  tenant_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                    VARCHAR(200) NOT NULL,
  status_code             VARCHAR(64) NOT NULL REFERENCES lkp_tenant_status(code),
  industry_type_code      VARCHAR(64) NOT NULL REFERENCES lkp_industry_type(code),
  timezone                VARCHAR(64) NOT NULL DEFAULT 'UTC',
  default_locale          VARCHAR(10) NOT NULL DEFAULT 'en-US',
  branding_config         JSONB NULL,                                  -- Step 9.1
  default_retention_policy JSONB NULL,                                 -- Step 9.1 (duration_days, action)
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
  subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,  -- composition, Step 4
  plan_name       VARCHAR(100) NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active','Cancelled','Expired')),
  limits          JSONB NOT NULL DEFAULT '{}',                          -- Step 9.1: resource-to-quota map
  starts_at       TIMESTAMPTZ NOT NULL,
  ends_at         TIMESTAMPTZ NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_subscriptions_tenant_active ON subscriptions (tenant_id) WHERE status = 'Active';
```

### 3.3 Organization
`departments, employees, sip_extensions, employee_departments` (+ `employee_skills`, Step 5.5)

```sql
CREATE TABLE departments (
  department_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(tenant_id),
  name          VARCHAR(150) NOT NULL,
  status_code   VARCHAR(64) NOT NULL REFERENCES lkp_department_status(code),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_departments_tenant_id ON departments (tenant_id);

CREATE TABLE employees (
  employee_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL REFERENCES tenants(tenant_id),
  user_id     UUID NULL REFERENCES users(user_id),
  first_name  VARCHAR(100) NOT NULL,
  last_name   VARCHAR(100) NOT NULL,
  status_code VARCHAR(64) NOT NULL REFERENCES lkp_employee_status(code),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_employees_tenant_id ON employees (tenant_id);

CREATE TABLE sip_extensions (
  sip_extension_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id),
  employee_id       UUID NULL REFERENCES employees(employee_id),
  extension_number  VARCHAR(20) NOT NULL,
  status_code       VARCHAR(64) NOT NULL REFERENCES lkp_extension_status(code),
  registration_status_code VARCHAR(64) NOT NULL REFERENCES lkp_registration_status(code),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_sip_extensions_tenant_number ON sip_extensions (tenant_id, extension_number);
CREATE UNIQUE INDEX uq_sip_extensions_employee ON sip_extensions (employee_id) WHERE employee_id IS NOT NULL;

CREATE TABLE employee_departments (
  employee_id   UUID NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
  department_id UUID NOT NULL REFERENCES departments(department_id) ON DELETE CASCADE,
  assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (employee_id, department_id)
  -- NOTE (Logical Design 10.3): do NOT add UNIQUE(employee_id) here — would silently downgrade to
  -- one-to-many; only add if product owner confirms single-department assignment.
);
CREATE INDEX ix_employee_departments_department_id ON employee_departments (department_id);

CREATE TABLE employee_skills (
  employee_id       UUID NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
  skill_code        VARCHAR(64) NOT NULL REFERENCES lkp_skill(code),
  proficiency_level SMALLINT NULL CHECK (proficiency_level BETWEEN 1 AND 5),
  PRIMARY KEY (employee_id, skill_code)
);
CREATE INDEX ix_employee_skills_skill_code ON employee_skills (skill_code);
```

### 3.4 Telephony / SIP Gateway
`sip_trunks, dids`

```sql
CREATE TABLE sip_trunks (
  sip_trunk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NULL REFERENCES tenants(tenant_id),                -- nullable: platform-shared, Step 7.1
  name         VARCHAR(150) NOT NULL,
  provider     VARCHAR(150) NOT NULL,
  status_code  VARCHAR(64) NOT NULL REFERENCES lkp_trunk_status(code),
  config       JSONB NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sip_trunks_tenant_id ON sip_trunks (tenant_id);

CREATE TABLE dids (
  did_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL REFERENCES tenants(tenant_id),
  sip_trunk_id UUID NOT NULL REFERENCES sip_trunks(sip_trunk_id),
  number       VARCHAR(20) NOT NULL,
  status_code  VARCHAR(64) NOT NULL REFERENCES lkp_did_status(code),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_dids_number ON dids (number);
CREATE INDEX ix_dids_tenant_id ON dids (tenant_id);
CREATE INDEX ix_dids_sip_trunk_id ON dids (sip_trunk_id);
```

### 3.5 IVR Design
`ivr_flows, flow_versions, nodes, connections`

```sql
CREATE TABLE ivr_flows (
  ivr_flow_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL REFERENCES tenants(tenant_id),
  name        VARCHAR(150) NOT NULL,
  status_code VARCHAR(64) NOT NULL REFERENCES lkp_flow_status(code),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by  UUID NULL REFERENCES users(user_id)
);
CREATE INDEX ix_ivr_flows_tenant_id ON ivr_flows (tenant_id);

CREATE TABLE flow_versions (
  flow_version_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ivr_flow_id       UUID NOT NULL REFERENCES ivr_flows(ivr_flow_id) ON DELETE CASCADE,
  version_number    INTEGER NOT NULL,
  status_code       VARCHAR(64) NOT NULL REFERENCES lkp_version_status(code),
  validation_result JSONB NULL,
  published_at      TIMESTAMPTZ NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID NULL REFERENCES users(user_id)
);
CREATE UNIQUE INDEX uq_flow_versions_flow_number ON flow_versions (ivr_flow_id, version_number);
CREATE INDEX ix_flow_versions_ivr_flow_id ON flow_versions (ivr_flow_id);

CREATE TABLE nodes (
  node_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  flow_version_id UUID NOT NULL REFERENCES flow_versions(flow_version_id) ON DELETE CASCADE,
  node_type_code  VARCHAR(64) NOT NULL REFERENCES lkp_node_type(code),
  name            VARCHAR(150) NOT NULL,
  configuration   JSONB NOT NULL DEFAULT '{}',    -- Step 9.6/10.4: deliberate JSONB; embedded voice_prompts
                                                   -- references are application-validated, not FK-enforced
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_nodes_flow_version_id ON nodes (flow_version_id);
CREATE INDEX ix_nodes_configuration_gin ON nodes USING GIN (configuration);

CREATE TABLE connections (
  connection_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  flow_version_id  UUID NOT NULL REFERENCES flow_versions(flow_version_id) ON DELETE CASCADE,
  source_node_id   UUID NOT NULL REFERENCES nodes(node_id),
  target_node_id   UUID NOT NULL REFERENCES nodes(node_id),
  condition        JSONB NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
  -- NOTE (Step 7.3): same-flow-version invariant for source/target enforced by FlowValidationService, not by FK.
);
CREATE INDEX ix_connections_flow_version_id ON connections (flow_version_id);
CREATE INDEX ix_connections_source_node_id ON connections (source_node_id);
CREATE INDEX ix_connections_target_node_id ON connections (target_node_id);
```

### 3.6 IVR Execution (Runtime)
`call_sessions`

```sql
CREATE TABLE call_sessions (
  call_session_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL REFERENCES tenants(tenant_id),
  did_id           UUID NOT NULL REFERENCES dids(did_id),
  flow_version_id  UUID NOT NULL REFERENCES flow_versions(flow_version_id),  -- set once at routing time,
                                                                              -- effectively immutable thereafter
                                                                              -- (Step 7.2) — no UPDATE grant to
                                                                              -- application roles on this column
  caller_number    VARCHAR(20) NULL,
  state_code       VARCHAR(64) NOT NULL REFERENCES lkp_call_session_state(code),
  disposition_code VARCHAR(64) NULL REFERENCES lkp_call_disposition(code),
  started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at         TIMESTAMPTZ NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_call_sessions_tenant_id ON call_sessions (tenant_id, started_at);
CREATE INDEX ix_call_sessions_did_id ON call_sessions (did_id);
CREATE INDEX ix_call_sessions_flow_version_id ON call_sessions (flow_version_id);
-- Step 10.5: highest-concurrency write path — candidate for read-replica scaling, not co-located with
-- low-volume administrative tables.
```

### 3.7 Media & Prompt
`voice_prompts`

```sql
CREATE TABLE voice_prompts (
  voice_prompt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id),
  name            VARCHAR(150) NOT NULL,
  source_code     VARCHAR(64) NOT NULL REFERENCES lkp_prompt_source(code),
  status_code     VARCHAR(64) NOT NULL REFERENCES lkp_prompt_status(code),
  language_code   VARCHAR(10) NOT NULL REFERENCES lkp_language(code),
  audio_file_ref  TEXT NOT NULL,                     -- pointer to tenant-partitioned blob storage, Step 10.5
  duration_ms     INTEGER NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID NULL REFERENCES users(user_id)
);
CREATE INDEX ix_voice_prompts_tenant_id ON voice_prompts (tenant_id);
```

### 3.8 Queueing & Routing
`call_queues, queue_memberships`

```sql
CREATE TABLE call_queues (
  call_queue_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
  department_id       UUID NULL REFERENCES departments(department_id),
  name                VARCHAR(150) NOT NULL,
  strategy_code       VARCHAR(64) NOT NULL REFERENCES lkp_queue_strategy(code),
  status_code         VARCHAR(64) NOT NULL REFERENCES lkp_queue_status(code),
  overflow_action_code VARCHAR(64) NOT NULL REFERENCES lkp_overflow_action(code),
  max_wait_seconds    INTEGER NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_call_queues_tenant_id ON call_queues (tenant_id);
CREATE INDEX ix_call_queues_department_id ON call_queues (department_id);

CREATE TABLE queue_memberships (
  call_queue_id UUID NOT NULL REFERENCES call_queues(call_queue_id) ON DELETE CASCADE,  -- composition, Step 4
  employee_id   UUID NOT NULL REFERENCES employees(employee_id),
  priority      SMALLINT NOT NULL DEFAULT 0,        -- per-pair routing weight, Step 5.4
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (call_queue_id, employee_id)
);
CREATE INDEX ix_queue_memberships_employee_id ON queue_memberships (employee_id);
```

### 3.9 Call Records & Recording
`call_detail_records, recordings, voicemails, consent_records`

```sql
CREATE TABLE consent_records (
  consent_record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_session_id   UUID NOT NULL REFERENCES call_sessions(call_session_id),
  status_code       VARCHAR(64) NOT NULL REFERENCES lkp_consent_status(code),
  method_code       VARCHAR(64) NOT NULL REFERENCES lkp_consent_method(code),
  captured_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_consent_records_call_session_id ON consent_records (call_session_id);

CREATE TABLE recordings (
  recording_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_session_id    UUID NOT NULL REFERENCES call_sessions(call_session_id),
  tenant_id          UUID NOT NULL REFERENCES tenants(tenant_id),
  consent_record_id  UUID NULL REFERENCES consent_records(consent_record_id),  -- optional FK, Step 7.5
  status_code        VARCHAR(64) NOT NULL REFERENCES lkp_recording_status(code),
  audio_file_ref     TEXT NOT NULL,                  -- blob storage pointer, Step 10.5
  duration_ms        INTEGER NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recordings_call_session_id ON recordings (call_session_id);
CREATE INDEX ix_recordings_tenant_id ON recordings (tenant_id);

CREATE TABLE voicemails (
  voicemail_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_session_id          UUID NOT NULL REFERENCES call_sessions(call_session_id),
  recipient_employee_id    UUID NULL REFERENCES employees(employee_id),
  recipient_department_id  UUID NULL REFERENCES departments(department_id),
  status_code              VARCHAR(64) NOT NULL REFERENCES lkp_voicemail_status(code),
  audio_file_ref            TEXT NOT NULL,
  duration_ms               INTEGER NULL,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_voicemails_exactly_one_recipient CHECK (
    (recipient_employee_id IS NOT NULL)::int + (recipient_department_id IS NOT NULL)::int = 1
  )     -- Step 1.4 / Step 7.6: exactly one recipient, enforced physically
);
CREATE INDEX ix_voicemails_call_session_id ON voicemails (call_session_id);
CREATE INDEX ix_voicemails_recipient_employee_id ON voicemails (recipient_employee_id);
CREATE INDEX ix_voicemails_recipient_department_id ON voicemails (recipient_department_id);

CREATE TABLE call_detail_records (
  call_detail_record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_session_id        UUID NOT NULL REFERENCES call_sessions(call_session_id),
  tenant_id               UUID NOT NULL REFERENCES tenants(tenant_id),
  call_queue_id           UUID NULL REFERENCES call_queues(call_queue_id),
  employee_id             UUID NULL REFERENCES employees(employee_id),
  disposition_code        VARCHAR(64) NOT NULL REFERENCES lkp_call_disposition(code),
  duration_seconds         INTEGER NOT NULL DEFAULT 0,
  started_at               TIMESTAMPTZ NOT NULL,
  ended_at                 TIMESTAMPTZ NOT NULL,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Step 6.2: exactly one CDR per CallSession, enforced as a constraint
CREATE UNIQUE INDEX uq_call_detail_records_call_session ON call_detail_records (call_session_id);
CREATE INDEX ix_call_detail_records_tenant_id ON call_detail_records (tenant_id, started_at);
CREATE INDEX ix_call_detail_records_call_queue_id ON call_detail_records (call_queue_id);
CREATE INDEX ix_call_detail_records_employee_id ON call_detail_records (employee_id);
```

### 3.10 Reporting & Analytics
`reports, analytics_events`

```sql
CREATE TABLE reports (
  report_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(tenant_id),
  type_code     VARCHAR(64) NOT NULL REFERENCES lkp_report_type(code),
  format_code   VARCHAR(64) NOT NULL REFERENCES lkp_report_format(code),
  name          VARCHAR(150) NOT NULL,
  parameters    JSONB NOT NULL DEFAULT '{}',   -- filter/date-range parameters used to build the report
  generated_at  TIMESTAMPTZ NULL,
  file_ref      TEXT NULL,                     -- blob pointer for generated CSV/PDF output
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID NULL REFERENCES users(user_id)
  -- Step 4: Reports are built by QUERYING call_detail_records/analytics_events, not owning them —
  -- deliberately no FK from reports to either table.
);
CREATE INDEX ix_reports_tenant_id ON reports (tenant_id);

-- analytics_events: Step 1.2/6.3/10.5 — BIGINT identity key, high-frequency append-only, time-partitioned.
-- Physical shape (partitioning) detailed in Section 4.
CREATE TABLE analytics_events (
  event_id          BIGINT GENERATED ALWAYS AS IDENTITY,
  event_uuid        UUID NOT NULL DEFAULT gen_random_uuid(),   -- external correlation id, Step 6.3
  call_session_id   UUID NOT NULL REFERENCES call_sessions(call_session_id),
  node_id           UUID NULL REFERENCES nodes(node_id),
  tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id),
  event_type        VARCHAR(100) NOT NULL,
  payload           JSONB NULL,
  occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, occurred_at)   -- partition key must be part of the PK on a partitioned table
) PARTITION BY RANGE (occurred_at);
```

### 3.11 AI Assistant
`ai_request_jobs`

```sql
CREATE TABLE ai_request_jobs (
  ai_request_job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id),
  user_id           UUID NOT NULL REFERENCES users(user_id),
  request_type_code VARCHAR(64) NOT NULL REFERENCES lkp_ai_request_type(code),
  status_code       VARCHAR(64) NOT NULL REFERENCES lkp_ai_job_status(code),
  input_payload     JSONB NOT NULL,                  -- Step 9.1: provider-specific request body
  output_payload    JSONB NULL,                       -- Step 9.1: provider-specific response body
  produced_flow_version_id UUID NULL REFERENCES flow_versions(flow_version_id),  -- zero-or-one-to-one, Step 4
  produced_voice_prompt_id UUID NULL REFERENCES voice_prompts(voice_prompt_id),  -- zero-or-one-to-one, Step 4
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
  -- NOTE: outputs are always drafts requiring human approval before becoming live artifacts (module rationale,
  -- Step 2) — enforced by IVRDesignService/MediaService workflow, not by this table.
);
CREATE INDEX ix_ai_request_jobs_tenant_id ON ai_request_jobs (tenant_id);
CREATE INDEX ix_ai_request_jobs_user_id ON ai_request_jobs (user_id);
```

### 3.12 Notification
`notification_templates, notifications`

```sql
CREATE TABLE notification_templates (
  notification_template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type    VARCHAR(100) NOT NULL,
  channel_code  VARCHAR(64) NOT NULL REFERENCES lkp_notification_channel(code),
  subject_template TEXT NULL,
  body_template    TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Step 6.2: unique composite (event_type, channel)
CREATE UNIQUE INDEX uq_notification_templates_event_channel ON notification_templates (event_type, channel_code);

CREATE TABLE notifications (
  notification_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID NOT NULL REFERENCES users(user_id),
  notification_template_id   UUID NOT NULL REFERENCES notification_templates(notification_template_id),
  channel_code                VARCHAR(64) NOT NULL REFERENCES lkp_notification_channel(code),
  status_code                 VARCHAR(64) NOT NULL REFERENCES lkp_notification_status(code),
  rendered_content             TEXT NOT NULL,
  sent_at                      TIMESTAMPTZ NULL,
  read_at                      TIMESTAMPTZ NULL,
  created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
  -- Step 4: AlertRule → Notification is a service-level trigger, not a stored FK — no alert_rule_id column here.
);
CREATE INDEX ix_notifications_user_id ON notifications (user_id, status_code);
CREATE INDEX ix_notifications_template_id ON notifications (notification_template_id);
```

### 3.13 Audit & Compliance
`audit_log_entries`

```sql
-- Step 1.2/6.3/7.4: BIGINT identity PK; polymorphic target is intentionally NOT a foreign key.
CREATE TABLE audit_log_entries (
  audit_log_entry_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  audit_log_entry_uuid UUID NOT NULL DEFAULT gen_random_uuid(),  -- external correlation id, Step 6.3
  tenant_id           UUID NULL REFERENCES tenants(tenant_id),   -- nullable: platform-level entries, Step 7.1
  actor_user_id        UUID NOT NULL REFERENCES users(user_id),
  action                TEXT NOT NULL,                            -- free-form narration, Step 9.2
  target_entity_type    VARCHAR(64) NOT NULL,                     -- table-name enum; deliberately unconstrained
                                                                   -- to any single table, per Step 7.4
  target_entity_id      TEXT NOT NULL,                             -- raw UUID or BIGINT of the affected row,
                                                                    -- stored as TEXT since the referenced key
                                                                    -- type varies by target_entity_type
  before_snapshot        JSONB NULL,                               -- Step 9.1: schema-agnostic row snapshot
  after_snapshot          JSONB NULL,
  occurred_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_entries_tenant_id ON audit_log_entries (tenant_id, occurred_at);
CREATE INDEX ix_audit_log_entries_target ON audit_log_entries (target_entity_type, target_entity_id);
CREATE INDEX ix_audit_log_entries_actor ON audit_log_entries (actor_user_id);
-- Step 10.3: a periodic integrity-check job (scanning target_entity_type/target_entity_id against the live
-- tables) is recommended as a safety net, since the database cannot itself enforce this reference.
```

### 3.14 Deployment
`deployments, deployment_environments`

```sql
CREATE TABLE deployment_environments (
  deployment_environment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(tenant_id),
  type_code     VARCHAR(64) NOT NULL REFERENCES lkp_environment_type(code),
  name          VARCHAR(100) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_deployment_environments_tenant_id ON deployment_environments (tenant_id);

CREATE TABLE deployments (
  deployment_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                  UUID NOT NULL REFERENCES tenants(tenant_id),
  did_id                       UUID NOT NULL REFERENCES dids(did_id),
  flow_version_id               UUID NOT NULL REFERENCES flow_versions(flow_version_id),
  deployment_environment_id     UUID NOT NULL REFERENCES deployment_environments(deployment_environment_id),
  status_code                    VARCHAR(64) NOT NULL REFERENCES lkp_deployment_status(code),
  deployed_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by                         UUID NULL REFERENCES users(user_id)
);
-- Step 6.2 / 7.2: exactly one Active Deployment per DID — the Design/Runtime seam invariant
CREATE UNIQUE INDEX uq_deployments_did_active ON deployments (did_id) WHERE status_code = 'Active';
CREATE INDEX ix_deployments_tenant_id ON deployments (tenant_id);
CREATE INDEX ix_deployments_flow_version_id ON deployments (flow_version_id);
CREATE INDEX ix_deployments_environment_id ON deployments (deployment_environment_id);
```

### 3.15 Monitoring
`health_checks, system_metrics, alert_rules`

```sql
CREATE TABLE health_checks (
  health_check_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  component_code       VARCHAR(64) NOT NULL REFERENCES lkp_health_component(code),
  status_code            VARCHAR(64) NOT NULL REFERENCES lkp_health_status(code),
  detail                  JSONB NULL,
  checked_at                TIMESTAMPTZ NOT NULL DEFAULT now()
  -- platform-level, not tenant-scoped, per Step 3 "Parent Entity: — (platform-level)"
);
CREATE INDEX ix_health_checks_component_checked_at ON health_checks (component_code, checked_at);

CREATE TABLE system_metrics (
  system_metric_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NULL REFERENCES tenants(tenant_id),   -- nullable, Step 7.1
  metric_name              VARCHAR(100) NOT NULL,
  metric_value                NUMERIC(18,4) NOT NULL,
  recorded_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_system_metrics_tenant_id ON system_metrics (tenant_id, recorded_at);
CREATE INDEX ix_system_metrics_name ON system_metrics (metric_name, recorded_at);

CREATE TABLE alert_rules (
  alert_rule_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id            UUID NULL REFERENCES tenants(tenant_id),     -- nullable, Step 7.1
  metric_name            VARCHAR(100) NOT NULL,   -- Step 5: matching key against system_metrics.metric_name,
                                                   -- evaluated at query time by MonitoringService — not a join table
  severity_code             VARCHAR(64) NOT NULL REFERENCES lkp_alert_severity(code),
  threshold                    NUMERIC(18,4) NOT NULL,
  comparison_operator             VARCHAR(2) NOT NULL CHECK (comparison_operator IN ('>','<','>=','<=','=')),
  is_active                          BOOLEAN NOT NULL DEFAULT true,
  created_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_alert_rules_tenant_id ON alert_rules (tenant_id);
CREATE INDEX ix_alert_rules_metric_name ON alert_rules (metric_name) WHERE is_active;
```

### 3.16 Billing & Metering
`usage_records, invoices`

```sql
CREATE TABLE usage_records (
  usage_record_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL REFERENCES tenants(tenant_id),
  metric_type_code        VARCHAR(64) NOT NULL REFERENCES lkp_usage_metric_type(code),
  quantity                   NUMERIC(18,4) NOT NULL,
  period_start                  DATE NOT NULL,
  period_end                       DATE NOT NULL,
  created_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_usage_records_tenant_period ON usage_records (tenant_id, period_start, period_end);

CREATE TABLE invoices (
  invoice_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id),
  payment_status_code VARCHAR(64) NOT NULL REFERENCES lkp_invoice_payment_status(code),
  total_amount           NUMERIC(14,2) NOT NULL,     -- Step 9.7: Money VO, amount half
  currency_code              VARCHAR(3) NOT NULL REFERENCES lkp_currency(code),  -- Step 9.7: Money VO, currency half
  line_items                    JSONB NOT NULL DEFAULT '[]',   -- Step 9.1: variable-length list of charge lines
  period_start                     DATE NOT NULL,
  period_end                          DATE NOT NULL,
  issued_at                              TIMESTAMPTZ NULL,
  created_at                                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                                   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invoices_tenant_id ON invoices (tenant_id, period_start);
```

---

## 4. Partitioning Strategy

Per Logical Design Step 1.2 and Step 10.5, `analytics_events` is the only table requiring physical partitioning at
launch; `audit_log_entries` is noted as a candidate for the same treatment if its volume later warrants it, but is
not partitioned by default since it is comparatively low-volume relative to `analytics_events`.

```sql
-- Monthly range partitions on occurred_at, tenant_id left as a leading index column within each partition
-- rather than a sub-partition dimension (sub-partitioning by tenant_id would multiply partition count by
-- tenant count, which does not scale for a SaaS platform with an open-ended tenant list).
CREATE TABLE analytics_events_2026_07 PARTITION OF analytics_events
  FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE analytics_events_2026_08 PARTITION OF analytics_events
  FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
-- ... one partition per month, created ahead of need by a scheduled job (pg_partman or equivalent).

CREATE INDEX ix_analytics_events_call_session_id ON analytics_events (call_session_id);
CREATE INDEX ix_analytics_events_tenant_id ON analytics_events (tenant_id, occurred_at);
CREATE INDEX ix_analytics_events_node_id ON analytics_events (node_id);
```

Retention/archival: partitions older than each tenant's `default_retention_policy` window are detached and moved
to cold storage (or dropped) by a scheduled job — `DROP TABLE` on a detached partition is near-instant, avoiding
the row-by-row `DELETE` cost a non-partitioned table would incur at this volume.

---

## 5. Tenant Isolation — Row-Level Security

Logical Design Step 7.1 states tenant_id isolation is "the single most important foreign key pattern" in the
schema. The foreign key alone does not prevent an application bug from leaking a cross-tenant row into a query
result; Row-Level Security (RLS) adds a database-enforced backstop:

```sql
-- Applied uniformly to every table carrying a NOT NULL tenant_id column (shown for one representative table;
-- the same three statements are executed per table in the migration script).
ALTER TABLE departments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON departments
  USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- The application sets this once per connection/transaction after authenticating the request:
-- SELECT set_config('app.current_tenant_id', '<tenant-uuid>', true);
```

Tables with a nullable `tenant_id` (users, roles, sip_trunks, system_metrics, alert_rules, audit_log_entries) use a
policy that also permits `tenant_id IS NULL` rows to be visible only to a distinct `app.is_super_admin` session
flag, so platform-level rows remain invisible to ordinary tenant sessions:

```sql
CREATE POLICY tenant_isolation_nullable ON roles
  USING (
    tenant_id = current_setting('app.current_tenant_id')::uuid
    OR (tenant_id IS NULL AND current_setting('app.is_super_admin', true) = 'true')
  );
```

---

## 6. Constraint Summary (physical realization of Logical Design Steps 6–8)

| Constraint | Table | Mechanism |
|---|---|---|
| Extension numbers unique within a Tenant | `sip_extensions` | `UNIQUE (tenant_id, extension_number)` |
| DID globally unique | `dids` | `UNIQUE (number)` |
| Role name unique within Tenant / globally for platform roles | `roles` | two partial unique indexes |
| Exactly one CDR per CallSession | `call_detail_records` | `UNIQUE (call_session_id)` |
| One template per (event_type, channel) | `notification_templates` | `UNIQUE (event_type, channel_code)` |
| Exactly one Active Subscription per Tenant | `subscriptions` | partial unique index `WHERE status = 'Active'` |
| Exactly one Active Deployment per DID | `deployments` | partial unique index `WHERE status_code = 'Active'` |
| Voicemail has exactly one recipient | `voicemails` | `CHECK` constraint on the two nullable FKs |
| Flow/version numbering | `flow_versions` | `UNIQUE (ivr_flow_id, version_number)` |
| Source/target nodes share the Connection's flow_version_id | `connections` | **not** DB-enforced — FlowValidationService, per Step 7.3 |
| Audit polymorphic target refers to a real row | `audit_log_entries` | **not** DB-enforced — AuditService + periodic integrity job, per Step 7.4 |
| Node configuration → VoicePrompt reference | `nodes.configuration` (JSONB) | **not** DB-enforced — FlowValidationService, per Step 10.4 |

The three "not DB-enforced" rows are carried over verbatim from Logical Design Steps 7.3, 7.4, and 10.4 — they are
accepted trade-offs of the DDD's own design intent (Step 1.2, 1.3), not gaps introduced during physical design.

---

## 7. Indexing Strategy Summary (Logical Design Step 10.7)

- Every tenant-scoped table's primary access-pattern index leads with `tenant_id`, matching the near-universal
  `WHERE tenant_id = ?` filter (see each module's `ix_<table>_tenant_id` above).
- `dids.number` carries a dedicated unique index — the busiest single lookup in the schema, sitting on the
  critical path of every inbound call (`number → tenant → active deployment → flow_version`).
- The partial unique index `uq_deployments_did_active` doubles as the fast-path index for resolving the active
  deployment for a dialed DID, avoiding a second index for that same query.
- `role_permissions` and `user_roles` are read on effectively every authorized request; both are indexed on their
  non-leading column (`permission_code`, `role_id` respectively) in addition to their composite primary key, and
  are strong caching candidates at the application layer given how rarely they change relative to read volume.
- `nodes.configuration` and other JSONB columns that are ever filtered on (not just read whole) carry a `GIN`
  index (see `ix_nodes_configuration_gin`); JSONB columns that are always read/written as an opaque whole
  (`tenants.branding_config`, `ai_request_jobs.input_payload`, `audit_log_entries.before_snapshot`/
  `after_snapshot`) do not, since no query filters on their internal keys.

---

## 8. Deferred Objects (carried forward, not created)

Per Logical Design Step 10.1–10.2, these tables/lookups are named in the domain model but explicitly deferred from
this release; they are **not** created by this migration and are listed here only so the physical design phase
does not lose track of them for a later migration: `webhook_subscriptions`, `flow_templates`,
`agent_presence_logs`, `callback_requests`, `data_subject_requests`, `lkp_did_release_reason`,
`lkp_tenant_offboard_reason`, `lkp_channel_type`.

---

## 9. Physical Design Review

**Storage & growth.** `call_sessions`, `analytics_events`, and `call_detail_records` are the fastest-growing
tables by row count; `recordings` and `voicemails` are the fastest-growing by byte volume, but store only
`audio_file_ref` pointers (Logical Design Step 10.5), keeping the relational tier itself storage-light regardless
of audio retention policy.

**Write concurrency.** `call_sessions` and `analytics_events` are isolated from the low-frequency administrative
tables both logically (separate modules) and physically (partitioning + read-replica routing for `call_sessions`
queries), so a burst in call volume cannot degrade IAM or IVR Design query latency.

**Cacheability.** `flow_versions`, `nodes`, and `connections` are immutable once a version is `Published`
(Logical Design Step 10.5) — the execution engine can treat them as aggressively cacheable and never needs to
read from a primary write node for flow structure during live call handling.

**Aggregate-boundary discipline.** Consistent with Logical Design Step 10.6, this physical design adds no
trigger or constraint that would let a child row (`nodes`, `connections`, `queue_memberships`, `sessions`,
`subscriptions`) be written independent of its owning aggregate root's service — that remains an
application-layer (DAO-per-aggregate-root) responsibility, not a database one.

**Open item for product sign-off.** Employee–Department many-to-many (`employee_departments`) is implemented
without a uniqueness constraint that would downgrade it to one-to-many, per Logical Design Step 10.3. This should
be reconfirmed with the product owner before this migration is run against production, since adding that
constraint later is a breaking schema change if multi-department assignment is already in use.
