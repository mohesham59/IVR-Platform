# NexusIVR Platform — Project Overview

This document provides a comprehensive, technically rigorous overview of the **NexusIVR Platform**. It details the architecture, module layout, database design, backend services, frontend builder canvas, Asterisk telephony integration, and the current verification status of the codebase.

---

## 1. Project Directory Mapping

The NexusIVR workspace is organized into three primary active modules and a database configuration layer:

| Module / Directory | Role & Technical Stack | Description |
| :--- | :--- | :--- |
| **`IVR-AI-engine`** | Java 21, Maven, Tomcat, Servlet API, JDBC, SLF4J | The AI backend orchestrator. It manages session tracking, LLM provider routing, prompt engineering, XML/JSON parsing, semantic validation, and local auto-repair logic. |
| **`IVR-webapp`** | TypeScript, React, Vite, Tailwind CSS, LocalStorage | The visual builder interface. It features a drag-and-drop workspace canvas, interactive node configurations, client-side VXML export, and an AI chat assistant. |
| **`IVR-engine`** | Java 21, Maven, Asterisk-Java, JVoiceXML bindings | The dynamic VoiceXML interpreter runtime. It exposes a FastAGI interface to Asterisk, handles call playback, parses DTMF inputs, executes AI dialogues, and registers Asterisk dialplan extensions. |
| **`Database`** | PostgreSQL 15+, `pgvector` 0.5+ | Holds schema migration scripts, global prompt templates, tenant isolation layers, and conversational/RAG vector search records. |

---

## 2. Backend Architecture (`IVR-AI-engine`)

The backend is built as a modular Maven package deployed onto a Tomcat Servlet container.

### A. Controller Layer (`com.nexusivr.ai.controller/`)

All HTTP endpoints are exposed via Tomcat Servlets inheriting from `BaseAiServlet`. They validate tenant headers, manage request payloads, and route traffic to service registries.

*   **`AiFlowServlet`** (`/api/v1/ai/flow/*`)
    Handles flow graph metadata operations:
    *   `/generate` (POST): Accepts prompt and generates a React Flow JSON flow draft (`GENERATE_FLOW` intent).
    *   `/improve` (POST): Takes existing flow configurations and improvement instructions to return an optimized model (`IMPROVE_FLOW` intent).
    *   `/validate` (POST): Validates flow structural integrity (`VALIDATE_FLOW` intent).
    *   `/suggestions` (POST): Returns a list of actionable corrections for structural errors (`FLOW_SUGGESTIONS` intent).
    *   `/publish` (POST): Serializes and writes scenario VoiceXML to the filesystem and registers Asterisk extensions.
    *   `/parse` (POST): Parses VoiceXML documents and converts them to React Flow JSON.
    *   `/draft` (POST): Saves standard draft schemas onto the disk.
    *   `/export` (POST): Exports React Flow JSON to VoiceXML directly.
    *   `/generation/{sessionId}/cancel` (POST): Triggers cancellation threads for a running generation process.
*   **`AiChatServlet`** (`/api/v1/ai/chat`)
    Manages user chat message loops. It supports synchronous JSON request-response handling (EventSource/SSE is not implemented).
*   **`AiAnalyticsServlet`** (`/api/v1/ai/analytics`)
    Exposes conversation logs and telemetry charts.
*   **`VoicePromptsGenerateServlet`** (`/api/v1/voice-prompts/generate`)
    Orchestrates text-to-speech synthesis (TTS) prompts using edge engines (e.g. Arabic translation + local TTS).
*   **`VoicePromptsUploadServlet`** (`/api/v1/voice-prompts/upload`)
    Handles manual upload of `.wav`/`.mp3` files to `/var/lib/asterisk/sounds/ivr-custom/`.
*   **`AiProviderListServlet`** (`/api/v1/ai/providers`) & **`AiProviderServlet`** (`/api/v1/ai/provider`)
    Lists available LLM engines, models, and temperature mappings.

### B. Core Services (`com.nexusivr.ai.service/`)

*   **`UnifiedAiEngine.java`**
    The central multi-pass flow coordinator. It structures generation into consecutive stages:
    1.  *Pass 1*: Prompt refinement (runs prompt through `PromptRefinerService` to yield structured specifications).
    2.  *Pass 2*: Call LLM generator to construct structured VoiceXML.
    3.  *Pass 3*: Normalize the response content (`LlmResponseNormalizer`).
    4.  *Pass 4*: DOM parse VoiceXML to intermediate `FlowModel` objects (`VxmlToModelConverter`).
    5.  *Pass 5*: Validate the structured graph (`ModelFlowValidator`).
    6.  *Pass 6*: Apply automatic repairs on structural errors (`ModelAutoRepair`).
    7.  *Pass 7*: Export the finalized graph into Asterisk-compliant VXML (`ModelToVxmlExporter`).
*   **`PromptRefinerService.java`**
    Implements stage 1 prompt refinement. Detects the business domain (e.g. hospitality, telecom) and expands short user descriptions into multi-page specifications mapping options and departments before LLM flow construction.
*   **`LlmResponseNormalizer.java`**
    Strips raw LLM markdown fences (e.g., ` ```xml `), trims invalid character headers (such as BOM indicators), and verifies basic XML envelope tags before parsing.
*   **`VxmlToModelConverter.java`**
    Parses dynamic VoiceXML documents using Java W3C DOM. It parses:
    *   `<form>` and `<menu>` elements into flow nodes.
    *   `<prompt>` and `<audio src="...">` into node text outputs.
    *   `<choice>` and `<goto>` elements into flow connections.
    *   `<field>` tags into inputs and handles `<filled>` handlers.
    *   `<if>`, `<elseif>`, and `<else>` tags into conditional routing branches.
    *   `<ai role="..." options="...">` tags into AI assistant conversational structures.
*   **`ModelToVxmlExporter.java`**
    Serializes a validated `FlowModel` into an Asterisk-runnable VoiceXML 2.1 document.
*   **`ModelFlowValidator.java` & `ModelAutoRepair.java`**
    The self-healing layer. Checks for:
    *   Orphan nodes (auto-reconnected to appropriate parent options or End nodes).
    *   Missing End/Hangup nodes (auto-inserts hangup forms).
    *   Invalid/colliding DTMF digits in Menus (remaps digits dynamically).
    *   Duplicate extensions (auto-increments phone lines).
*   **`VoiceGenerationService.java`**
    Manages TTS rendering. For Arabic speech synthesis, it executes specific phoneme mappings, saves the generated output to local directories, and stores a cache record in the PostgreSQL database.

### C. Data Access & Models (`com.nexusivr.ai.dao/` & `com.nexusivr.ai.model/`)

NexusIVR implements a pure JDBC-based DAO layer.
*   **`FlowDao.java`**: Maps to PostgreSQL `flows` table. It implements strict multi-tenancy by filtering all query operations with `tenant_id`.
*   **`AiSessionDao.java`**: Tracks session identifiers, customer numbers, channel metadata, and timestamps.
*   **`MessageDao.java`**: Saves individual message histories for RAG context extraction.
*   **`DatabaseManager.java`**: Manages the PostgreSQL connection pool.

### D. Provider Orchestration (`com.nexusivr.ai.ai/`)

*   **`ProviderManager.java`**
    Distributes LLM requests using a circuit breaker and multi-stage fallbacks.
    *   *Default*: Gemini (`gemini-2.0-flash`).
    *   *First Fallback*: Groq (`llama-3.3-70b-versatile`).
    *   *Second Fallback*: OpenRouter (`openai.gpt-oss-20b-1:0`).
    *   *Ultimate Fallback*: Local Template Generator.
*   **`CircuitBreaker.java`**
    Monitors consecutive failures on each provider. When failures threshold or quota limits (HTTP 429) are encountered, it shifts states (`CLOSED` → `OPEN`) and halts routing requests to that provider during a cooldown window.
*   **OpenRouter ITI Student Gateway Mappings**
    To avoid LLM gateway errors, `OpenAiCompatibleClient.java` intercepts OpenRouter calls. If it detects the ITI student portal proxy gateway URL (`apiaccess.iti.net.eg/api/v1/student/chat`), it automatically maps custom model definitions (like mapping `llama-3.3-70b-versatile` to `openai.gpt-oss-20b-1:0`), resolving previous 30-second timeouts.

---

## 3. Database Layer (PostgreSQL)

The NexusIVR schema relies on PostgreSQL 15+ combined with `pgvector` 0.5+.

### A. Key Database Tables

1.  **`flows`**
    Stores builder canvas designs.
    *   `id` UUID PRIMARY KEY, `tenant_id` UUID, `name` VARCHAR, `description` VARCHAR, `flow_json` JSONB (React Flow format), `status` VARCHAR (DRAFT/PUBLISHED), timestamps.
2.  **`ai_sessions`**
    Tracks conversational turns.
    *   `id` UUID PRIMARY KEY, `tenant_id` UUID, `channel` VARCHAR (VOICE/CHAT/etc), `external_reference_id` VARCHAR, `customer_identifier` VARCHAR, `status` VARCHAR, timestamps.
3.  **`ai_messages`**
    Saves RAG and conversation turns.
    *   `id` UUID, `session_id` UUID, `turn_number` INT, `role` VARCHAR (USER/ASSISTANT/SYSTEM), `content` TEXT, tokens used.
4.  **`embeddings`**
    Implements semantic vector storage.
    *   `id` UUID PRIMARY KEY, `chunk_id` UUID, `tenant_id` UUID, `embedding_model` VARCHAR, `embedding` VECTOR(1536).
    *   *Indexes*: Features `idx_embeddings_vector_hnsw` HNSW cosine similarity index for fast searches.
5.  **`voice_prompts`**
    Holds cache records for synthesized TTS files.
    *   `id` UUID, `name` VARCHAR, `language` VARCHAR, `duration` VARCHAR, `file_path` TEXT, created timestamps.

---

## 4. Asterisk Telephony Integration & VXML Runtime

The `IVR-engine` acts as a middle-tier bridging Asterisk calls to VoiceXML scenario scripts.

```
 Asterisk Call  ────>  add_extension.sh  ────>  agi://127.0.0.1:4573
                                                      │
                                                      ▼
 Asterisk-Java  <───────────────────────────  VxmlAgiHandler.java
 (DTMF & Prompt Play)                                 │
                                                      ▼
                                            VxmlScenarioEngine.java
                                            (VoiceXML interpreter)
```

### A. AGI Call Handling (`VxmlAgiHandler.java`)
1.  **Incoming Call**: Asterisk routes calls to `agi://127.0.0.1:4573/default`.
2.  **Scenario Loading**:
    *   Reads `VXML_FILE` Asterisk channel variable, or extracts it from the AGI path stem (e.g. `/restaurant` resolves to `restaurant.vxml`).
    *   Loads VXML files using `VxmlLoader` from `scenarios/`.
3.  **Execution Loop**:
    *   Uses DOM parsing to walk through form fields and menus.
    *   Plays prompts and audio recordings using `TtsEngine` playback bindings.
    *   Accepts DTMF digits using `channel.waitForDigit()`.
    *   *Dynamic Conversational AI*: For `<ai>` tags, it starts an interactive loop. Records user utterances, translates speech-to-text using Python `speech_recognition` (`/dev/shm/asr.py` Google Speech API wrapper), posts text to Ollama (`OllamaAgent`), and reads the reply back to the caller.
    *   *REST Integration*: For `<api>` tags, executing a GET request, extracts JSON values using a `jsonPath` attribute, and maps them to session variables.
4.  **Result Propagation**: Sets Asterisk channel variables (`VXML_SESSION_ID`, `VXML_STATE`, `VXML_RESULT_*`) so that the Asterisk dialplan can take action upon call completion.

### B. Provisioning and Permissions Setup
*   **`add_extension.sh`**
    Automates registration of new VXML flows in the Asterisk dialplan:
    *   Reads extension numbers and VXML scenario paths.
    *   Removes existing dialplan blocks for the target extension to guarantee idempotency.
    *   Modifies `/etc/asterisk/extensions.conf` to inject AGI routing lines:
        ```ini
        exten => 500,1,NoOp(Incoming call for VXML Scenario: my_scenario)
        exten => 500,n,Answer()
        exten => 500,n,Set(VXML_FILE=my_scenario)
        exten => 500,n,AGI(agi://127.0.0.1:4573/default)
        exten => 500,n,Hangup()
        ```
    *   Runs `asterisk -rx "dialplan reload"` to apply updates immediately.
*   **`setup_permissions.sh`**
    Deploys configurations for secure execution:
    *   Inserts passwordless sudoers permissions under `/etc/sudoers.d/nexus_ivr` for the script `add_extension.sh` (`${USER} ALL=(ALL) NOPASSWD: /bin/bash /path/to/add_extension.sh *`).
    *   Ensures that Asterisk control parameters (`astctlpermissions`, `astctlowner`, `astctlgroup`) in `/etc/asterisk/asterisk.conf` are configured correctly.
    *   Sets group-write permissions on `/etc/asterisk/extensions.conf` and restarts Asterisk.

---

## 5. Frontend Canvas & AI Chat Flow (`IVR-webapp`)

The builder interface is a React single-page webapp built on top of customized canvas structures.

### A. Supported Canvas Node Types
*   **Flow Controllers**: `start` (entry point), `end` (disconnect/hangup).
*   **Audio Output**: `greeting` (welcome recordings), `playback` (play files), `tts` (synthesize text), `voicemail` (record voicemail), `record` (record call).
*   **User Input**: `dtmf_menu` (branching dialpad), `dtmf_input` (numeric capture).
*   **Call Routing**: `queue` (ACD queue), `transfer` (live agent bridging), `extension` (SIP dial).
*   **Integrations**: `api` (REST request), `database` (database lookup), `webhook` (trigger webhook).
*   **Control Logic**: `hours` (business hours check), `holiday` (holiday calendar routing), `condition` (boolean branch expression), `variable` (variable setter).
*   **AI Assistants**: `ai` (conversational voice agent).

### B. Action Controls & Buttons
*   **Save Button**: Encodes nodes/edges, POSTs JSON payload to `/api/v1/ai/flow/draft`, pushes a `draft` version snapshot, and alerts on write confirmation.
*   **Publish Button**: Opens a publishing prompt. POSTs the flow JSON, desired phone extension, and sanitized file slug to `/api/v1/ai/flow/publish`. Creates a `published` version snapshot.
*   **Import Button**:
    *   File picker accepts: `.json`, `.vxml`, `.xml`.
    *   *VoiceXML Import*: If VXML is detected, it POSTs the text to the backend `/parse` parser. Maps nodes using grid coordinates, runs graph validation, sets state variables, and syncs flow context.
    *   *JSON Import*: Parses client-side using `JSON.parse`.
*   **Export Button**: Sends canvas JSON to `/api/v1/ai/flow/export` to retrieve VoiceXML code, then triggers a client-side download of `[slug].vxml`.

### C. Removed and Hidden Configuration Flags
*   **AI Suggestions Panel**: Still fully functional in the workspace under the "AI Suggestions" tab at the bottom canvas drawer, rendering `currentSuggestions` that can be individual or bulk-applied (such as reconnecting orphans and correcting ports).
*   **Enhance Prompt Toggle**: The hook state variable (`enhancePrompt` / `setEnhancePrompt`) and its API mapping are still active in the hook backend, but the toggle switch has been removed from the `AIAssistant.tsx` dashboard UI.
*   **Mock Provider**: Still defined in `ProviderManager.java` (`"mock" -> new MockLlmClient()`), but has been removed from the user settings options list. Only OpenRouter, Gemini, and Groq are accessible to front-end selections.

### D. Client-Side VXML Exporter (`vxmlExporter.ts` Limitations)
The web exporter converts canvas schemas into VoiceXML 2.1 blocks. For platform-independent routing nodes, it writes placeholders that require human intervention:
*   *Business Hours*: Generates a default ECMAScript conditional branch with placeholder text:
    ```xml
    <if cond="true /* TODO: replace with platform hours check */">
    ```
*   *Holiday Checks*:
    ```xml
    <if cond="false /* TODO: replace with holiday calendar check */">
    ```
*   *Boolean Conditions*:
    ```xml
    <if cond="true /* TODO: Replace cond attribute with ECMAScript expression */">
    ```
*   *Variable Setters*:
    ```xml
    <assign name="var_my_variable" expr="'' /* TODO: set expression */"/>
    ```

---

## 6. Recent Commit History

The latest 10 commits recorded in the repository reflect intense feature unification and bug correction:

1.  `docs: update README with permission script setup guide`
2.  `feat(ui): unify VXML export views, support generation cancel stop action, restyle deployment box, and link live canvas clipboard copy`
3.  `feat(api): expose generation cancellation, VXML export endpoints, and thread-local session mapping in servlets`
4.  `feat(flow): scope drafts and published scenarios to subdirectories, sanitise filenames, automate permissions script, and clean legacy files`
5.  `fix(vxml): resolve duplicate menu DTMF mapping and add integration tests for VXML export unification`
6.  `feat(ai): improve prompt refiner, flow generation pipeline, auto-repair, and English title generation`
7.  `fix(ai): resolve OpenRouter and Bedrock system message folding and cancellation handling`
8.  `chore: add draft/scenarios patterns to .gitignore to exclude manual test artifacts`
9.  `Reconstruct and sync implementation state, resolve all test failures including NPE, dead-end cycles, and metadata round-trips`
10. `Fix Arabic TTS generation, update servlet mapping, and add test flow`

---

## 7. Automated Test & Verification Status

Backend integrity is guaranteed by a comprehensive test suite.
*   **Command**: `mvn test` executed on the `IVR-AI-engine` root.
*   **Validation Result**: **SUCCESS**
*   **Test Count**: **391 tests run, 0 failures, 0 errors, 0 skipped**.
*   **Core Covered Modules**:
    *   Multi-pass Unified AI pipeline generation logic.
    *   Provider manager fallback routes and circuit breaker state switches.
    *   DOM XML parser and exporter compatibility tests.
    *   Multi-tenant isolation and JDBC schema queries.
    *   Auto-repair mechanisms, duplicate menu DTMF configurations, and orphan node connections.
