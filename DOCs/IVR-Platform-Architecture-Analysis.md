# AI-Powered Multi-Tenant IVR Platform
## Architecture & Domain Analysis

---

## 1. Business Problem Analysis

### What problem does the platform solve?

Traditional IVR systems are built and deployed per-company: each business buys a PBX, hires integrators, and waits weeks for even a small change to a call flow. This creates three persistent problems:

- **High cost of entry.** Small and mid-size businesses (clinics, restaurants, local branches of larger companies) cannot afford a dedicated Asterisk deployment, a telecom engineer, and ongoing maintenance.
- **Slow change cycles.** Editing a menu, adding a queue, or changing business hours usually requires engineering involvement and redeployment, not a business-user action.
- **No shared intelligence.** Every company reinvents call routing, reporting, and prompt design from scratch, with no AI assistance to speed up flow design or surface operational insights.

This platform solves that by turning IVR infrastructure into a **multi-tenant, self-service SaaS product**. A single shared Asterisk/telephony backbone and a single web application serve many independent companies, each with an isolated configuration space, while an AI assistant reduces the effort of designing and improving call flows.

### Who are the target users?

Distinct personas exist, and the architecture must serve all of them, not just "the caller":

- **Platform Owner / Super Admin** — Anthropic-style operator of the SaaS itself: onboards tenants, manages billing, monitors platform health, enforces global policy.
- **Tenant Admin** — a company's administrator: configures departments, employees, extensions, queues, IVRs, and views that company's reports.
- **Tenant Agent / Employee** — receives transferred calls, handles voicemail, may have a lightweight desktop/softphone experience.
- **End Caller** — the customer calling in; never touches the web app, only interacts through voice/DTMF.
- **AI-assisted Designer** — conceptually a "role" the Tenant Admin plays when using the AI to generate or refine a flow rather than building it node-by-node.

### Why would businesses use it instead of a traditional IVR?

- **No infrastructure ownership** — no PBX hardware, no SIP trunk management, no on-prem maintenance.
- **Self-service configuration** — business users, not engineers, can build and change flows through a visual builder.
- **Faster time-to-value** — AI-generated starter flows ("Build me a hospital appointment IVR") collapse days of design into minutes.
- **Elastic cost model** — pay for usage (extensions, minutes, queues) instead of capital expenditure.
- **Built-in analytics** — reporting and call analytics are part of the platform rather than a separate integration project.
- **Consistent security and compliance posture** — tenant isolation, recording retention, and access control are handled once, centrally, instead of per-company.

---

## 2. Business Requirements

### 2.1 Functional Requirements

**Platform / Tenant Management**
- Company (tenant) registration, onboarding, and approval workflow
- Tenant-level plan/subscription and feature entitlement management
- Tenant suspension, reactivation, and offboarding (data export/deletion)
- Super Admin console for platform-wide oversight

**Identity & Access**
- User registration and authentication scoped to a tenant
- Role-based access control (Super Admin, Tenant Admin, Manager, Agent, Auditor)
- Department- and team-based permission scoping
- Session management, password policy, optional MFA

**Organization Structure**
- Department creation and hierarchy
- Employee/agent management, assignment to departments and queues
- SIP extension provisioning per employee/department

**Telephony**
- SIP/PJSIP trunk and DID (phone number) management per tenant
- Inbound call reception and routing into the correct tenant's IVR
- Outbound calling capability (agent-initiated, callback flows)
- Call queueing with configurable strategies (round robin, longest idle, skills-based)
- Call transfer (blind/attended) to agent or queue
- Call recording, with storage and retrieval
- Voicemail capture and retrieval

**IVR Builder & Execution**
- Visual, node-based flow builder (drag-and-drop graph editor)
- Node library: Greeting, Playback, Menu, DTMF Input, Queue, Agent Transfer, Voicemail, Recording, API Request, Database Lookup, Business Hours, Holiday Check, Hangup
- Flow versioning, draft vs. published states
- Flow validation (no orphan nodes, no infinite loops, required exits defined)
- Deployment of a flow to a live DID
- Runtime execution engine that walks the graph per call

**Voice Prompts & Media**
- Upload, store, and manage audio prompts
- Text-to-speech generation of prompts
- Prompt localization (multiple languages per tenant)

**AI Assistant**
- Generate an IVR flow from a natural-language description
- Suggest improvements to an existing flow (e.g., reduce steps, add fallback paths)
- Generate voice prompt scripts
- Summarize reports and surface anomalies/recommendations in plain language

**Reporting & Analytics**
- Call detail records (CDR) per tenant
- Queue performance metrics (wait time, abandonment rate, SLA)
- Agent performance metrics
- IVR flow analytics (drop-off points, path popularity)
- Exportable reports (CSV/PDF)
- Dashboards with time-range filtering

**Billing (if the SaaS is commercial)**
- Usage metering (minutes, extensions, storage, AI calls)
- Invoice generation and payment integration

### 2.2 Non-Functional Requirements

- **Multi-tenancy & data isolation** — no tenant can access another tenant's data, whether through direct access or aggregation leakage.
- **Scalability** — the telephony layer and the web/API layer must scale independently; concurrent calls across tenants can spike unpredictably.
- **Availability** — telephony is expected to have very high uptime (a down IVR means missed calls); target something like 99.9%+ for the call path specifically.
- **Performance** — IVR node execution must respond within the latency tolerances of real-time telephony (sub-second decisioning per node in most cases).
- **Security** — encryption in transit and at rest, SIP trunk security (registration hijacking, toll fraud prevention), tenant-level access control, audit logging.
- **Compliance** — call recording consent/retention rules vary by industry (healthcare, banking) and jurisdiction; the platform must support configurable retention and consent policies.
- **Auditability** — every configuration change (who changed which IVR node, when) should be traceable.
- **Extensibility** — new node types, new channels (SMS, WhatsApp, chat) should be addable without re-architecting the core.
- **Observability** — centralized logging, metrics, and tracing across Tomcat services and Asterisk.
- **Disaster recovery** — backup/restore for both configuration data and recordings.
- **Maintainability** — clear module boundaries so a Core Java/Servlet codebase doesn't collapse into a monolith that's hard to change.
- **Usability** — the visual builder needs to be usable by non-technical business admins, not just engineers.

### 2.3 Missing Requirements Worth Adding

A few things a real enterprise product would need that aren't in the current scope:

- **Multi-channel support roadmap** — even if phase 1 is voice-only, the domain model should not hard-code "call" as the only channel; WhatsApp/SMS/chat bots are common companion features.
- **Outbound campaign management** — many IVR platforms also support outbound dialing (appointment reminders, surveys).
- **Consent & compliance management** (e.g., call recording announcements, GDPR-style data subject requests) — critical for banks/hospitals specifically named in your use cases.
- **SLA & alerting** — proactive notification when a queue breaches SLA or an IVR flow starts failing.
- **Sandbox/testing mode** — test-call simulation for a flow before publishing it live.
- **Tenant-level customization of the caller experience** — branded hold music, custom TTS voices.
- **Rate limiting / fraud protection** — toll fraud and DDoS-style call flooding are real risks for any multi-tenant telephony platform.
- **API access for tenants** — allowing a tenant's own systems (CRM, EHR, booking system) to push/pull data via a public API, not just the "API Request" node calling out.

---

## 3. Business Domains

- **Identity & Access Management (IAM)** — authentication, authorization, roles, sessions.
- **Tenant Management** — company lifecycle, plans, entitlements, branding.
- **Organization Management** — departments, employees, extensions, teams.
- **Telephony / SIP Gateway** — trunks, DIDs, Asterisk integration, real-time call signaling.
- **IVR Design (Flow Builder)** — node graph modeling, validation, versioning.
- **IVR Execution (Runtime Engine)** — the engine that interprets a deployed flow during a live call.
- **Media & Prompt Management** — audio assets, TTS, localization.
- **Queueing & Routing** — queue strategies, agent availability, transfer logic.
- **Call Records & Recording** — CDRs, recordings, voicemail storage.
- **Reporting & Analytics** — aggregation, dashboards, exports.
- **AI Assistant** — LLM integration, prompt orchestration for flow generation/suggestions.
- **Billing & Metering** *(if commercial)* — usage tracking, invoicing.
- **Audit & Compliance** — change history, consent tracking, retention policy enforcement.
- **Notification** — alerts to admins (SLA breaches, deployment failures, billing events).
- **Platform Administration** — Super Admin tooling, tenant oversight, system health.

These are not all equal in size — some (IVR Design vs. IVR Execution) are deliberately split even though they sound similar, because *designing* a flow and *executing* a flow have very different technical concerns (a graph editor vs. a real-time interpreter).

---

## 4. Domain Entities

Grouped roughly by domain, but relationships cut across groups.

### Identity & Access
**User**
- *Purpose:* represents any human who logs into the web app (admin, manager, agent, auditor).
- *Responsibilities:* authentication identity, holds role/permission assignments.
- *Key attributes:* name, email/username, credentials, status (active/locked), MFA settings, last login.
- *Relationships:* belongs to one Tenant; has one or more Roles; may be linked to an Employee record.
- *Lifecycle:* invited → active → (locked/suspended) → deactivated.

**Role / Permission**
- *Purpose:* defines what a User is allowed to do.
- *Responsibilities:* groups permissions (e.g., "Tenant Admin", "Agent", "Report Viewer").
- *Key attributes:* name, permission set, scope (tenant-wide vs. department-scoped).
- *Relationships:* assigned to Users; may be tenant-defined (custom roles) or platform-defined.
- *Lifecycle:* created by Super Admin or Tenant Admin → assigned → revoked.

**Session / AuthToken**
- *Purpose:* represents an authenticated session.
- *Responsibilities:* tracks login state, expiry, device/context.
- *Key attributes:* token, issued-at, expiry, IP/device metadata.
- *Relationships:* belongs to a User.
- *Lifecycle:* issued at login → refreshed → expired/revoked.

### Tenant Management
**Tenant (Company)**
- *Purpose:* the isolation boundary of the entire platform; represents one customer business.
- *Responsibilities:* owns all data created within its boundary (users, IVRs, calls, reports).
- *Key attributes:* company name, industry type (bank/hospital/hotel/etc.), status, plan, branding (logo, TTS voice defaults), timezone.
- *Relationships:* has many Users, Departments, Employees, IVRs, DIDs, Reports.
- *Lifecycle:* registration → pending approval → active → suspended → offboarded/archived.

**Subscription / Plan**
- *Purpose:* defines what a tenant is entitled to use.
- *Responsibilities:* caps on extensions, queues, AI calls, storage; feature flags.
- *Key attributes:* plan name, limits, billing cycle, price.
- *Relationships:* belongs to a Tenant.
- *Lifecycle:* assigned at signup → upgraded/downgraded → expired/cancelled.

### Organization Structure
**Department**
- *Purpose:* logical grouping within a tenant (e.g., "Cardiology", "Front Desk", "Billing").
- *Responsibilities:* groups Employees and Queues; can have its own business hours.
- *Key attributes:* name, description, business hours override.
- *Relationships:* belongs to Tenant; has many Employees, Queues.
- *Lifecycle:* created → active → archived.

**Employee (Agent)**
- *Purpose:* a person who handles calls (may or may not also be a system User).
- *Responsibilities:* receives transferred calls, updates availability status.
- *Key attributes:* name, status (available/busy/offline), skills/tags, assigned extension.
- *Relationships:* belongs to Department(s); linked to a SIP Extension; may belong to one or more Queues; optionally linked to a User account.
- *Lifecycle:* onboarded → active → on leave → offboarded.

**SIP Extension**
- *Purpose:* the telephony endpoint an Employee's phone/softphone registers to.
- *Responsibilities:* routes calls to a specific device/person.
- *Key attributes:* extension number, SIP credentials, registration status.
- *Relationships:* belongs to Tenant; assigned to one Employee.
- *Lifecycle:* provisioned → active → deactivated/reassigned.

### Telephony
**DID / Phone Number**
- *Purpose:* the public phone number customers dial to reach a tenant.
- *Responsibilities:* entry point that maps to a deployed IVR flow.
- *Key attributes:* number, provider/trunk reference, tenant assignment.
- *Relationships:* belongs to Tenant; points to one deployed IVR Flow Version.
- *Lifecycle:* purchased/ported → assigned → released.

**SIP Trunk**
- *Purpose:* the connection between Asterisk and the outside telephony network (or per-tenant trunk if required).
- *Responsibilities:* carries inbound/outbound signaling and media.
- *Key attributes:* trunk provider, credentials, capacity/concurrency limits.
- *Relationships:* may be shared platform-wide or dedicated per Tenant; owns many DIDs.
- *Lifecycle:* configured → active → disabled.

**Call Session**
- *Purpose:* represents one live/completed phone call end-to-end.
- *Responsibilities:* tracks the caller's journey from ring to hangup.
- *Key attributes:* caller number, DID dialed, start/end time, duration, disposition (answered/abandoned/voicemail), current node (while live).
- *Relationships:* belongs to Tenant; associated with a Flow Version, possibly a Queue, an Employee, a Recording, a Voicemail.
- *Lifecycle:* ringing → in-progress (executing flow) → connected-to-agent/queue/voicemail → ended.

### IVR Design
**IVR Flow**
- *Purpose:* the logical, named workflow a tenant designs (the "project" a Tenant Admin edits).
- *Responsibilities:* container for versions; represents intent, independent of any single deployment.
- *Key attributes:* name, description, owning department (optional), current draft version pointer.
- *Relationships:* belongs to Tenant; has many Flow Versions.
- *Lifecycle:* created → iterated on (drafts) → published → retired.

**Flow Version**
- *Purpose:* an immutable snapshot of a flow's graph at a point in time — enables safe deployment and rollback.
- *Responsibilities:* holds the actual node graph for that version.
- *Key attributes:* version number, status (draft/published/archived), created-by, created-at, validation status.
- *Relationships:* belongs to an IVR Flow; composed of many Nodes and Connections; can be deployed to one or more DIDs.
- *Lifecycle:* draft → validated → published → superseded → archived.

**Node**
- *Purpose:* a single step in the workflow graph (Greeting, Menu, Queue, etc.).
- *Responsibilities:* encapsulates one unit of behavior and its configuration.
- *Key attributes:* node type, configuration payload (varies per type), position (for the visual canvas).
- *Relationships:* belongs to a Flow Version; connected to other Nodes via Connections (edges).
- *Lifecycle:* added → configured → connected → (removed during editing).

**Connection (Edge)**
- *Purpose:* defines the directed link between two nodes, optionally conditioned on an outcome (e.g., "press 1" branch).
- *Responsibilities:* determines execution order/branching.
- *Key attributes:* source node, target node, condition/label (e.g., DTMF digit, "success"/"failure" outcome).
- *Relationships:* connects two Nodes within the same Flow Version.
- *Lifecycle:* created during design → may be revalidated when nodes change.

### Media & Prompts
**Voice Prompt**
- *Purpose:* an audio asset played to callers (greeting, menu options, hold messages).
- *Responsibilities:* stores or references playable audio; may be TTS-generated or uploaded.
- *Key attributes:* name, language/locale, source (upload vs. TTS), file reference, duration.
- *Relationships:* belongs to Tenant; referenced by one or more Nodes (e.g., Greeting, Playback).
- *Lifecycle:* created/generated → used in flows → replaced/archived.

### Queueing & Routing
**Call Queue**
- *Purpose:* holds callers waiting for the next available agent.
- *Responsibilities:* applies a routing strategy, tracks wait time, enforces SLA thresholds.
- *Key attributes:* name, strategy (round robin, longest idle, skills-based), max wait time, overflow behavior.
- *Relationships:* belongs to Tenant/Department; has many Employees (as members); receives Call Sessions from Nodes.
- *Lifecycle:* created → active → paused → archived.

**Queue Membership**
- *Purpose:* represents an Employee's participation in a Queue (many-to-many with attributes).
- *Responsibilities:* tracks priority/skill weighting of that employee within that queue.
- *Key attributes:* priority, skill tags.
- *Relationships:* links Employee and Call Queue.
- *Lifecycle:* added → active → removed.

### Call Records & Recording
**Call Detail Record (CDR)**
- *Purpose:* the permanent historical record of a completed call, used for reporting.
- *Responsibilities:* stores the finalized facts about a call for analytics.
- *Key attributes:* timestamps, duration, disposition, path taken (nodes visited), queue/agent involved.
- *Relationships:* derived from a Call Session; belongs to Tenant.
- *Lifecycle:* created at call end → immutable → eventually archived per retention policy.

**Recording**
- *Purpose:* the actual audio file of a recorded call.
- *Responsibilities:* stores/retrieves call audio; enforces retention and access rules.
- *Key attributes:* file reference, duration, consent flag, retention expiry.
- *Relationships:* belongs to a Call Session; belongs to Tenant.
- *Lifecycle:* recorded → stored → (accessed/reviewed) → purged per retention policy.

**Voicemail**
- *Purpose:* a message left by a caller when no agent was available.
- *Responsibilities:* stores audio + metadata; tracks read/unread state.
- *Key attributes:* file reference, duration, caller number, read status.
- *Relationships:* belongs to a Call Session; assigned to an Employee/Department.
- *Lifecycle:* recorded → new → listened → archived/deleted.

### Reporting & Analytics
**Report**
- *Purpose:* a generated or on-demand analytical view (e.g., "Queue Performance – Q1").
- *Responsibilities:* aggregates CDRs and other events into a consumable format.
- *Key attributes:* type, date range, generated-at, format (dashboard/export).
- *Relationships:* belongs to Tenant; built from CDRs, Queues, Flow Versions.
- *Lifecycle:* requested/scheduled → generated → viewed/exported → (retained/deleted).

**Analytics Event**
- *Purpose:* fine-grained telemetry (a caller hit Node X, pressed digit Y) used to compute flow analytics like drop-off points.
- *Responsibilities:* captures granular in-call events for later aggregation.
- *Key attributes:* event type, node reference, timestamp, call session reference.
- *Relationships:* belongs to a Call Session; references a Node.
- *Lifecycle:* emitted during call execution → aggregated → optionally purged after aggregation.

### AI Assistant
**AI Request / Generation Job**
- *Purpose:* represents one interaction with the external LLM (e.g., "generate a flow for a hotel front desk").
- *Responsibilities:* stores the prompt, response, and resulting artifact (a proposed Flow Version, a prompt script, a report summary).
- *Key attributes:* request type, input prompt, generated output, status, tokens/cost used.
- *Relationships:* belongs to Tenant/User; may produce a draft Flow Version or Voice Prompt.
- *Lifecycle:* requested → processing → completed/failed → (accepted or discarded by the admin).

### Audit & Compliance
**Audit Log Entry**
- *Purpose:* records who changed what, when, across the platform.
- *Responsibilities:* provides traceability for configuration changes (flow edits, permission changes, tenant setting changes).
- *Key attributes:* actor (User), action, target entity/type, timestamp, before/after snapshot (optional).
- *Relationships:* references a User and the affected entity.
- *Lifecycle:* append-only, retained per compliance policy.

**Consent Record**
- *Purpose:* tracks whether/when a caller consented to recording (relevant for banks/hospitals).
- *Responsibilities:* supports compliance reporting.
- *Key attributes:* consent given (bool), method (announcement played), timestamp.
- *Relationships:* belongs to a Call Session.
- *Lifecycle:* created at call start → immutable.

### Billing (if applicable)
**Usage Record**
- *Purpose:* meters consumption against the tenant's plan (minutes used, AI calls made, storage consumed).
- *Responsibilities:* feeds invoicing and enforces plan limits.
- *Key attributes:* metric type, quantity, period.
- *Relationships:* belongs to Tenant.
- *Lifecycle:* accumulated continuously → reset per billing cycle.

**Invoice**
- *Purpose:* billing document for a tenant's usage/subscription in a period.
- *Responsibilities:* summarizes charges.
- *Key attributes:* period, line items, total, payment status.
- *Relationships:* belongs to Tenant.
- *Lifecycle:* generated → sent → paid/overdue.

---

## 5. Modules (Entity Grouping)

**1. Identity & Access Module**
`User`, `Role/Permission`, `Session/AuthToken`
→ Grouped because they exist purely to answer "who is this and what can they do," independent of telephony or tenant business logic. This module should be reusable/foundational across the whole platform.

**2. Tenant Module**
`Tenant`, `Subscription/Plan`
→ These define the isolation and entitlement boundary that every other module depends on. Kept minimal and central so every other module can reference "tenant_id" against a single authoritative source.

**3. Organization Module**
`Department`, `Employee`, `SIP Extension`
→ Represents the tenant's internal structure — the "who works here and how do they get called" layer, distinct from platform-level identity.

**4. Telephony Module**
`DID/Phone Number`, `SIP Trunk`, `Call Session`
→ Everything concerned with the raw signaling/connectivity layer and the live/completed call as a telephony object (not yet "what happened for reporting," which is CDR's job).

**5. IVR Design Module**
`IVR Flow`, `Flow Version`, `Node`, `Connection`
→ Purely the authoring/versioning concern. This module is only ever touched by the builder UI and validation logic — it never runs a live call.

**6. IVR Execution Module**
Uses `Flow Version`, `Node`, `Connection` (read-only) + produces `Call Session` state transitions and `Analytics Event`
→ Deliberately separated from IVR Design because execution has real-time constraints and a completely different failure mode (a bad deploy vs. a bad runtime interpretation are different problems to isolate).

**7. Media Module**
`Voice Prompt`
→ Small but distinct because prompts are a shared asset referenced by many nodes and may involve external TTS integration, with its own lifecycle and localization concerns.

**8. Queueing & Routing Module**
`Call Queue`, `Queue Membership`
→ Routing strategy and agent-availability logic is complex enough (and reused by multiple node types) to deserve its own module rather than living inside "Organization" or "IVR Execution."

**9. Call Records Module**
`CDR`, `Recording`, `Voicemail`
→ The "what happened, and where's the evidence" layer — read-heavy, compliance-sensitive, and consumed by Reporting.

**10. Reporting & Analytics Module**
`Report`, `Analytics Event`
→ Aggregation and presentation layer built on top of Call Records and Execution telemetry.

**11. AI Module**
`AI Request/Generation Job`
→ Isolated because it talks to an external system (LLM API), has cost/usage implications, and its outputs (draft flows, prompts) must be reviewed/accepted by a human before affecting live modules.

**12. Audit & Compliance Module**
`Audit Log Entry`, `Consent Record`
→ Cross-cutting by nature but modeled as its own module because compliance retention rules differ from operational data retention rules.

**13. Billing Module**
`Usage Record`, `Invoice`
→ Financial concern, naturally separate from operational modules; often the first module to be extracted into its own service if the platform scales.

---

## 6. Missing Features Worth Adding

- **Flow testing / simulation mode** — let a Tenant Admin "run" a flow with simulated DTMF input before publishing, without tying up a real phone line. Prevents broken flows from going live.
- **Business continuity / failover routing** — a way to define "if the platform or Asterisk cluster serving this tenant is degraded, fall back to a static forwarding number." Enterprise buyers will ask for this.
- **Real-time agent dashboard** — live queue view (calls waiting, agent status) is table stakes for any contact-center-adjacent product (Genesys/Amazon Connect both lead with this).
- **Skills-based routing** — beyond round robin, route by agent skill/language tags, which matters a lot for hospitals/banks with specialized departments.
- **Callback-from-queue** — let a caller request a callback instead of waiting on hold; hugely valued in real contact centers.
- **A/B testing of flows** — compare drop-off rates between two flow versions.
- **Webhook/event system** — let tenants subscribe to events (call ended, voicemail received) to integrate with their own systems, complementing the "API Request" node which only covers outbound calls from the flow.
- **Tenant-facing public API & API keys** — so a tenant's CRM/EHR can pull reports or push data (e.g., appointment status) rather than relying solely on the "Database Lookup"/"API Request" node.
- **Multi-language / multi-region support** — prompt localization, plus possibly region-pinned deployments for data residency (relevant for banks/government).
- **Granular consent & compliance workflows** — configurable recording announcements, consent capture, and data subject access/erasure requests — very relevant given the target verticals include banks and hospitals.
- **White-labeling** — custom domain/branding for tenants who want the portal to look like their own product.
- **Notification center** — email/SMS/push alerts to Tenant Admins for SLA breaches, failed deployments, low balance, etc.
- **Sandbox/staging tenant environment** — separate from production for large tenants to test big changes safely.
- **Version diffing/rollback UI** — visually compare two Flow Versions and one-click rollback, not just "publish a new version."

---

## 7. Architecture Review

### Weak Points in the Current Design

- **Tight coupling risk between IVR Design and IVR Execution.** If both are implemented as the same module/table set with no separation, a change to how the builder stores a node can break the live call engine. These need a clear contract (a published Flow Version is the only thing the runtime ever reads).
- **No explicit real-time layer.** Java Servlets + JDBC is a fine fit for the web/admin CRUD side, but the IVR *execution* engine has real-time, stateful, concurrency-heavy requirements (a live call is a long-running state machine) that map poorly onto a typical request/response servlet model. This needs to be architected as a distinct component — likely a persistent process or service that talks to Asterisk (via AMI/ARI) and holds in-memory or fast-store call state, separate from the servlet-based admin backend.
- **Underspecified integration between Asterisk and the application layer.** The document says "Asterisk → Load Company → Load IVR" but doesn't yet define *how* — this is one of the most architecturally important decisions in the whole system (Dialplan + AGI/AMI vs. ARI-driven external control app) and should be resolved before database design, since it affects what "Call Session" state needs to look like.
- **AI is bolted onto "administration" without a review/approval boundary defined.** The requirements say AI generates flows/prompts, but there's no explicit state distinguishing "AI-proposed, not yet reviewed" from "human-approved, live." This should be a first-class concept (the `AI Request` producing a *draft* Flow Version, never a published one directly).

### Missing Modules

- A dedicated **Execution/Runtime module**, distinct from IVR Design (noted above).
- A **Notification module** (alerts to admins) — currently absent entirely.
- A **Compliance/Consent module**, which matters given the named verticals (banks, hospitals).
- A **Public API/Integration module** for tenant-side system integration, beyond what a Node can call outward.

### Scalability Concerns

- **Telephony scaling is different from web scaling.** Concurrent call capacity is bound by Asterisk instance capacity (CPU, RTP bandwidth), while the admin web app scales more conventionally. These should be designed (and eventually deployed/scaled) as separate tiers from day one, even though Docker will host both — don't assume one scaling policy fits both.
- **Multi-tenant data growth is uneven.** A bank tenant might generate far more call volume than a small clinic. The data model and later the database design should anticipate partitioning/sharding strategies by tenant, and reporting queries should be designed to never scan across tenants.
- **Recording storage grows unboundedly** without a retention policy — this is a storage cost and compliance risk simultaneously.
- **AI calls have latency and cost implications** — the AI module should be decoupled (async job model) rather than assumed synchronous, since LLM calls can be slow and admins generating a full flow shouldn't block the UI thread.

### Security Concerns

- **Tenant isolation must be enforced at every layer**, not just the database — API endpoints, session context, and even the Asterisk dialplan routing logic must always be tenant-scoped to prevent cross-tenant data leakage or, worse, cross-tenant call routing.
- **SIP/telephony-specific attack surface** — toll fraud (unauthorized outbound calling through compromised extensions), SIP registration hijacking, and DDoS via call flooding are real risks unique to this platform that a typical web SaaS architecture review would miss. Rate limiting and anomaly detection on call volume per tenant/extension should be planned.
- **Recording and voicemail access control** — these are sensitive audio assets (potentially containing PII, health information, financial details) and need the same rigor as any document storage system: encryption at rest, scoped access, audit logging on playback/download.
- **AI prompt-injection surface** — if the AI ever ingests caller-provided data (e.g., "summarize this call") as part of a prompt to the LLM, that's an injection vector into the admin-facing AI output and should be treated with the same caution as any external input.

### Maintainability Concerns

- **Core Java Servlets + DAO/MVC is a legitimate, learnable stack for a graduation project, but it will require strict internal module boundaries to avoid becoming a "big ball of mud."** Since there's no framework (like Spring) enforcing layering, the discipline has to be manual — this is worth flagging as a deliberate architectural risk to manage via clear package boundaries per module (e.g., `identity`, `tenant`, `ivr.design`, `ivr.execution`, `telephony`, `reporting`, `ai`), even though it's a monolith.
- **Node type extensibility.** The node library (Greeting, Menu, Queue, etc.) should be designed so a new node type doesn't require touching every existing node's code — this argues for a consistent node interface/contract (a strategy pattern equivalent, without prescribing code) rather than a growing if/else chain in the execution engine.

### Future Expansion Opportunities

- Additional channels beyond voice (SMS, WhatsApp, web chat) reusing the same flow-graph concept.
- Outbound campaign engine reusing the same node/flow model for proactive calling.
- Marketplace of pre-built industry-specific flow templates (hospital appointment flow, hotel front desk flow) — a natural extension of the AI generation feature.
- Tenant-level custom node types or scripting for advanced customers.

### Recommendations Before Database Design

1. **Formally separate "IVR Design" and "IVR Execution" as two modules with a one-way contract**: the runtime only ever reads a *published, immutable* Flow Version.
2. **Decide the Asterisk integration pattern explicitly** (ARI-driven external control application is generally the better fit for a dynamic, multi-tenant, graph-driven IVR than static per-tenant dialplans) before modeling `Call Session` state, since this materially affects what needs to be persisted vs. held in memory.
3. **Add the missing modules** (Notification, Compliance/Consent, Public API) to the domain model now, even if their entities stay minimal in v1, so the database design doesn't need to retrofit tenant-scoping and audit hooks later.
4. **Model retention policy as a first-class concept** (on Recordings, CDRs, Analytics Events) rather than an afterthought — it affects storage design significantly.
5. **Treat AI output as always-draft** — make sure the entity model (`AI Request` → draft `Flow Version`) reflects a human-in-the-loop approval step, not a direct-to-production path.
6. **Plan tenant-scoping as a cross-cutting design rule**, applied consistently to every table in the next phase (every table that isn't platform-global carries a tenant identifier, and every DAO enforces it) — worth stating explicitly as a rule before writing any schema.

---

*This document intentionally excludes SQL, database schema, and code, per the request — it is meant to serve as the domain/architecture foundation for the next phase (database design).*
