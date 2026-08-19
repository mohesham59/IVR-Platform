# 02. Database Schema & Migrations Specification

## Database Overview & Technology Stack
- **DBMS**: PostgreSQL 15+
- **Extensions**: `pgcrypto` (`gen_random_uuid()`), `vector` (`pgvector` 0.5+ for 1536-dimensional HNSW cosine similarity vector embeddings)
- **Primary Schema Locations**: `Database/AI-database/` (000 through 015) and `Database/Payment-database/001_payment_and_subscriptions.sql`
- **ORM / Data Access**: Raw JDBC via connection pools (`DatabaseManager`) with explicit `PreparedStatement` binding across all backend services.

---

## Complete Table Specifications (Alphabetical Order)

### 1. `agent_states`
- **Migration**: `013_queue_management.sql`
- **Purpose**: Tracks real-time presence and activity status of tenant agents assigned to queues.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique agent state record ID |
| `agent_id` | `UUID` | `NOT NULL, UNIQUE, REFERENCES users(id) ON DELETE CASCADE` | — | User ID of the agent |
| `current_state` | `VARCHAR(20)` | `NOT NULL, CHECK (current_state IN ('available', 'in_call', 'paused', 'offline'))` | `'available'` | Current agent status |
| `state_changed_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Timestamp of last status transition |
| `current_queue_id` | `UUID` | `REFERENCES queues(id) ON DELETE SET NULL` | `NULL` | ID of queue currently handling call for |

- **Indexes**: `idx_agent_states_agent` on `(agent_id)`

---

### 2. `ai_messages`
- **Migration**: `000_full_mvp_schema_combined.sql`, `003_ai_messages.sql`
- **Purpose**: Individual conversational turns (prompts and completions) within an AI session.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique message ID |
| `session_id` | `UUID` | `NOT NULL, REFERENCES ai_sessions(id) ON DELETE CASCADE` | — | Parent AI session ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant owner ID for scoping |
| `turn_number` | `INT` | `NOT NULL` | — | Sequential turn index within session |
| `role` | `VARCHAR(20)` | `NOT NULL, CHECK (role IN ('USER','ASSISTANT','SYSTEM'))` | — | Message author role |
| `content` | `TEXT` | `NOT NULL` | — | Raw message text content |
| `model_used` | `VARCHAR(100)` | — | `NULL` | Specific LLM model identifier used |
| `tokens_input` | `INT` | — | `NULL` | Input token count |
| `tokens_output` | `INT` | — | `NULL` | Output token count |
| `metadata` | `JSONB` | `NOT NULL` | `'{}'::jsonb` | Additional diagnostic/provider metadata |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Message timestamp |

- **Constraints**: `uq_ai_messages_session_turn` `UNIQUE (session_id, turn_number)`
- **Indexes**:
  - `idx_ai_messages_session_turn` on `(session_id, turn_number)`
  - `idx_ai_messages_tenant_created_at` on `(tenant_id, created_at DESC)`
  - `idx_ai_messages_content_fts` GIN index on `to_tsvector('english', content)`

---

### 3. `ai_sessions`
- **Migration**: `000_full_mvp_schema_combined.sql`, `002_ai_sessions.sql`
- **Purpose**: Root container for AI assistant chat and interactive session dialogues.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique session ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant owner ID |
| `channel` | `VARCHAR(20)` | `NOT NULL, CHECK (channel IN ('VOICE','CHAT','WHATSAPP','WEB_WIDGET','SMS'))` | — | Communication channel |
| `external_reference_id` | `VARCHAR(255)` | — | `NULL` | External call/chat identifier |
| `customer_identifier` | `VARCHAR(255)` | — | `NULL` | Phone number or user ID of customer |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('ACTIVE','ENDED','ABANDONED','ERROR'))` | `'ACTIVE'` | Session lifecycle state |
| `started_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Session start time |
| `ended_at` | `TIMESTAMPTZ` | — | `NULL` | Session completion time |
| `metadata` | `JSONB` | `NOT NULL` | `'{}'::jsonb` | Arbitrary session context |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record update timestamp |

- **Constraints**: `chk_ai_sessions_ended_after_started CHECK (ended_at IS NULL OR ended_at >= started_at)`
- **Indexes**:
  - `idx_ai_sessions_tenant_status` on `(tenant_id, status)`
  - `idx_ai_sessions_tenant_started_at` on `(tenant_id, started_at DESC)`
  - `idx_ai_sessions_tenant_customer` on `(tenant_id, customer_identifier)`
  - `uq_ai_sessions_tenant_external_ref` UNIQUE partial index on `(tenant_id, external_reference_id) WHERE external_reference_id IS NOT NULL`
- **Triggers**: `trg_ai_sessions_updated_at` BEFORE UPDATE calls `set_updated_at()`

---

### 4. `audit_logs`
- **Migration**: `015_audit_logs.sql`
- **Purpose**: System-wide security and compliance audit trail for administrative and operational actions.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique audit log ID |
| `tenant_id` | `UUID` | `REFERENCES tenants(id) ON DELETE CASCADE` | `NULL` | Tenant ID (NULL for SuperAdmin global actions) |
| `actor_user_id` | `UUID` | `REFERENCES users(id) ON DELETE SET NULL` | `NULL` | User who performed action |
| `actor_email` | `VARCHAR(255)` | — | `NULL` | Email of actor at time of action |
| `action_type` | `VARCHAR(100)` | `NOT NULL` | — | Action code (e.g. `COMPANY_CREATED`, `IVR_PUBLISHED`) |
| `target_entity_type` | `VARCHAR(100)` | — | `NULL` | Target domain type (`TENANT`, `USER`, `FLOW`) |
| `target_entity_id` | `VARCHAR(255)` | — | `NULL` | Target entity identifier |
| `details` | `JSONB` | `NOT NULL` | `'{}'::jsonb` | Structured JSON change payload |
| `ip_address` | `VARCHAR(45)` | — | `NULL` | IP address of request client |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Log entry timestamp |

- **Indexes**:
  - `idx_audit_logs_tenant` on `(tenant_id)`
  - `idx_audit_logs_action_type` on `(action_type)`
  - `idx_audit_logs_created_at` on `(created_at DESC)`

---

### 5. `call_events`
- **Migration**: `010_telephony_analytics.sql`
- **Purpose**: Granular menu selections and node interaction events during telephony calls.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique event ID |
| `session_id` | `VARCHAR(255)` | `NOT NULL, REFERENCES call_logs(session_id) ON DELETE CASCADE` | — | Links to telephony call session |
| `event_type` | `VARCHAR(50)` | `NOT NULL` | — | Event classification (`MENU_SELECTION`, `FORM_SUBMIT`) |
| `node_name` | `VARCHAR(100)` | `NOT NULL` | — | VXML menu/form node identifier |
| `event_time` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Timestamp when node was triggered |

---

### 6. `call_logs`
- **Migration**: `010_telephony_analytics.sql`
- **Purpose**: Call Detail Record (CDR) logs captured during FastAGI VXML scenario execution.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique log ID |
| `session_id` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | — | Unique FastAGI call session string |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Tenant owner ID |
| `caller_id` | `VARCHAR(50)` | `NOT NULL` | — | Caller phone number or extension |
| `scenario_name` | `VARCHAR(100)` | `NOT NULL` | — | Executed VXML scenario name |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('ANSWERED', 'MISSED', 'BUSY', 'FAILED', 'IN_PROGRESS'))` | — | Telephony call outcome |
| `start_time` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Call start timestamp |
| `end_time` | `TIMESTAMPTZ` | — | `NULL` | Call completion timestamp |
| `duration` | `INT` | — | `0` | Total duration in seconds |
| `last_node` | `VARCHAR(100)` | — | `NULL` | Last VXML node visited prior to hangup |

- **Indexes**:
  - `idx_call_logs_tenant_start` on `(tenant_id, start_time DESC)`
  - `idx_call_logs_status` on `(status)`

---

### 7. `conversation_history`
- **Migration**: `000_full_mvp_schema_combined.sql`, `004_conversation_history.sql`
- **Purpose**: Summarized customer context across multiple historical call/chat sessions.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique summary record ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant ID |
| `customer_identifier` | `VARCHAR(255)` | `NOT NULL` | — | Customer phone number or email |
| `session_id` | `UUID` | `REFERENCES ai_sessions(id) ON DELETE SET NULL` | `NULL` | Associated session ID |
| `summary_text` | `TEXT` | `NOT NULL` | — | Generated summary text |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Summary creation timestamp |

- **Indexes**: `idx_conversation_history_tenant_customer` on `(tenant_id, customer_identifier, created_at DESC)`

---

### 8. `embeddings`
- **Migration**: `000_full_mvp_schema_combined.sql`, `007_embeddings.sql`
- **Purpose**: High-dimensional vector embeddings for RAG semantic search over knowledge base chunks.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique embedding ID |
| `chunk_id` | `UUID` | `NOT NULL, UNIQUE, REFERENCES knowledge_chunks(id) ON DELETE CASCADE` | — | Parent chunk ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant owner ID |
| `embedding_model` | `VARCHAR(100)` | `NOT NULL` | — | Name of embedding model used |
| `embedding` | `VECTOR(1536)` | `NOT NULL` | — | 1536-dimensional floating point vector |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Generation timestamp |

- **Indexes**:
  - `idx_embeddings_tenant` on `(tenant_id)`
  - `idx_embeddings_vector_hnsw` HNSW index on `embedding vector_cosine_ops`

---

### 9. `knowledge_chunks`
- **Migration**: `000_full_mvp_schema_combined.sql`, `006_knowledge_chunks.sql`
- **Purpose**: Document text segments extracted during knowledge base ingestion.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique chunk ID |
| `document_id` | `UUID` | `NOT NULL, REFERENCES knowledge_documents(id) ON DELETE CASCADE` | — | Parent document ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant owner ID |
| `chunk_index` | `INT` | `NOT NULL` | — | Zero-indexed chunk order |
| `content` | `TEXT` | `NOT NULL` | — | Text content of chunk |
| `token_count` | `INT` | — | `NULL` | Estimated token count |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Chunk creation timestamp |

- **Constraints**: `uq_knowledge_chunks_document_index UNIQUE (document_id, chunk_index)`
- **Indexes**:
  - `idx_knowledge_chunks_tenant` on `(tenant_id)`
  - `idx_knowledge_chunks_content_fts` GIN index on `to_tsvector('english', content)`

---

### 10. `knowledge_documents`
- **Migration**: `000_full_mvp_schema_combined.sql`, `005_knowledge_documents.sql`
- **Purpose**: Uploaded or imported documents providing domain context for RAG vector search.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique document ID |
| `tenant_id` | `UUID` | `NOT NULL` | — | Tenant owner ID |
| `title` | `VARCHAR(500)` | `NOT NULL` | — | Human readable document title |
| `source_type` | `VARCHAR(20)` | `NOT NULL, CHECK (source_type IN ('UPLOAD','URL','API','MANUAL'))` | — | Source of document |
| `source_uri` | `TEXT` | — | `NULL` | URI or file path of source |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('PENDING','INGESTING','INGESTED','FAILED'))` | `'PENDING'` | Ingestion status |
| `version` | `INT` | `NOT NULL` | `1` | Document version number |
| `checksum` | `VARCHAR(64)` | — | `NULL` | SHA-256 content checksum |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Upload timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Update timestamp |

- **Indexes**:
  - `idx_knowledge_documents_tenant_status` on `(tenant_id, status)`
  - `uq_knowledge_documents_tenant_checksum` UNIQUE partial index on `(tenant_id, checksum) WHERE checksum IS NOT NULL`
- **Triggers**: `trg_knowledge_documents_updated_at` BEFORE UPDATE calls `set_updated_at()`

---

### 11. `notifications`
- **Migration**: `009_users_and_tenants.sql`
- **Purpose**: System and billing notifications delivered to tenant users and Super Admins.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique notification ID |
| `tenant_id` | `UUID` | `REFERENCES tenants(id) ON DELETE CASCADE` | `NULL` | Recipient tenant ID (NULL for SuperAdmin) |
| `user_id` | `UUID` | `REFERENCES users(id) ON DELETE CASCADE` | `NULL` | Recipient user ID (NULL for tenant broadcast) |
| `message` | `TEXT` | `NOT NULL` | — | Notification body text |
| `link_url` | `VARCHAR(255)` | — | `NULL` | Optional UI navigation route |
| `is_read` | `BOOLEAN` | `NOT NULL` | `false` | Read status |
| `type` | `VARCHAR(50)` | — | `NULL` | Notification classification (`PAYMENT_SUCCESS`, `PLAN_OVERRIDE`) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Creation timestamp |

- **Indexes**:
  - `idx_notifications_tenant` on `(tenant_id)`
  - `idx_notifications_user` on `(user_id)`

---

### 12. `phone_numbers`
- **Migration**: `011_phone_numbers.sql`
- **Purpose**: Direct Inward Dialing (DID) telephone numbers provisioned to tenants.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique phone number record ID |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Owner tenant ID |
| `phone_number` | `VARCHAR(50)` | `NOT NULL` | — | E.164 formatted telephone number |
| `country` | `VARCHAR(10)` | `NOT NULL` | `'US'` | Country ISO code |
| `provider` | `VARCHAR(50)` | `NOT NULL` | `'Twilio'` | Telephony carrier provider |
| `assigned_flow_id` | `VARCHAR(255)` | — | `NULL` | ID of IVR flow bound to this number |
| `assigned_flow_name` | `VARCHAR(255)` | — | `NULL` | Name of IVR flow bound to this number |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('ACTIVE', 'UNASSIGNED', 'DISABLED'))` | `'UNASSIGNED'` | Number state |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record update timestamp |

- **Constraints**: `uq_phone_numbers_tenant_number UNIQUE (tenant_id, phone_number)`
- **Indexes**:
  - `idx_phone_numbers_tenant` on `(tenant_id)`
  - `idx_phone_numbers_status` on `(status)`

---

### 13. `prompt_templates`
- **Migration**: `000_full_mvp_schema_combined.sql`, `008_prompt_templates.sql`
- **Purpose**: System and module LLM prompt templates (global and tenant-customized).

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique prompt template ID |
| `tenant_id` | `UUID` | — | `NULL` | Tenant ID (NULL for global default) |
| `module` | `VARCHAR(50)` | `NOT NULL, CHECK (module IN ('ASSISTANT','RAG','SUMMARY'))` | — | System module using template |
| `template_key` | `VARCHAR(150)` | `NOT NULL` | — | Key identifier (e.g. `system_prompt`) |
| `version` | `INT` | `NOT NULL` | `1` | Template version number |
| `content` | `TEXT` | `NOT NULL` | — | Prompt text containing `{{variables}}` |
| `variables` | `JSONB` | `NOT NULL` | `'{}'::jsonb` | Schema of variables expected |
| `is_active` | `BOOLEAN` | `NOT NULL` | `true` | Active status flag |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Template creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Template update timestamp |

- **Indexes**:
  - `uq_prompt_templates_tenant_scoped` UNIQUE partial on `(tenant_id, module, template_key, version) WHERE tenant_id IS NOT NULL`
  - `uq_prompt_templates_global` UNIQUE partial on `(module, template_key, version) WHERE tenant_id IS NULL`
  - `idx_prompt_templates_active` partial on `(module, template_key) WHERE is_active = true`

---

### 14. `queue_members`
- **Migration**: `013_queue_management.sql`
- **Purpose**: Join table assigning agents (`users`) to specific call `queues` with penalty weighting.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique membership ID |
| `queue_id` | `UUID` | `NOT NULL, REFERENCES queues(id) ON DELETE CASCADE` | — | Target call queue ID |
| `agent_id` | `UUID` | `NOT NULL, REFERENCES users(id) ON DELETE CASCADE` | — | Assigned agent user ID |
| `penalty` | `INT` | `NOT NULL` | `0` | Call routing priority penalty |
| `added_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Assignment timestamp |

- **Constraints**: `uq_queue_agent UNIQUE (queue_id, agent_id)`
- **Indexes**:
  - `idx_queue_members_queue` on `(queue_id)`
  - `idx_queue_members_agent` on `(agent_id)`

---

### 15. `queues`
- **Migration**: `013_queue_management.sql`
- **Purpose**: Call center queue definitions specifying ACD routing strategies, hold music, and timeouts.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique queue ID |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Owner tenant ID |
| `name` | `VARCHAR(100)` | `NOT NULL` | — | Queue display name |
| `strategy` | `VARCHAR(30)` | `NOT NULL, CHECK (strategy IN ('round_robin', 'least_recent', 'ring_all', 'linear'))` | `'round_robin'` | ACD call distribution strategy |
| `wrap_up_time_seconds` | `INT` | `NOT NULL` | `15` | Post-call agent rest period |
| `max_wait_seconds` | `INT` | `NOT NULL` | `300` | Maximum queue wait time before overflow |
| `music_on_hold` | `VARCHAR(50)` | `NOT NULL` | `'default'` | Asterisk MOH class |
| `overflow_action` | `VARCHAR(100)` | `NOT NULL` | `'voicemail'` | Fallback routing destination |
| `business_hours` | `JSONB` | — | `'{"mon_fri": {"open": "08:00", "close": "18:00"}}'::jsonb` | Schedule rules |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('active', 'inactive'))` | `'active'` | Queue operating status |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record update timestamp |

- **Constraints**: `uq_tenant_queue_name UNIQUE (tenant_id, name)`
- **Indexes**: `idx_queues_tenant` on `(tenant_id)`

---

### 16. `sip_extensions`
- **Migration**: `012_sip_extensions.sql`
- **Purpose**: PJSIP extension endpoints provisioned in Asterisk for tenant agents and SIP hardphones.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique extension record ID |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Owner tenant ID |
| `extension_number` | `VARCHAR(20)` | `NOT NULL` | — | Numeric extension (e.g. `1001`) |
| `display_name` | `VARCHAR(100)` | `NOT NULL` | — | User or desk phone display name |
| `assigned_user_id` | `UUID` | `REFERENCES users(id) ON DELETE SET NULL` | `NULL` | Bound platform user ID |
| `sip_password` | `VARCHAR(255)` | `NOT NULL` | — | PJSIP authentication password |
| `tls_enabled` | `BOOLEAN` | `NOT NULL` | `false` | SIP TLS encryption flag |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record update timestamp |

- **Constraints**: `uq_tenant_extension UNIQUE (tenant_id, extension_number)`
- **Indexes**: `idx_sip_extensions_tenant` on `(tenant_id)`

---

### 17. `subscription_plans`
- **Migration**: `001_payment_and_subscriptions.sql` (Payment-database)
- **Purpose**: Defines commercial SaaS subscription tiers, pricing in piasters (EGP), and Paymob integration IDs.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique plan ID |
| `name` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | — | Plan name (`Starter`, `Business`, `Enterprise`) |
| `price_piasters` | `BIGINT` | `NOT NULL` | — | Price in Egyptian Piasters (e.g. 50000 = 500 EGP) |
| `billing_interval` | `VARCHAR(20)` | `NOT NULL` | — | Interval (`MONTHLY`, `YEARLY`) |
| `integration_ids` | `TEXT` | — | `NULL` | Comma-separated Paymob Integration IDs |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Update timestamp |

---

### 18. `tenants`
- **Migration**: `009_users_and_tenants.sql`, altered by `001_payment_and_subscriptions.sql`
- **Purpose**: Represents an isolated SaaS customer enterprise account.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique tenant UUID |
| `display_name` | `VARCHAR(255)` | — | `NULL` | Tenant company name |
| `owner_user_id` | `UUID` | `REFERENCES users(id) ON DELETE CASCADE` | `NULL` | Primary owner user ID |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))` | `'INACTIVE'` | Tenant status |
| `subscription_plan_id` | `UUID` | `REFERENCES subscription_plans(id) ON DELETE SET NULL` | `NULL` | Active subscription plan |
| `subscription_status` | `VARCHAR(20)` | — | `'INACTIVE'` | Subscription state (`ACTIVE`, `INACTIVE`) |
| `subscription_expires_at`| `TIMESTAMPTZ` | — | `NULL` | Expiration date of current billing cycle |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Record update timestamp |

---

### 19. `transactions`
- **Migration**: `001_payment_and_subscriptions.sql` (Payment-database)
- **Purpose**: Payment checkout attempts and Paymob transaction ledger.

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique platform transaction ID |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Paying tenant ID |
| `type` | `VARCHAR(20)` | `NOT NULL, CHECK (type IN ('SUBSCRIPTION', 'ONE_TIME'))` | — | Transaction intent |
| `amount_piasters` | `BIGINT` | `NOT NULL` | — | Billed amount in piasters |
| `currency` | `VARCHAR(10)` | `NOT NULL` | `'EGP'` | Billed currency code |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED'))` | — | Transaction state |
| `paymob_transaction_id`| `VARCHAR(100)` | — | `NULL` | Paymob gateway transaction ID |
| `paymob_order_id` | `VARCHAR(100)` | — | `NULL` | Paymob gateway order ID |
| `plan_id` | `UUID` | `REFERENCES subscription_plans(id) ON DELETE SET NULL` | `NULL` | Target plan ID |
| `card_token` | `VARCHAR(255)` | — | `NULL` | Stored card token for auto-renewal |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Initiation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Update timestamp |

- **Indexes**:
  - `idx_transactions_tenant_id` on `(tenant_id)`
  - `idx_transactions_status` on `(status)`
  - `idx_transactions_paymob_txn` on `(paymob_transaction_id)`

---

### 20. `users`
- **Migration**: `009_users_and_tenants.sql`
- **Purpose**: Platform user accounts (Tenant Admins, Agents, and Super Admins).

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique user UUID |
| `active_tenant_id` | `UUID` | `REFERENCES tenants(id) ON DELETE SET NULL` | `NULL` | Tenant scope (NULL for SuperAdmin) |
| `email` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | — | User email address (login username) |
| `password` | `TEXT` | `NOT NULL` | — | Password (SHA-256 hashed or raw legacy) |
| `is_superadmin` | `BOOLEAN` | `NOT NULL` | `false` | Super Admin privilege flag |
| `username` | `VARCHAR(100)` | `NOT NULL` | — | Display username |
| `status` | `VARCHAR(20)` | `NOT NULL, CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))` | `'ACTIVE'` | User access status |
| `last_login_at` | `TIMESTAMPTZ` | — | `NULL` | Timestamp of last successful login |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | `now()` | Update timestamp |

- **Indexes**:
  - `idx_users_email` on `(email)`
  - `idx_users_active_tenant` on `(active_tenant_id)`

---

### 21. `voice_prompts`
- **Migration**: `014_voice_prompts.sql` / `Database/voice_prompts.sql`
- **Purpose**: System audio prompt files (uploaded WAVs or AI-generated TTS audio files).

| Column | Type | Constraints | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | `gen_random_uuid()` | Unique voice prompt ID |
| `tenant_id` | `UUID` | `NOT NULL, REFERENCES tenants(id) ON DELETE CASCADE` | — | Owner tenant ID |
| `name` | `VARCHAR(255)` | `NOT NULL` | — | Prompt title |
| `language` | `VARCHAR(50)` | `NOT NULL` | `'en-US'` | Audio prompt language code |
| `duration` | `VARCHAR(20)` | — | `'0:15'` | Formatted duration string |
| `type` | `VARCHAR(50)` | `NOT NULL` | `'Uploaded'` | Prompt type (`Uploaded`, `AI Generated`) |
| `created_by` | `VARCHAR(255)` | `NOT NULL` | `'Admin'` | Creator user name or email |
| `file_path` | `TEXT` | `NOT NULL` | — | Relative path in `/var/lib/asterisk/sounds` |
| `size_bytes` | `BIGINT` | — | `102400` | Audio file size in bytes |
| `created_at` | `TIMESTAMPTZ` | — | `now()` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | — | `now()` | Update timestamp |

- **Indexes**: `idx_voice_prompts_tenant` on `(tenant_id)`
