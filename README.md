# 🎙️ NexusIVR — AI-Powered, Multi-Tenant IVR & Contact Center Platform

**NexusIVR** is a multi-service platform that combines a visual, drag-and-drop IVR flow builder, a 7-pass generative-AI flow engine, a standard VoiceXML runtime, an Asterisk PBX integration layer, and a Paymob-based subscription/billing service. It lets a tenant describe a call flow in natural language (or build it visually), validates and auto-repairs the resulting flow graph, compiles it to VoiceXML 2.1, and publishes it directly onto a live Asterisk dialplan.

> **Scope note:** this README is derived from a direct inspection of the repository's source code, SQL migrations, Docker/Compose configuration, and the project's own `Documentation/` folder — not from assumptions. Sections are explicit about what is implemented, partially implemented, or not yet wired up.

[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Maven](https://img.shields.io/badge/Build-Maven-red)]()
[![React](https://img.shields.io/badge/Frontend-React%2019%20%2B%20TypeScript-61DAFB)]()
[![Vite](https://img.shields.io/badge/Bundler-Vite-646CFF)]()
[![Asterisk](https://img.shields.io/badge/Telephony-Asterisk%20PBX-red)]()
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2015%2B%20%2B%20pgvector-336791)]()
[![Docker](https://img.shields.io/badge/Deploy-Docker%20Compose-2496ED)]()

---

## 📑 Table of Contents

1. 🧭 [Project Overview](#1-project-overview)
2. ✨ [Key Features](#2-key-features)
3. 🏗️ [System Architecture](#3-system-architecture)
4. 🧩 [Component Breakdown](#4-component-breakdown)
5. 🤖 [The AI Flow Generation Pipeline](#5-the-ai-flow-generation-pipeline)
6. 🔌 [LLM Provider Architecture](#6-llm-provider-architecture)
7. 📜 [VoiceXML Pipeline](#7-voicexml-pipeline)
8. 🎨 [Visual IVR Builder](#8-visual-ivr-builder)
9. ☎️ [Asterisk + FastAGI Integration](#9-asterisk--fastagi-integration)
10. 📁 [Project Directory Structure](#10-project-directory-structure)
11. 🧰 [Technology Stack](#11-technology-stack)
12. ✅ [Prerequisites](#12-prerequisites)
13. ⚙️ [Installation & Configuration](#13-installation--configuration)
14. 🔐 [Environment Variables](#14-environment-variables)
15. ▶️ [Running the Project](#15-running-the-project)
16. 🔄 [End-to-End Workflow Example](#16-end-to-end-workflow-example)
17. 📞 [Manual VXML Workflow](#17-manual-vxml-workflow)
18. 🎧 [Custom Voice Recordings](#18-custom-voice-recordings)
19. 📡 [REST API Reference](#19-rest-api-reference)
20. 🗄️ [Database Schema](#20-database-schema)
21. 🛡️ [Security](#21-security)
22. 🏢 [Multi-Tenancy](#22-multi-tenancy)
23. 🩹 [Validation & Auto-Repair](#23-validation--auto-repair)
24. 🧪 [Testing](#24-testing)
25. 🧯 [Troubleshooting](#25-troubleshooting)
26. 🚀 [Deployment](#26-deployment)
27. 🧱 [Extending the Platform](#27-extending-the-platform)
28. ⚠️ [Known Limitations & Technical Debt](#28-known-limitations--technical-debt)
29. 🤝 [Contributing](#29-contributing)
30. 📄 [License](#30-license)

---

## 1. 🧭 Project Overview

### The problem

Traditional IVR development is slow and brittle: VoiceXML is hand-written, call flows are hard-coded into a PBX dialplan, non-technical staff cannot safely make changes, and every new business (banking, healthcare, hospitality, restaurants, etc.) starts from scratch. Publishing a change usually means editing raw XML, manually touching Asterisk configuration, and reloading the dialplan by hand.

### The solution

NexusIVR replaces that workflow with:

- A **natural-language AI Assistant** that turns a short prompt ("Build a bilingual hotel booking IVR") into a structured, production-shaped call flow.
- A **visual canvas** (React Flow-style drag-and-drop) so the generated flow can be inspected and edited by hand.
- A **validation and auto-repair layer** that catches dead ends, orphan nodes, and duplicate DTMF digits before anything is published.
- A **VoiceXML 2.1 compiler** that turns the visual/AI-generated graph into a standards-compliant `.vxml` scenario.
- **One-click Asterisk publishing** that writes the dialplan extension and reloads Asterisk automatically.
- A **FastAGI runtime** (`IVR-engine`) that interprets the published VXML on live calls.
- A **multi-tenant SaaS layer** — authentication, tenant isolation, Super Admin controls, Paymob billing/subscriptions, telephony resource management (SIP extensions, queues, DIDs), analytics, audit logs, and a RAG knowledge base for the AI assistant.

---

## 2. ✨ Key Features

Only features that are actually implemented in the codebase are listed here (cross-checked against `Documentation/10_Known_Limitations_Dummy_Data_and_Inconsistencies.md`).

### AI / Generative
- Natural-language → IVR flow generation via a **7-pass pipeline** (`DomainFlowGenerator`, orchestrated by `UnifiedAiEngine`).
- Prompt refinement / domain detection (`PromptRefinerService`, `DomainDetector`).
- Flow improvement from natural-language instructions, applied as structured patches (`FlowPatchApplier`, `dto/patch/*`).
- Flow validation with actionable suggestions (`FlowModelValidator`, `FlowValidationOrchestrator`).
- Automatic structural repair — orphan reconnection, missing hangup insertion, DTMF collision remapping, cycle repair (`ModelAutoRepair`, `FlowModelAutoRepair`).
- Multi-provider LLM abstraction with circuit-breaker fallback (`ProviderManager`, `CircuitBreaker`).
- Specialized agent roles: business planner, conversation designer, routing expert, optimization advisor, validator assistant, voice-prompt writer (`ai/agents/*`).
- Conversation memory, semantic caching, token usage tracking, quota-aware routing, prompt minimization (`ai/optimization/*`).
- A standalone **RAG microservice** (Python, ChromaDB) for document ingestion and retrieval (`IVR-AI-engine/rag/`), queried by the Java backend via `RagClient`.
- AI-assisted voice prompt / TTS generation with Arabic keyword support (`config/arabic_keywords.json`, `VoicePromptsGenerateServlet`).

### IVR Builder (Frontend)
- Drag-and-drop flow canvas (`FlowCanvas.tsx`) with a dedicated graph engine (`graphEngine.ts`).
- A wide node-type library (see [Section 8](#8-visual-ivr-builder)).
- Client-side VXML import/export (`flowParser.ts`, `vxmlExporter.ts`).
- AI Assistant side panel with a generation stepper showing the 7 pipeline passes (`AiAssistantPanel.tsx`, `GenerationStepper.tsx`).
- Save (draft) vs. Publish (live) separation, each producing its own snapshot.

### VoiceXML
- Bidirectional VXML ⇄ internal `FlowModel` conversion (`VxmlToModelConverter`, `ModelToVxmlExporter`).
- Support for `<form>`, `<menu>`, `<field>`, `<choice>`, `<goto>`, `<if>/<elseif>/<else>`, `<audio>`, and a custom `<ai role="..." options="...">` tag for embedded conversational nodes.
- A DOM-based VXML validator (`VxmlValidator`, implemented independently in both `IVR-engine` and `IVR-AI-engine`).

### Asterisk / Telephony
- FastAGI server listening on TCP `4573` (`FastAgiServerMain`, `VxmlAgiHandler`).
- Dynamic dialplan provisioning via `add_extension.sh`, idempotent re-injection, automatic `dialplan reload`.
- Custom JVoiceXML implementation platform bound to Asterisk channels (`platform/Asterisk*`).
- Call Detail Record and per-node event logging to PostgreSQL (`call_logs`, `call_events`).
- SIP extension (PJSIP) and call-queue (ACD) management, backed by real database tables and REST endpoints.
- Asterisk Manager Interface (AMI) health probing (`AsteriskAmiClient`, `AsteriskMonitor`).

### Platform / SaaS
- JWT-based authentication with SHA-256 password hashing (`JwtUtil`, `PasswordUtil`).
- Multi-tenant data isolation enforced at the DAO layer (every query scoped by `tenant_id`).
- Super Admin console: tenant management, user management, plan overrides, platform-wide reports, system health.
- Paymob payment gateway integration with SHA-512 HMAC callback verification and a subscription-expiry scheduler.
- In-app notifications, audit logging, telephony analytics, and a dedicated System Health diagnostics endpoint.

---

## 3. 🏗️ System Architecture

```
                                   ┌───────────────────────────────┐
                                   │          IVR-webapp           │
                                   │  React 19 + TypeScript + Vite │
                                   └───────────────┬───────────────┘
                                                   │ HTTP / JSON REST (JWT Bearer)
                          ┌────────────────────────┼─────────────────────────┐
                          │                                                  │
                          ▼                                                  ▼
             ┌──────────────────────────┐                       ┌────────────────────────────────┐
             │      IVR-AI-engine       │                       │      IVR-payment-service       │
             │  Tomcat 10 · Port 8081   │                       │    Tomcat 10 · Port 8082       │
             │  Auth, 7-pass AI engine, │                       │  Paymob checkout, HMAC         │
             │  VXML compile/parse,     │                       │  verification, subscription    │
             │  RAG, analytics, admin   │                       │  renewal scheduler             │
             └────────────┬─────────────┘                       └───────────────┬────────────────┘
                          │  Shared volume:                                     │
                          │  IVR-engine/scenarios/*.vxml                        │
                          │  + shell call to add_extension.sh                   │
                          ▼                                                     ▼
             ┌──────────────────────────┐                        ┌──────────────────────────────┐
             │        IVR-engine        │                        │        PostgreSQL            │
             │  FastAGI · Port 4573     │◄───────────────────────┤   (NeonDB + pgvector 0.5+)   │
             │  JVoiceXML runtime       │   JDBC (SSL) — all 3   │  tenants, users, flows, RAG  │
             │  VXML scenario execution │   Java services write  │  embeddings, call logs, CDR, │
             └────────────┬─────────────┘   here                │  queues, SIP ext., payments   │
                          │ FastAGI protocol (TCP 4573)          └──────────────────────────────┘
                          ▼
             ┌───────────────────────────┐
             │        Asterisk PBX       │
             │  Dialplan, SIP/RTP,       │
             │  sound storage, CDR CSV   │
             └───────────────────────────┘

             ┌───────────────────────────┐
             │     IVR-rag-service       │  ◄── queried by IVR-AI-engine (RagClient) over HTTP
             │  Python · ChromaDB        │
             │  Document ingestion +     │
             │  semantic retrieval       │
             └───────────────────────────┘
```

**Inter-service communication** (as configured in `docker-compose.yml` and referenced in code):

| Source | Target | Protocol | Port | Purpose |
|---|---|---|---|---|
| `IVR-webapp` | `IVR-AI-engine` | HTTP/JSON | 8081 | Auth, AI generation, flow management, RAG, analytics |
| `IVR-webapp` | `IVR-payment-service` | HTTP/JSON | 8082 | Checkout, billing status, plan retrieval |
| `IVR-AI-engine` | PostgreSQL | JDBC (SSL) | 5432 | Tenants, users, flows, embeddings, logs |
| `IVR-payment-service` | PostgreSQL | JDBC (SSL) | 5432 | Plans, transactions, tenant subscription state |
| `IVR-engine` | PostgreSQL | JDBC (SSL) | 5432 | Call analytics |
| `IVR-AI-engine` | OpenRouter / Gemini / Groq / Ollama | HTTPS/REST | remote / 11434 | AI generation, embeddings |
| `IVR-AI-engine` | `IVR-rag-service` | HTTP | 8085 | RAG document retrieval |
| `IVR-payment-service` | Paymob API | HTTPS/REST | — | Auth token, order, payment key |
| Asterisk PBX | `IVR-engine` | FastAGI | 4573 | Executes VXML scenarios per call |
| `IVR-AI-engine` | Asterisk | AMI socket / shell script | — | Health probing, `add_extension.sh` |
| `IVR-AI-engine` | `IVR-engine` | Shared filesystem volume | — | Publishing `.vxml` scenario files |

---

## 4. 🧩 Component Breakdown

### `IVR-AI-engine` — the AI/API backend
- **Stack**: Java 21, Jakarta Servlet 6.0, embedded Tomcat 10, raw JDBC.
- **Entry point**: `com.nexusivr.ai` package, deployed via `AppServletContextListener`.
- **Responsibilities**: authentication/JWT, the 7-pass AI flow pipeline, VXML parsing/export, flow validation and auto-repair, Asterisk dialplan publishing, RAG orchestration, telephony resource CRUD (SIP extensions, queues, phone numbers), analytics, audit logs, notifications, system health.
- ~30 REST servlets under `controller/`, backed by a DAO layer under `dao/` and a large service layer under `service/` and `ai/`.

### `IVR-engine` — the FastAGI telephony runtime
- **Stack**: Java 21, Asterisk-Java 3.30.0, a JVoiceXML-inspired custom platform (`gov.iti.telecom.platform`).
- **Entry point**: `FastAgiServerMain`, listening on TCP `4573`.
- **Responsibilities**: loads `.vxml` scenarios from `scenarios/`, executes the dialogue state machine against a live Asterisk channel (`VxmlAgiHandler`, `VxmlScenarioEngine`), synthesizes speech (`TtsEngine`), handles an embedded conversational AI loop via `OllamaAgent` for `<ai>` tags, and logs CDR/event data.

### `IVR-payment-service` — billing microservice
- **Stack**: Java 21, Jakarta Servlet 6.0, embedded Tomcat 10.
- **Responsibilities**: subscription plan catalog, Paymob checkout handshake (token → order → payment key), SHA-512 HMAC webhook verification, subscription activation, and a 24-hour expiry scheduler (`SubscriptionScheduler`).

### `IVR-webapp` — the frontend
- **Stack**: React 19, TypeScript 5, Vite, Tailwind CSS utilities + a custom CSS design system.
- **Responsibilities**: the visual IVR builder canvas, the AI Assistant chat panel, tenant and Super Admin dashboards, telephony resource screens (SIP extensions, queues, voice prompts, phone numbers), billing screens, analytics, audit logs, and system health.

### `IVR-AI-engine/rag` — RAG microservice
- **Stack**: Python, ChromaDB (a persisted `chroma_db/` store is included in the repo).
- **Responsibilities**: document ingestion (`ingest.py`), retrieval (`retriever.py`), a small HTTP server (`server.py`) queried by `IVR-AI-engine`'s `RagClient`.

### `Database/`
- Numbered PostgreSQL migration scripts (`000`–`015`) for the AI engine schema, plus a separate `Payment-database/001_payment_and_subscriptions.sql` for billing tables. Requires the `pgcrypto` and `pgvector` (0.5+) extensions.

---

## 5. 🤖 The AI Flow Generation Pipeline

The core generation flow is orchestrated by **`UnifiedAiEngine`**, which drives **`DomainFlowGenerator`** through 7 passes:

```
Natural-language prompt
        │
        ▼
Pass 1 — Intention & Domain Analysis     (DomainDetector identifies e.g. Banking, Healthcare, Hospitality)
        │
        ▼
Pass 2 — Flow Architecture Planning      (high-level node topology / state tree)
        │
        ▼
Pass 3 — Node & Edge Synthesis           (menu choices, inputs, transfers, webhooks, conditions)
        │
        ▼
Pass 4 — Voice Prompt Writing            (natural EN/AR prompts per node, VoicePromptWriterAgent)
        │
        ▼
Pass 5 — Actions & Integrations Binding  (API webhooks, queue transfers, voicemail handlers)
        │
        ▼
Pass 6 — Structural & Business Validation (acyclicity, unreachable nodes, missing prompts/choices)
        │
        ▼
Pass 7 — Polish & Auto-Repair            (ModelAutoRepair fixes structural defects, serializes FlowModel)
        │
        ▼
FlowModel JSON  →  sent to IVR-webapp for canvas rendering
```

Underlying this is a second internal pipeline used specifically for raw LLM VXML generation and normalization, implemented in `UnifiedAiEngine`:

1. **Prompt refinement** — `PromptRefinerService` expands a short prompt into a structured, multi-section specification.
2. **LLM generation** — the refined spec is sent to the active provider to produce VoiceXML.
3. **Response normalization** — `LlmResponseNormalizer` strips markdown fences, BOM artifacts, and verifies the XML envelope.
4. **DOM parsing** — `VxmlToModelConverter` turns the VXML into a `FlowModel` object graph.
5. **Validation** — `ModelFlowValidator` checks the graph.
6. **Auto-repair** — `ModelAutoRepair` fixes structural issues.
7. **Export** — `ModelToVxmlExporter` serializes the final, Asterisk-runnable VXML.

---

## 6. 🔌 LLM Provider Architecture

Provider selection and failover is handled by **`ProviderManager`** combined with **`CircuitBreaker`**:

| Order | Provider | Model (default) | Client class |
|---|---|---|---|
| Default | Gemini | `gemini-2.0-flash` | `GeminiClient` |
| Fallback 1 | Groq | `llama-3.3-70b-versatile` | `GroqClient` |
| Fallback 2 | OpenRouter | `openai/gpt-oss-20b` (configurable) | `OpenAiCompatibleClient` |
| Fallback 3 | Ollama (local) | `granite4.1:8b` | `OllamaClient` |
| Ultimate fallback | Local template generator | — | `TemplateGenerator` |

- The active provider is selected via the `AI_PROVIDER` environment variable and can be switched at runtime by a Super Admin (`AiProviderServlet`, `SuperAdminSettings.tsx`).
- `CircuitBreaker` tracks consecutive failures per provider; on repeated `429`/`5xx`/timeout responses it opens the circuit and routes around that provider for a cooldown window.
- A **mock provider** (`MockLlmClient`) exists in code (`ProviderManager`, `"mock" -> new MockLlmClient()`) and is used in the test suite, but it is not exposed in the frontend's provider-selection UI.
- `OpenAiCompatibleClient` contains a special-case mapping for an ITI student-portal OpenRouter proxy gateway (see [Known Limitations](#28-known-limitations--technical-debt)) — this is environment-specific and should be reviewed before using a different OpenRouter endpoint.

Environment variables (placeholders — see [Section 14](#14-environment-variables) for the full table):

```env
AI_PROVIDER=gemini
GEMINI_API_KEY=your_gemini_api_key_here
GROQ_API_KEY=your_groq_api_key_here
OPENROUTER_API_KEY=your_openrouter_api_key_here
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_MODEL=openai/gpt-oss-20b
OLLAMA_BASE_URL=http://localhost:11434
```

---

## 7. 📜 VoiceXML Pipeline

Two conversion directions are fully implemented:

**VXML → internal model** (`VxmlToModelConverter`):
- `<form>` / `<menu>` → flow nodes
- `<prompt>` / `<audio src="...">` → node text/audio output (`audio` text content is used as a TTS fallback)
- `<choice>` / `<goto>` → flow connections (edges)
- `<field>` + `<filled>` → input nodes and their fulfilled handlers
- `<if>` / `<elseif>` / `<else>` → conditional routing branches
- `<ai role="..." options="...">` (custom NexusIVR extension) → embedded conversational AI nodes

**Internal model → VXML** (`ModelToVxmlExporter`): serializes a validated `FlowModel` into a VoiceXML 2.1 document that `IVR-engine` can execute.

**Validation** exists in two independent implementations (documented as a known duplication — see [Section 28](#28-known-limitations--technical-debt)):
- `gov.iti.telecom.VxmlValidator` in `IVR-engine` (Java DOM-based)
- `com.nexusivr.ai.service.VxmlValidator` in `IVR-AI-engine` (XML SAX-based)

---

## 8. 🎨 Visual IVR Builder

The frontend canvas (`IVR-webapp/src/ivr/`) supports the following node types (`nodeConfig.ts`, `types.ts`):

| Category | Node types |
|---|---|
| Flow control | `start`, `end` |
| Audio output | `greeting`, `playback`, `tts`, `voicemail`, `record` |
| User input | `dtmf_menu`, `dtmf_input` |
| Call routing | `queue`, `transfer`, `extension` |
| Integrations | `api`, `database`, `webhook` |
| Control logic | `hours`, `holiday`, `condition`, `variable` |
| AI | `ai` (conversational voice agent node) |

**Canvas architecture:**

```
FlowCanvas.tsx  (drag-and-drop rendering)
      │
      ▼
graphEngine.ts  (node/edge state, topology, connection validation)
      │
      ├── flowParser.ts    — deserializes JSON or VXML into the visual graph
      └── vxmlExporter.ts  — compiles the visual graph into VoiceXML 2.1
```

**Save vs. Publish**:
- **Save** → `POST /ai/agent` (or the flow draft endpoint) writes a `draft` version snapshot.
- **Publish** → posts flow JSON + desired extension number + a sanitized filename slug; the backend validates, exports `.vxml`, writes it to `IVR-engine/scenarios/`, and calls `add_extension.sh`.
- **Import** accepts `.json`, `.vxml`, and `.xml` files; VXML imports are parsed server-side.
- **Export** downloads the compiled `[slug].vxml`.

**Client-side exporter limitations** — `vxmlExporter.ts` emits `TODO` placeholders for platform-specific logic that cannot be safely inferred client-side:
```xml
<if cond="true /* TODO: replace with platform hours check */">
<if cond="false /* TODO: replace with holiday calendar check */">
<assign name="var_my_variable" expr="'' /* TODO: set expression */"/>
```
These require manual completion (or server-side generation via the AI pipeline, which fills in real logic) before being production-safe.

---

## 9. ☎️ Asterisk + FastAGI Integration

```
SIP Client ──▶ Asterisk PBX ──▶ Dialplan Extension ──▶ AGI(agi://127.0.0.1:4573/default)
                                                              │
                                                              ▼
                                                     IVR-engine (VxmlAgiHandler)
                                                              │
                                          VxmlLoader loads scenarios/<name>.vxml
                                                              │
                                                              ▼
                                                     VxmlScenarioEngine executes
                                                     the VoiceXML dialogue against
                                                     the live Asterisk channel
```

- **`VxmlAgiHandler`** answers the channel, reads `agi_callerid`, `agi_uniqueid`, and the `VXML_FILE` channel variable, loads the matching scenario, and walks its `<form>`/`<menu>` structure — playing prompts, collecting DTMF via `getData`/`getOption`, executing `Dial` for transfers, and recording CDR data on hangup.
- **`add_extension.sh`** is invoked by `IVR-AI-engine`'s `FlowPublishService` on publish. It idempotently removes any existing dialplan block for the target extension, injects a fresh one into `/etc/asterisk/extensions.conf`, and runs `asterisk -rx "dialplan reload"`.

Example dialplan block generated for extension `1001` / scenario `banking_iv`:
```ini
exten => 1001,1,Answer()
exten => 1001,n,Set(VXML_FILE=banking_iv)
exten => 1001,n,AGI(agi://127.0.0.1:4573/default)
exten => 1001,n,Hangup()
```

### One-time host setup (non-Docker / bare-metal Asterisk)

To let the backend inject extensions and reload the dialplan without a password prompt on every publish, add a `sudoers` exception for `add_extension.sh`. Replace `<REPO_PATH>` with your actual clone path:

```bash
echo "$USER ALL=(ALL) NOPASSWD: /bin/bash <REPO_PATH>/IVR-engine/add_extension.sh *" | sudo tee /etc/sudoers.d/nexus_ivr
```
> The trailing `*` is required so the backend can pass the extension number and scenario name as arguments without triggering a password prompt.

Then allow group socket access to Asterisk's control interface:
```bash
sudo sed -i 's/^;astctlpermissions = 0660/astctlpermissions = 0660/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlowner = root/astctlowner = asterisk/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlgroup = apache/astctlgroup = asterisk/' /etc/asterisk/asterisk.conf
sudo chmod g+w /etc/asterisk/extensions.conf
sudo systemctl restart asterisk
```

---

## 10. 📁 Project Directory Structure

```
IVR-Platform/
├── Database/
│   ├── AI-database/                # SQL migrations 000–015 (tenants, AI sessions, RAG, telephony, audit)
│   └── Payment-database/           # Subscription & transaction schema
├── Documentation/                  # In-depth technical docs (architecture, DB, APIs, workflows, limitations)
├── IVR-AI-engine/                  # Core REST API backend (Java 21 / Maven / Tomcat 10)
│   ├── rag/                        # Python RAG microservice (ChromaDB)
│   └── src/main/java/com/nexusivr/ai/
│       ├── ai/                     # Providers, circuit breaker, agents, optimization
│       ├── config/                 # CORS, servlet context, AI config
│       ├── controller/             # ~30 REST servlets
│       ├── dao/                    # JDBC data access objects
│       ├── dto/                    # Request/response/patch DTOs
│       ├── model/                  # Domain entities + flow graph model
│       ├── security/               # JWT + password hashing
│       ├── service/                # 7-pass AI pipeline, VXML services, validation
│       └── util/                   # XML/sound directory helpers
├── IVR-engine/                     # FastAGI VoiceXML runtime (Java 21 / Asterisk-Java)
│   ├── scenarios/                  # Published .vxml files (and JSON siblings)
│   ├── draft/                      # Saved (unpublished) draft flow JSON
│   └── add_extension.sh            # Asterisk dialplan auto-provisioning script
├── IVR-payment-service/            # Paymob billing microservice (Java 21 / Maven / Tomcat 10)
├── IVR-webapp/                     # React + TypeScript + Vite frontend
│   └── src/
│       ├── api/                    # Axios clients (aiApi.ts, backendUrl.ts)
│       ├── components/             # Shared layout / UI components
│       ├── hooks/                  # useAIAssistant.ts, etc.
│       ├── ivr/                    # Canvas, graph engine, node config, VXML exporter
│       └── screens/                # Tenant + Super Admin pages
├── asterisk-sounds/                # Shared TTS/uploaded audio storage (Docker volume mount)
├── asterisk-cdr/                   # Asterisk CDR CSV output (Docker volume mount)
├── tools/                          # Developer utilities (agi_sim.py)
├── docker-compose.yml              # Full multi-container orchestration
├── .env.example                    # Root environment variable template
└── README.md
```

---

## 11. 🧰 Technology Stack

| Layer | Technology | Notes |
|---|---|---|
| Frontend | React 19, TypeScript 5, Vite, Tailwind CSS | `IVR-webapp` |
| Backend (API) | Java 21, Jakarta Servlet 6.0, embedded Tomcat 10 | `IVR-AI-engine`, `IVR-payment-service` |
| Telephony runtime | Java 21, Asterisk-Java 3.30.0 | `IVR-engine` |
| Voice flow format | VoiceXML 2.1 | Custom `<ai>` tag extension |
| Telephony/PBX | Asterisk | Runs on host network mode in Docker |
| Database | PostgreSQL 15+ with `pgvector` 0.5+ and `pgcrypto` | Hosted on NeonDB in the provided compose file |
| RAG store | ChromaDB (Python) | `IVR-AI-engine/rag` |
| Payments | Paymob (Egypt) REST API, SHA-512 HMAC | `IVR-payment-service` |
| Build tools | Maven (Java services), npm/pnpm (frontend) | `pnpm-lock.yaml` and `package-lock.json` both present |
| Containerization | Docker, Docker Compose | 6 services defined |

---

## 12. ✅ Prerequisites

### Required
- **Java 21+**
- **Maven** (`mvn`)
- **Node.js 18+** and npm (or pnpm — both lockfiles are present in `IVR-webapp`)
- **Asterisk PBX**, reachable at `127.0.0.1` for FastAGI
- **PostgreSQL 15+** with the `pgcrypto` and `pgvector` extensions available (the provided `docker-compose.yml` points at a NeonDB cloud instance by default)
- At least one configured LLM provider (Gemini, Groq, OpenRouter, or a local Ollama instance)

### Optional
- **Docker + Docker Compose**, for the full containerized stack (`docker-compose.yml` orchestrates 6 services)
- A SIP softphone (e.g. Zoiper, MicroSIP) registered to your Asterisk server, for testing live calls
- **Ollama**, if running the local LLM fallback provider
- A **Paymob** merchant account, if testing the billing/subscription flow

---

## 13. ⚙️ Installation & Configuration

```bash
# 1. Clone the repository
git clone https://github.com/mohesham59/IVR-Platform.git
cd IVR-Platform

# 2. Copy and fill in environment configuration
cp .env.example .env
# then edit .env with real database and LLM provider credentials
```

Each Java service also expects its own `.env` (referenced via `env_file` in `docker-compose.yml`):
```bash
cp IVR-AI-engine/.env.example IVR-AI-engine/.env        # if present, otherwise create manually
cp IVR-payment-service/.env.example IVR-payment-service/.env
```
> The repository ships a root-level `.env.example`; `IVR-AI-engine` and `IVR-payment-service` read variables from their own local `.env` files at runtime (see `docker-compose.yml`'s `env_file` directives). Populate both from the variable table in [Section 14](#14-environment-variables).

### Database setup
Apply the SQL migrations in order against a PostgreSQL 15+ database with `pgcrypto` and `pgvector` enabled:
```bash
psql "$DATABASE_URL" -f Database/AI-database/000_full_mvp_schema_combined.sql
# or apply 001 through 015 individually if you are not using the combined script
psql "$DATABASE_URL" -f Database/Payment-database/001_payment_and_subscriptions.sql
```

### Asterisk & permissions
See [Section 9](#9-asterisk--fastagi-integration) for the one-time `sudoers` and `asterisk.conf` setup required for non-Docker deployments. The Docker Compose setup mounts `/etc/asterisk` directly and does not require this step on the host, but the container itself still needs write access to `extensions.conf`.

### Frontend dependencies
```bash
cd IVR-webapp
npm install   # or: pnpm install
```

---

## 14. 🔐 Environment Variables

Values below are illustrative placeholders — never commit real secrets.

### Database
| Variable | Required | Description | Example |
|---|---|---|---|
| `DATABASE_URL` / `DB_URL` | Yes | JDBC connection string to PostgreSQL | `jdbc:postgresql://<host>/<db>?sslmode=require` |
| `DATABASE_USER` / `DB_USER` | Yes | Database username | `neondb_owner` |
| `DATABASE_PASSWORD` / `DB_PASSWORD` | Yes | Database password | `your_password_here` |

### AI Providers
| Variable | Required | Description | Example |
|---|---|---|---|
| `AI_PROVIDER` | Yes | Active provider key | `gemini`, `groq`, `openrouter`, `ollama` |
| `GEMINI_API_KEY` | If using Gemini | Google Gemini API key | `your_gemini_api_key_here` |
| `GROQ_API_KEY` | If using Groq | Groq Cloud API key | `your_groq_api_key_here` |
| `OPENROUTER_API_KEY` | If using OpenRouter | OpenRouter API key | `your_openrouter_key_here` |
| `OPENROUTER_BASE_URL` | If using OpenRouter | API base URL | `https://openrouter.ai/api/v1` |
| `OPENROUTER_MODEL` | If using OpenRouter | Model identifier | `openai/gpt-oss-20b` |
| `OLLAMA_BASE_URL` | If using Ollama | Local Ollama endpoint | `http://localhost:11434` |
| `OLLAMA_MODEL` | Optional | Model used by `IVR-engine`'s fallback voice agent | `granite4.1:8b` |

### Asterisk / Telephony
| Variable | Required | Description | Example |
|---|---|---|---|
| `AMI_HOST` | Yes (AI engine) | Asterisk Manager Interface host | `host.docker.internal` |

### Payments (Paymob — Egypt/EGP)
| Variable | Required | Description | Example |
|---|---|---|---|
| `PAYMENT_SERVICE_PORT` | Yes | Port for `IVR-payment-service` | `8082` |
| `PAYMOB_API_KEY` | Yes | Paymob API key | `your_paymob_api_key_here` |
| `PAYMOB_SECRET_KEY` | Yes | Paymob secret key | `your_paymob_secret_key_here` |
| `PAYMOB_PUBLIC_KEY` | Yes | Paymob public key | `your_paymob_public_key_here` |
| `PAYMOB_HMAC_SECRET` | Yes | Secret for SHA-512 callback verification | `your_hmac_secret_here` |
| `PAYMOB_INTEGRATION_ID_CARD` | Yes | Card payment integration ID | `5834828` |
| `PAYMOB_INTEGRATION_ID_WALLET` | Marked "needs clarification" in repo docs | Wallet integration ID | `5834829` |
| `PAYMOB_MOTO_INTEGRATION_ID` | Marked "needs clarification" in repo docs | MOTO integration ID | `5834830` |
| `PAYMOB_IFRAME_ID` | Yes | Hosted checkout iframe ID | `1067447` |

### RAG
| Variable | Required | Description | Example |
|---|---|---|---|
| `RAG_SERVICE_URL` | Yes (if using RAG) | URL of the RAG microservice | `http://ivr-rag-service:8085/query` |

> **Security note:** the `docker-compose.yml` checked into this repository contains a hardcoded NeonDB connection string, username, and password as default values for several services. Treat these as compromised, rotate them, and move all credentials into `.env` files (which are already git-ignored) before any real deployment.

---

## 15. ▶️ Running the Project

### Option A — Docker Compose (recommended, full stack)
```bash
docker compose up --build
```
This starts, in dependency order: `asterisk` → `ivr-engine` → `ivr-rag-service` → `ivr-ai-engine` (waits on RAG health) → `ivr-payment-service` → `ivr-webapp` (waits on both APIs' health checks).

| Service | Port | URL |
|---|---|---|
| `ivr-webapp` | 3000 (host) → 80 (container, Nginx) | `http://localhost:3000` |
| `ivr-ai-engine` | 8081 | `http://localhost:8081` |
| `ivr-payment-service` | 8082 | `http://localhost:8082` |
| `ivr-rag-service` | 8085 | `http://localhost:8085` |
| `ivr-engine` (FastAGI) | 4573 (TCP, host network) | `agi://127.0.0.1:4573` |
| Asterisk | SIP 5060/5061, RTP 10000–20000 (host network) | — |

### Option B — Manual / development mode (three terminals)

**Terminal 1 — FastAGI IVR Engine**
```bash
cd IVR-engine
mvn clean compile
mvn exec:java -Dexec.mainClass="org.asteriskjava.fastagi.DefaultAgiServer"
```
Expected output: `Listening on *:4573` and `Thread pool started.`

**Terminal 2 — AI Backend**
```bash
cd IVR-AI-engine
mvn clean package cargo:run -Dmaven.test.skip=true
```
Starts Tomcat on `http://localhost:8081`.

**Terminal 3 — Payment Service** *(optional, only needed for billing screens)*
```bash
cd IVR-payment-service
mvn clean package cargo:run -Dmaven.test.skip=true
```
Starts Tomcat on `http://localhost:8082`.

**Terminal 4 — Frontend**
```bash
cd IVR-webapp
npm install
npm run dev
```
Open `http://localhost:5173`.

> A production build/deploy path via Docker exists (see `Dockerfile` in each module and `nginx.conf` for the frontend). There is no separate, distinct "production mode" documented beyond the Docker Compose setup — treat Docker Compose as the closest thing to a production configuration currently defined in the repo.

---

## 16. 🔄 End-to-End Workflow Example

**Scenario: "Create a banking IVR"**

1. Start the platform (Docker Compose or the three manual terminals) and open `IVR-webapp`.
2. Log in (`/login` → `POST /api/auth/login`); the JWT is stored and attached to subsequent requests.
3. Open the IVR Builder screen and enter a prompt, e.g. *"Build a banking IVR with balance inquiry, card blocking, and a transfer to a human agent."*
4. The frontend calls the AI agent endpoint; `UnifiedAiEngine` runs the 7-pass pipeline and streams back a `FlowModel` (nodes + edges), rendered on the canvas via `GenerationStepper`.
5. The user edits nodes/prompts directly on the canvas as needed.
6. Clicking **Save** persists a draft snapshot; clicking **Publish** triggers validation, VXML export, and `FlowPublishService`.
7. `FlowPublishService` writes `IVR-engine/scenarios/{tenant_id}_{flow_name}.vxml` and calls `add_extension.sh <extension> <scenario_name>`, which updates `/etc/asterisk/extensions.conf` and reloads the dialplan.
8. A SIP client dials the published extension; Asterisk routes the call through `AGI(agi://127.0.0.1:4573/default)`.
9. `VxmlAgiHandler` loads the scenario and executes the VoiceXML dialogue against the live channel — playing prompts, collecting DTMF, and transferring to a queue/extension as configured.
10. On hangup, call data is written to `call_logs` / `call_events`, visible in the `CallAnalytics.tsx` screen.

---

## 17. 📞 Manual VXML Workflow

If you don't want to use the AI Builder, a scenario can be added directly:

1. Place a `.vxml` file (e.g. `my-scenario.vxml`) inside `IVR-engine/scenarios/`.
2. Register the extension:
   ```bash
   cd IVR-engine
   sudo /bin/bash add_extension.sh 700 my-scenario
   ```
3. Dial `700` from a registered SIP softphone.

Minimal valid VXML example:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<vxml version="2.1">
  <form id="welcome">
    <block>
      <prompt>Welcome to NexusIVR. Goodbye.</prompt>
      <exit/>
    </block>
  </form>
</vxml>
```

---

## 18. 🎧 Custom Voice Recordings

The platform supports the standard VXML `<audio>` tag to replace TTS-generated prompts with pre-recorded voice files.

**Storage path** (shared across Asterisk and `IVR-engine` via a Docker volume mount):
```
/var/lib/asterisk/sounds/ivr-custom/
```
(Locally, `./asterisk-sounds` is mounted to this path by `docker-compose.yml`.)

Files uploaded through the web interface (`VoicePromptsUploadServlet`, `VoicePromptsGenerateServlet`) are written here and tracked in the `voice_prompts` database table.

**Usage** — the text inside `<audio>` acts as a TTS fallback if the file is missing:
```xml
<prompt>
  <audio src="welcome_en.wav">Welcome to our service. Press 1 for English.</audio>
</prompt>
```

---

## 19. 📡 REST API Reference

Base URLs: `IVR-AI-engine` → `http://localhost:8081`, `IVR-payment-service` → `http://localhost:8082`.

### Authentication & health
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Returns `{ token, user }` |
| GET | `/health` | Public | Liveness check (both services) |
| GET | `/api/system-health` | Super Admin | DB, Asterisk AMI, AI provider, JVM memory |

### AI / Flow builder
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/ai/agent` | Tenant Admin | Generate a flow (default action) |
| POST | `/ai/agent?action=refine_prompt` | Tenant Admin | Refine a raw prompt |
| POST | `/ai/agent?action=validate` | Tenant Admin | Validate a flow model |
| POST | `/ai/agent?action=improve` | Tenant Admin | Apply an improvement instruction |
| DELETE | `/ai/agent?sessionId=...` | Tenant Admin | Cancel an in-progress generation |
| POST | `/ai/chat` | Tenant Admin | AI Assistant chat turn (RAG-augmented) |
| GET | `/ai/chat/history?sessionId=...` | Tenant Admin | Chat history |
| POST | `/ai/summarize` | Tenant Admin | Summarize a conversation |
| POST | `/ai/function-call` | Tenant Admin | Invoke a registered AI tool/function |
| GET / POST | `/ai/providers` | Super Admin | List / switch active LLM provider |

### Telephony resources
| Method | Path | Description |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/telephony/phone-numbers` | DID management *(UI screen currently uses static/mock state — see [Limitations](#28-known-limitations--technical-debt))* |
| GET/POST/DELETE | `/api/telephony/queues` | ACD queue management |
| GET/POST | `/api/telephony/sip-extensions` | PJSIP extension management |
| GET | `/api/voice-prompts` | List voice prompts |
| POST | `/api/voice-prompts/generate` | Synthesize a TTS prompt |
| GET | `/api/voice-prompts/stream` | Stream a prompt's audio |

### Analytics & admin
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/dashboard` | Tenant Admin | Tenant KPI dashboard |
| GET | `/api/cdr` | Tenant Admin | Parsed Asterisk CDR data |
| GET/POST | `/api/notifications` | Tenant/Super | In-app notifications |
| GET | `/api/audit-logs` | Super Admin | Paginated audit trail |
| GET | `/api/super-admin/dashboard` | Super Admin | Platform-wide metrics |
| GET/POST | `/api/super-admin/companies` | Super Admin | Tenant management, plan override |
| GET/POST | `/api/super-admin/users` | Super Admin | User management |
| GET | `/api/super-admin/reports` | Super Admin | Platform reports (+ CSV export) |

### Payments (`IVR-payment-service`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/payment/plans` | Tenant Admin | List subscription plans |
| GET | `/api/payment/billing-status` | Tenant Admin | Current subscription state |
| POST | `/api/payment/initiate` | Tenant Admin | Start Paymob checkout, returns iframe URL |
| POST / GET | `/api/payment/callback` | Paymob webhook / browser | HMAC-verified payment confirmation |
| GET | `/api/payment/verify` | Tenant Admin | Manually verify a transaction |

> The full endpoint list — including request/response shapes — is maintained in [`Documentation/08_Complete_REST_API_Endpoint_Reference.md`](Documentation/08_Complete_REST_API_Endpoint_Reference.md).

---

## 20. 🗄️ Database

- **Engine**: PostgreSQL 15+, with `pgcrypto` (`gen_random_uuid()`) and `pgvector` 0.5+ (1536-dimensional HNSW cosine-similarity vector search).
- **Access pattern**: raw JDBC via `DatabaseManager`, with explicit `PreparedStatement` binding in every DAO — no ORM.
- **Multi-tenancy**: every tenant-scoped table carries a `tenant_id UUID` column, and every DAO query filters on it, sourced from the verified JWT rather than client-supplied parameters.

### Key tables (21 total across both migration sets)
`tenants`, `users`, `flows`, `ai_sessions`, `ai_messages`, `conversation_history`, `knowledge_documents`, `knowledge_chunks`, `embeddings`, `prompt_templates`, `call_logs`, `call_events`, `phone_numbers`, `sip_extensions`, `queues`, `queue_members`, `agent_states`, `voice_prompts`, `audit_logs`, `notifications`, `subscription_plans`, `transactions`.

Full column-level specifications, constraints, and indexes for every table are documented in [`Documentation/02_Database_Schema_and_Migrations.md`](Documentation/02_Database_Schema_and_Migrations.md).

### High-level relationships

```
tenants 1───* users
tenants 1───* flows
tenants 1───* ai_sessions 1───* ai_messages
tenants 1───* knowledge_documents 1───* knowledge_chunks 1───1 embeddings
tenants 1───* call_logs 1───* call_events
tenants 1───* phone_numbers
tenants 1───* sip_extensions
tenants 1───* queues 1───* queue_members ──* users (agents)
tenants 1───* subscription_plans (via tenants.subscription_plan_id)
tenants 1───* transactions
tenants 1───* audit_logs
```

---

## 21. 🛡️ Security

**Implemented:**
- JWT-based authentication (`JwtUtil`), with `tenantId` and `isSuperadmin` encoded as claims and verified server-side on every request via `BaseAiServlet`.
- SHA-256 password hashing for new user registrations (`PasswordUtil`).
- Tenant isolation enforced at the SQL query level (never trusts a client-supplied tenant ID).
- SHA-512 HMAC verification of Paymob webhook callbacks (`HmacVerifier`), computed over a fixed, documented field sequence.
- CORS handling centralized in `CorsFilter` / `BaseAiServlet`.

**Known gaps / needs review before production:**
- The seed migration (`009_users_and_tenants.sql`) inserts default `admin`/`user` accounts with **plaintext** passwords; `UserDao` falls back to plaintext comparison for these legacy seeded accounts specifically, while new registrations are hashed. Rotate or remove these seed accounts before any real deployment.
- `docker-compose.yml` contains a hardcoded database connection string, username, and password used as default values for multiple services — these must be moved to secrets/`.env` and rotated.
- Two Paymob integration IDs (`PAYMOB_INTEGRATION_ID_WALLET`, `PAYMOB_MOTO_INTEGRATION_ID`) are explicitly flagged in the repo's own documentation as unverified placeholders.
- No rate limiting, WAF, or request-throttling layer is present in the codebase reviewed.

This section describes what exists in the code, not a security audit or a certification of production-readiness.

---

## 22. 🏢 Multi-Tenancy

- A `tenants` table represents each isolated SaaS customer; a `users` table links accounts to an `active_tenant_id` (or `NULL` for platform Super Admins).
- Role model: **Super Admin** (`is_superadmin = true`, tenant-agnostic, full platform access) vs. **Tenant Admin/User** (scoped to their `active_tenant_id`).
- Every DAO method that reads or writes tenant-owned data takes/filters on `tenant_id`, extracted server-side from the verified JWT (`BaseAiServlet.extractTenantId`) — never from request parameters.
- Super Admin capabilities: create/suspend tenants, suspend users, override a tenant's subscription plan (writes an audit log entry and a notification), view platform-wide reports and system health.

---

## 23. 🩹 Validation & Auto-Repair

`ModelFlowValidator` / `FlowModelValidator` check a generated or edited flow graph for:
- Orphan nodes (no incoming connection)
- Missing `End`/hangup termination nodes
- Colliding or invalid DTMF digits within a menu
- Structural cycles
- Duplicate dialplan extensions

`ModelAutoRepair` / `FlowModelAutoRepair` then attempt automatic fixes:
- Reconnects orphan nodes to an appropriate parent option or an End node
- Inserts a missing hangup/End node
- Remaps colliding DTMF digits
- Auto-increments duplicate phone-line/extension conflicts
- Repairs structural cycles (see `CycleRepairTest`, `DisconnectedFallbackMenuRepairTest` in the test suite)

Validation runs both automatically as part of the 7-pass generation pipeline (Pass 6/7) and on demand via `POST /ai/agent?action=validate`.

---

## 24. 🧪 Testing

| Module | Test files | Framework |
|---|---|---|
| `IVR-AI-engine` | 98 Java test classes (`ai/`, `controller/`, `dao/`, `service/`) | JUnit (via Maven Surefire) |
| `IVR-engine` | 2 Java test classes | JUnit |
| `IVR-payment-service` | 5 Java test classes | JUnit |
| `IVR-webapp` | 2 TypeScript test files (hooks, graph engine) | Vitest-style `*.test.ts` |

Run the backend test suites:
```bash
cd IVR-AI-engine && mvn test
cd IVR-engine && mvn test
cd IVR-payment-service && mvn test
```

Test coverage in `IVR-AI-engine` is notably broad, spanning the AI pipeline (`UnifiedAiEngineCompletenessTest`, `UnifiedAiEngineTwoPassTest`), VXML round-tripping (`VxmlFullRoundTripTest`, `VxmlRoundTripFidelityTest`), auto-repair (`FlowModelAutoRepairTest`, `CycleRepairTest`), provider failover (`ProviderManagerTest`, `CircuitBreakerBackoffTest`), and multi-tenant isolation (`TenantIsolationTest`).

---

## 25. 🧯 Troubleshooting

**Port already in use (8081 / 8082 / 4573 / 5173)**
- *Cause*: a previous instance is still running.
- *Fix*: `lsof -i :<port>` (or `netstat -ano` on Windows) and kill the conflicting process, or change the port in `application.properties` / `vite.config.ts` / `docker-compose.yml`.

**Asterisk not reachable / FastAGI connection refused**
- *Cause*: Asterisk isn't running, or `IVR-engine` hasn't started before a call is placed.
- *Fix*: confirm Asterisk is up (`asterisk -rx "core show version"`), confirm `IVR-engine` logs `Listening on *:4573`.

**Dialplan not reloading after publish**
- *Cause*: missing `sudoers` exception, or the container/user lacks write access to `extensions.conf`.
- *Fix*: re-run the one-time setup in [Section 9](#9-asterisk--fastagi-integration); verify `/etc/asterisk/extensions.conf` is group-writable.

**LLM provider failures / all providers exhausted**
- *Cause*: missing or invalid API key, expired quota, or the OpenRouter proxy gateway mapping doesn't match your endpoint.
- *Fix*: check `AI_PROVIDER` and the corresponding `*_API_KEY`; inspect `ProviderManager`/`CircuitBreaker` logs; as a last resort the platform falls back to `TemplateGenerator` (non-AI templated output).

**Database connection failures**
- *Cause*: missing `pgvector`/`pgcrypto` extensions, wrong SSL mode, or expired NeonDB credentials.
- *Fix*: verify `DATABASE_URL` includes `sslmode=require`; confirm extensions are installed (`CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE EXTENSION IF NOT EXISTS vector;`).

**Audio file not found during a call**
- *Cause*: the `.wav` file wasn't written to (or isn't mounted at) `/var/lib/asterisk/sounds/ivr-custom/`.
- *Fix*: confirm the `asterisk-sounds` volume is mounted identically across the `asterisk` and `ivr-engine` containers; the VXML `<audio>` tag's text content will play as a TTS fallback in the meantime.

**Frontend can't reach the backend (CORS / network errors)**
- *Cause*: `backendUrl.ts` pointing at the wrong host/port, or `CorsFilter` misconfigured.
- *Fix*: confirm `IVR-AI-engine` is healthy at `:8081/health`; check the frontend's configured backend base URL.

**Invalid or dead-end IVR graph rejected on publish**
- *Cause*: the flow has orphan nodes, missing terminal nodes, or duplicate DTMF digits that auto-repair could not resolve.
- *Fix*: run `POST /ai/agent?action=validate` and address the returned `issues` list, or use the "AI Suggestions" panel in the builder to apply fixes.

---

## 26. 🚀 Deployment

- `docker-compose.yml` is the most complete deployment definition in the repository: 6 services (Asterisk, `ivr-engine`, `ivr-ai-engine`, `ivr-payment-service`, `ivr-webapp`, `ivr-rag-service`) with health checks and dependency ordering.
- Each Java service and the frontend ship their own `Dockerfile`; the frontend's `Dockerfile` builds a static Vite bundle served by Nginx (`nginx.conf`).
- Asterisk and `ivr-engine` use Docker's `host` network mode, which is required for SIP/RTP traffic and for the AGI socket to be reachable at `127.0.0.1:4573` — this **ties the current Compose setup to a single Linux host** and is not a distributed/multi-host deployment topology.
- There is no Kubernetes manifest, Helm chart, or cloud-specific IaC (Terraform/CloudFormation) in the repository. A managed PostgreSQL instance (NeonDB) is referenced by default, but self-hosting Postgres is equally supported by simply changing `DATABASE_URL`.
- No CI/CD pipeline configuration (e.g. GitHub Actions) was found in the repository at the time of writing.

**In short**: the project is deployable as a single-host Docker Compose stack today; multi-host / orchestrated production deployment would require additional infrastructure work not currently present in the repo.

---

## 27. 🧱 Extending the Platform

### Add a new LLM provider
1. Implement a new client under `IVR-AI-engine/src/main/java/com/nexusivr/ai/ai/` following the pattern of `GeminiClient`/`GroqClient`/`OllamaClient` (implement `LlmClient`).
2. Register it in `ProviderManager` / `LlmProviderFactory` alongside the existing fallback chain.
3. Add the new provider's environment variables to `.env.example` and to the frontend's provider list in `SuperAdminSettings.tsx` if it should be selectable.

### Add a new IVR node type
1. Define the node type in `IVR-webapp/src/ivr/types.ts` and `nodeConfig.ts`.
2. Handle its rendering in `FlowCanvas.tsx` and its VXML compilation in `vxmlExporter.ts`.
3. Mirror the corresponding parsing/export logic server-side in `VxmlToModelConverter.java` / `ModelToVxmlExporter.java` so round-tripping stays consistent.
4. Add a corresponding case to `ModelFlowValidator` / `ModelAutoRepair` if the node introduces new structural constraints.

### Add a new REST endpoint
1. Create a servlet under `controller/` extending `BaseAiServlet` (for auth/CORS/tenant scoping "for free").
2. Add the corresponding DAO method(s) under `dao/`, always filtering by the extracted `tenant_id` for tenant-scoped resources.
3. Add request/response DTOs under `dto/request` and `dto/response`.
4. Wire the frontend call in `src/api/aiApi.ts`.

### Extend the RAG system
- Ingestion logic lives in `IVR-AI-engine/rag/ingest.py`; retrieval logic in `retriever.py`; the HTTP surface is `server.py`.
- The Java side talks to it via `RagClient.java` and `KnowledgeService.java`.

---

## 28. ⚠️ Known Limitations & Technical Debt

Sourced directly from the repository's own `Documentation/10_Known_Limitations_Dummy_Data_and_Inconsistencies.md`, cross-checked against the code:

1. **`PhoneNumbers.tsx` uses partially mocked/static UI state** even though its backend (schema, DAO, servlet) is fully implemented — the frontend screen has not been fully wired to live data yet.
2. **Duplicate VXML validator implementations** exist in `IVR-engine` (DOM-based) and `IVR-AI-engine` (SAX-based); minor logic duplication, not a functional bug, but a maintenance concern.
3. **Seed accounts use plaintext passwords**; `UserDao` retains a plaintext-comparison fallback specifically for these legacy seeded rows.
4. **OpenRouter base URL is pre-configured for an ITI student-portal proxy gateway** in the example environment; `OpenAiCompatibleClient.java` contains gateway-specific model-name remapping that should be reviewed/removed for non-ITI deployments.
5. **Paymob wallet/MOTO integration IDs are placeholders**, explicitly marked "Needs Clarification" in the source documentation — only the card integration ID is described as verified.
6. **Live carrier DID purchasing is not implemented**; the `phone_numbers` schema supports Twilio/Vonage fields, but number creation is a database insert only — no outbound call to a carrier's provisioning API.
7. **Single-host topology**: Asterisk and `ivr-engine` rely on Docker host networking, which constrains the current Compose setup to one machine.
8. **No CI/CD, Kubernetes, or cloud IaC configuration** is present in the repository.
9. **Client-side VXML exporter emits `TODO` placeholders** for business-hours, holiday, and boolean-condition logic (see [Section 8](#8-visual-ivr-builder)) — these are not automatically resolved into working ECMAScript expressions on the client.
10. **Hardcoded database credentials in `docker-compose.yml`** as default values for several services (see [Security](#21-security)).

---

## 29. 🤝 Contributing

1. Fork the repository and create a feature branch (`git checkout -b feature/my-change`).
2. Make your changes, following the existing package/module conventions described in [Section 4](#4-component-breakdown).
3. Run the relevant test suite(s) before opening a PR (`mvn test` in each affected Java module; the frontend's `*.test.ts` files).
4. Keep commits focused and write descriptive commit messages.
5. Open a pull request describing the change, its motivation, and any manual testing performed (especially for anything touching Asterisk provisioning or payment callbacks).

---

## 30. 📄 License

No `LICENSE` file was found in the repository at the time of writing. Licensing terms should be added by the repository owner before external reuse or redistribution is assumed to be permitted.

---

## 🙏 Acknowledgments

Built on top of open-source foundations including **Asterisk**, **Asterisk-Java**, **React**, **Vite**, and **PostgreSQL**/**pgvector**. AI generation is powered by pluggable third-party LLM providers (Google Gemini, Groq, OpenRouter, and self-hosted Ollama models). Payment processing is provided by **Paymob**.
