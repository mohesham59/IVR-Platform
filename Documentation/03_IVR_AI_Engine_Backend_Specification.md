# 03. IVR-AI-Engine Backend Specification

## Service Overview & Architecture
- **Runtime Environment**: Java 21 / Jakarta Servlet 6.0 / Embedded Apache Tomcat 10 (Port `8081`)
- **Main Package**: `com.nexusivr.ai`
- **Core Responsibilities**:
  - User Authentication, JWT Issuance & Scoping, Password Hashing
  - Multi-tenant CRUD operations (Companies, Users, Phone Numbers, SIP Extensions, Queues, Voice Prompts)
  - 7-Pass Generative AI IVR Flow Builder & Prompt Refiner Engine
  - Flow Draft Persistence, Validation, VXML Export, and Asterisk Dialplan Auto-Provisioning
  - Retrieval-Augmented Generation (RAG) Vector Search Knowledge Base Engine
  - AI Provider Orchestration with Fallback Chain (OpenRouter / Gemini / Groq / Ollama) and Circuit Breaker
  - Telephony Call Analytics, System Health Probing, Audit Logging, and In-App Notifications

---

## Detailed Class Reference

### 1. Controller / Servlet Layer (`com.nexusivr.ai.controller`)

#### `BaseAiServlet` (Abstract Base Class)
- **Responsibility**: Foundation servlet providing CORS setup, JSON request parsing, JWT token authentication, tenant scoping, and standard error handling for all child servlets.
- **Public Methods**:
  - `doOptions(HttpServletRequest, HttpServletResponse)`: Standardized preflight CORS header response (`Access-Control-Allow-Origin: *`, `Methods`, `Headers`).
  - `setCorsHeaders(HttpServletResponse)`: Injects CORS headers into outgoing HTTP responses.
  - `sendJsonResponse(HttpServletResponse, int statusCode, Object data)`: Serializes object to JSON via Gson and writes response.
  - `sendError(HttpServletResponse, int statusCode, String message)`: Writes standardized `ErrorResponse` JSON.
  - `extractTenantId(HttpServletRequest)`: Decodes JWT Bearer token from `Authorization` header to extract `tenantId` (UUID). Returns `null` if user is Super Admin or header missing.
  - `extractUserEmail(HttpServletRequest)`: Decodes user email claim from Bearer token.
  - `isSuperAdmin(HttpServletRequest)`: Returns `true` if `isSuperadmin` claim in JWT is `true`.
  - `parseRequestBody(HttpServletRequest, Class<T> clazz)`: Deserializes JSON request body into target DTO class.
- **Dependencies**: `JwtUtil`, `Gson`
- **Callers**: Extended by all 20 REST API servlets in the engine.

#### `AiAgentServlet` (`/ai/agent`)
- **Responsibility**: Primary entry point for AI IVR flow generation, prompt refiner, flow validation, and flow improvement.
- **Public Methods**:
  - `doPost(HttpServletRequest, HttpServletResponse)`: Handles multi-action requests based on JSON payload or `action` param:
    - `action=refine_prompt`: Calls `PromptRefinerService.refinePrompt()`
    - `action=validate`: Calls `FlowModelValidator.validate()`
    - `action=improve`: Calls `UnifiedAiEngine.improveFlow()`
    - Default: Triggers 7-pass `DomainFlowGenerator.generateFlow()`
  - `doDelete(HttpServletRequest, HttpServletResponse)`: Cancels an in-progress AI generation session via `GenerationCancellationRegistry`.
- **Dependencies**: `UnifiedAiEngine`, `PromptRefinerService`, `FlowModelValidator`, `GenerationCancellationRegistry`
- **Caller**: `IVRBuilder.tsx`, `AiAssistantPanel.tsx` in `IVR-webapp`

#### `AiChatServlet` (`/ai/chat`)
- **Responsibility**: Manages AI assistant dialogue sessions and RAG-augmented chat messages.
- **Public Methods**:
  - `doPost(HttpServletRequest, HttpServletResponse)`: Accepts `ChatRequest`, retrieves RAG context from `KnowledgeService`, posts turn to `UnifiedAiEngine.chat()`, stores message in `MessageDao`, returns `ChatResponse`.
  - `doGet(HttpServletRequest, HttpServletResponse)`: Retrieves chat session history for a given `sessionId`.
- **Dependencies**: `UnifiedAiEngine`, `KnowledgeService`, `AiSessionDao`, `MessageDao`
- **Caller**: `AIAssistant.tsx`

#### `AiPromptsServlet` (`/ai/prompts`)
- **Responsibility**: Manages customizable LLM prompt templates (system prompts, RAG templates, flow generation prompts).
- **Public Methods**:
  - `doGet`: Lists global and tenant-customized prompt templates.
  - `doPost`: Creates or updates a tenant prompt template override.
  - `doDelete`: Resets a tenant template back to global default.
- **Dependencies**: `PromptTemplateDao` (integrated in `FlowDao`)

#### `AiProviderServlet` & `AiProviderListServlet` (`/ai/providers`, `/ai/providers/list`)
- **Responsibility**: Manages active LLM provider selection and queries model catalogs / provider health.
- **Public Methods**:
  - `doGet`: Returns list of available AI providers (`gemini`, `groq`, `openrouter`, `ollama`), active selection, and circuit breaker health statuses.
  - `doPost`: Dynamically switches active system AI provider.
- **Dependencies**: `ProviderManager`, `CircuitBreaker`
- **Caller**: `SuperAdminSettings.tsx`

#### `AuditLogsServlet` (`/api/audit-logs`)
- **Responsibility**: Security audit query endpoint.
- **Public Methods**:
  - `doGet`: Returns paginated, filtered audit log records (`tenant_id`, `actor_email`, `action_type`, date range).
- **Dependencies**: `AuditLogDao`
- **Caller**: `AuditLogs.tsx`

#### `CdrServlet` (`/api/cdr`)
- **Responsibility**: Parses Asterisk Call Detail Records (CDR) CSV log files.
- **Public Methods**:
  - `doGet`: Reads `/var/log/asterisk/cdr-csv/Master.csv`, parses records into `CdrRecord` DTOs, returns summary metrics (`CdrSummary`).
- **Dependencies**: File system reader (`/var/log/asterisk/cdr-csv/Master.csv`)
- **Caller**: `CallAnalytics.tsx`

#### `DashboardServlet` (`/api/dashboard`)
- **Responsibility**: Provides metrics for the Tenant Operations Dashboard.
- **Public Methods**:
  - `doGet`: Fetches total calls, call success rate, active IVR flows count, active phone numbers, active queues count, and recent 5 call logs for the caller's tenant.
- **Dependencies**: `DashboardDao`, `CallAnalyticsDao`, `FlowDao`, `PhoneNumberDao`, `QueueDao`
- **Caller**: `TenantAdminDashboard.tsx`

#### `HealthServlet` & `SystemHealthServlet` (`/health`, `/api/system-health`)
- **Responsibility**: Liveness and deep system diagnostic endpoints.
- **Public Methods**:
  - `doGet`: Evaluates PostgreSQL connection, Asterisk AMI socket health, active AI provider circuit breaker state, disk space, and JVM memory usage.
- **Dependencies**: `DatabaseManager`, `AsteriskAmiClient`, `ProviderManager`, `SystemHealthService`
- **Caller**: Docker healthcheck, `SystemHealth.tsx`

#### `NotificationServlet` (`/api/notifications`)
- **Responsibility**: In-app notifications retrieval and status management.
- **Public Methods**:
  - `doGet`: Returns unread notifications for tenant/user.
  - `doPost`: Marks notification(s) as read or broadcasts new notification.
- **Dependencies**: `NotificationDao`
- **Caller**: `NotificationBell.tsx`, `TenantLayout.tsx`, `SuperAdminLayout.tsx`

#### `SuperAdminDashboardServlet` & `SuperAdminReportsServlet` (`/api/super-admin/dashboard`, `/api/super-admin/reports`)
- **Responsibility**: Platform-wide metrics, tenant growth tracking, and system usage reports.
- **Public Methods**:
  - `doGet`: Aggregates active tenant counts, subscription revenue, platform call volumes, tenant tier distributions, and generates downloadable CSV reports.
- **Dependencies**: `SuperAdminDashboardDao`, `ReportsDao`
- **Caller**: `SuperAdminDashboard.tsx`, `Reports.tsx`

#### `SuperAdminUsersServlet` & `TenantCompaniesServlet` (`/api/super-admin/users`, `/api/super-admin/companies`)
- **Responsibility**: Admin controls over user accounts and tenant company subscriptions.
- **Public Methods**:
  - `doGet`: Lists all registered platform users and tenant companies with subscription status.
  - `doPost`: Creates new tenant company, suspends user/company, or overrides subscription plan.
- **Dependencies**: `UserDao`, `TenantDao`, `AuditLogDao`, `NotificationDao`
- **Caller**: `SuperAdminUsers.tsx`, `SuperAdminCompanies.tsx`, `TenantCompanies.tsx`

#### `TenantPhoneNumberServlet`, `TenantQueueServlet`, `TenantSipExtensionServlet` (`/api/telephony/phone-numbers`, `/api/telephony/queues`, `/api/telephony/sip-extensions`)
- **Responsibility**: Complete CRUD operations for tenant telephony resources.
- **Public Methods**:
  - `doGet`: Lists tenant phone numbers (DIDs), call queues, or PJSIP extensions.
  - `doPost`: Provisions new DID, queue, or extension.
  - `doPut`: Binds IVR flow to phone number or updates queue strategy.
  - `doDelete`: Releases phone number, queue, or extension.
- **Dependencies**: `PhoneNumberDao`, `QueueDao`, `SipExtensionDao`, `AgentStateDao`
- **Caller**: `PhoneNumbers.tsx` (mocked UI), `QueueManagement.tsx`, `SIPExtensions.tsx`

#### `VoicePromptsGenerateServlet` & `VoicePromptsStreamServlet` (`/api/voice-prompts/generate`, `/api/voice-prompts/stream`)
- **Responsibility**: Audio prompt generation and streaming.
- **Public Methods**:
  - `doPost`: Synthesizes audio prompt using system TTS (`espeak` / `festival`) or mock fallback, writes `.wav` file into `/var/lib/asterisk/sounds`, and inserts metadata into `voice_prompts` table.
  - `doGet`: Streams requested `.wav` file from `/var/lib/asterisk/sounds` back to the browser audio player.
- **Dependencies**: `VoicePromptDao`, `TtsEngine`, File System (`/var/lib/asterisk/sounds`)
- **Caller**: `VoicePrompts.tsx`

---

### 2. Data Access Layer (`com.nexusivr.ai.dao`)

#### `DatabaseManager`
- **Responsibility**: Manages JDBC connections, HikariCP/Standard connection pooling, database transactions, and schema query execution.
- **Public Methods**:
  - `getConnection()`: Obtains active `Connection` from `DATABASE_URL`.
  - `executePreparedUpdate(String sql, Object... params)`: Executes `UPDATE`/`INSERT`/`DELETE`.
  - `executeQuery(String sql, ResultSetConsumer consumer, Object... params)`: Executes query with lambda mapping.

#### `UserDao`
- **Responsibility**: Database operations on `users` table.
- **Public Methods**: `findByEmail(email)`, `findById(id)`, `createUser(user)`, `updateStatus(id, status)`, `updateLastLogin(id)`.

#### `TenantDao`
- **Responsibility**: Database operations on `tenants` table.
- **Public Methods**: `findAll()`, `findById(id)`, `createTenant(name, status)`, `updateSubscription(tenantId, planId, status, expiresAt)`, `updateStatus(tenantId, status)`.

#### `FlowDao`
- **Responsibility**: Storage and retrieval of IVR flow draft JSONs and published VXML scenarios.
- **Public Methods**: `saveDraft(tenantId, flowId, jsonContent)`, `getDraft(tenantId, flowId)`, `publishFlow(tenantId, flowId, vxmlContent)`, `listFlows(tenantId)`.

#### `KnowledgeDocumentDao`
- **Responsibility**: Database operations for RAG documents, chunks, and vector similarity search.
- **Public Methods**: `insertDocument(doc)`, `insertChunks(chunks)`, `insertEmbeddings(embeddings)`, `searchSimilarChunks(tenantId, float[] queryVector, int topK)` (executes `<->` HNSW cosine distance SQL query).

#### `Telephony Analytics DAOs` (`CallAnalyticsDao`, `TelephonyAnalyticsDao`, `DashboardDao`, `ReportsDao`, `SuperAdminDashboardDao`)
- **Responsibility**: Aggregate statistics and call activity logging over `call_logs`, `call_events`, `tenants`, `users`, and `transactions`.

#### `Telephony Resource DAOs` (`PhoneNumberDao`, `SipExtensionDao`, `QueueDao`, `AgentStateDao`, `VoicePromptDao`)
- **Responsibility**: Explicit PostgreSQL CRUD operations for DIDs, SIP extensions, queues, agents, and audio prompt files with full `tenant_id` scoping.

#### `AuditLogDao` & `NotificationDao`
- **Responsibility**: Persistence and retrieval for security audit records and user/tenant in-app notifications.

---

### 3. Service Layer & AI Pipeline (`com.nexusivr.ai.service` & `com.nexusivr.ai.ai`)

#### `UnifiedAiEngine`
- **Responsibility**: Master facade orchestrating generative AI services.
- **Public Methods**:
  - `generateFlow(FlowGenerationRequest)`: Delegates to `DomainFlowGenerator`.
  - `refinePrompt(String userPrompt)`: Delegates to `PromptRefinerService`.
  - `chat(ChatRequest)`: Executes conversational turn with RAG context via `ProviderManager`.
  - `summarize(SummarizationRequest)`: Generates concise dialogue summary.
  - `improveFlow(FlowImprovementRequest)`: Applies targeted AI patches to existing flow model.

#### `ProviderManager` & `CircuitBreaker`
- **Responsibility**: High-availability AI provider routing with automatic fallback.
- **Workflow**:
  1. Primary Provider: Attempt request via configured provider (`OPENROUTER` by default).
  2. Circuit Breaker Check: Inspect provider error rates. If OPEN due to 429/500 errors, immediately bypass.
  3. Fallback: On failure/timeout, fail over to `GROQ` (`llama-3.3-70b-versatile`), then `GEMINI` (`gemini-2.0-flash`), then `OLLAMA` (`granite4.1:8b`).

#### `DomainFlowGenerator` (7-Pass Generative AI Pipeline)
- **Responsibility**: Constructs production-grade IVR flows from high-level natural language prompts.
- **7 Pipeline Passes**:
  1. **Pass 1: Intention & Domain Analysis**: Identifies business domain (Banking, Healthcare, Telecom, Restaurant, Hotel) and core user intents.
  2. **Pass 2: Flow Architecture Planning**: Constructs high-level node topology and state tree.
  3. **Pass 3: Node & Edge Synthesis**: Generates individual menu choices, inputs, transfers, webhooks, condition branches.
  4. **Pass 4: Voice Prompt Writing**: Drafts natural, conversational voice prompts for each node in English/Arabic.
  5. **Pass 5: Action & Integrations Binding**: Attaches API webhooks, queue transfers, and voicemail handlers.
  6. **Pass 6: Structural & Business Rule Validation**: Validates graph acyclicity, unreachable nodes, missing audio prompts, and invalid choices.
  7. **Pass 7: Polish & Auto-Repair**: Fixes structural defects and serializes final `FlowModel` JSON.

#### `ModelToVxmlExporter` & `FlowPublishService`
- **Responsibility**: Transforms visual `FlowModel` JSON graph into standard VoiceXML 2.1 document.
- **Publishing Workflow**:
  1. Validates `FlowModel` JSON.
  2. Generates `.vxml` content via `ModelToVxmlExporter`.
  3. Saves VXML file into scenario directory: `IVR-engine/scenarios/{tenant_id}_{flow_name}.vxml`.
  4. Invokes shell script `./IVR-engine/add_extension.sh` to dynamically inject dialplan rule into Asterisk `/etc/asterisk/extensions.conf` and reload Asterisk via AMI.
