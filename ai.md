# NexusIVR AI Module — Technical Audit Report

**Date:** 2026-07-28  
**Scope:** AI backend module only (read-only assessment)  
**Working directory:** `/home/mohesham/Desktop/IVR-GP`

---

## 1. Current AI Architecture

### Request Entry Point

The AI module exposes three Jakarta Servlet endpoints:

| Endpoint | Servlet | Purpose |
|----------|---------|---------|
| `POST /api/v1/ai/chat` | `AiChatServlet` | Chat turns, session management |
| `POST /api/v1/ai/flow/generate` | `AiFlowServlet` | New IVR flow generation |
| `POST /api/v1/ai/flow/improve` | `AiFlowServlet` | Flow improvement |
| `POST /api/v1/ai/flow/validate` | `AiFlowServlet` | Flow validation |

All servlets extend `BaseAiServlet`, which intercepts every request in `service()` and:
1. Extracts `X-AI-Provider`, `X-AI-Model`, `X-AI-Temperature`, `X-AI-Timeout` headers
2. Stores them in a `ThreadLocal<RequestOverrides>` via `GlobalAiConfig.setOverrides()`
3. Calls `super.service()` to dispatch to `doPost()`
4. Clears overrides in `finally`

### Component Flow

```
Client Request
    │
    ▼
BaseAiServlet (header extraction, ThreadLocal overrides)
    │
    ▼
AiFlowServlet / AiChatServlet
    │
    ▼
UnifiedAiEngine (or ChatService for chat)
    │
    ├── PromptRefinerService (Pass 1 - optional)
    │       │
    │       ▼
    │   ProviderManager.executeWithRetryAndFallback()
    │       │
    │       ▼
    │   LlmClient (Groq/Gemini/Ollama/Mock)
    │       │
    │       ▼
    │   AiResponse
    │
    ├── PromptBuilder (system instruction assembly)
    │
    ├── DomainDetector (keyword-based domain detection)
    │
    ├── ProviderManager.executeWithRetryAndFallback() (Pass 2 - main generation)
    │       │
    │       ▼
    │   LlmClient → raw VoiceXML/JSON string
    │
    ├── LlmResponseNormalizer (cleanup)
    │
    ├── VxmlToModelConverter (VoiceXML → FlowModel)
    │
    ├── ModelFlowValidator (validate FlowModel)
    │       │
    │       ▼ (if invalid)
    │   ModelAutoRepair (local repair)
    │       │
    │       ▼
    │   ModelFlowValidator (re-validate)
    │
    ├── ModelToFlowRenderer (FlowModel → React Flow JSON)
    │
    └── Return Flow / ChatResponse / FlowImprovementResponse
```

### Actual vs Intended Architecture

| Intended Stage | Actual Implementation | Gap |
|----------------|----------------------|-----|
| LLM Refinement | `PromptRefinerService` calls LLM for Pass 1 | Implemented, but uses same provider chain as generation |
| Business Understanding | No separate reasoning stage | The "business understanding" is implicitly done by the LLM in Pass 1, but there's no explicit extracted business model |
| IVR Flow Design | No explicit design stage | The system goes directly from prompt to VoiceXML generation. There's no intermediate "plan" object that gets validated before rendering |
| Flow Generator Engine | `UnifiedAiEngine.generateFlow()` | Implemented |
| Validation | `ModelFlowValidator` | Implemented |
| Auto Repair | `ModelAutoRepair` | Implemented, but only runs locally after LLM output |

**Key finding:** The architecture does not have a separate "Business Understanding → IVR Design" stage that produces a validated intermediate plan. The two-pass system is:
- Pass 1: Prompt refinement (makes the prompt better)
- Pass 2: Direct VoiceXML generation

There is no intermediate structured plan (e.g., a graph plan with nodes, edges, ports) that gets validated before being rendered to VoiceXML.

---

## 2. Current Pipeline Status

### Prompt Refinement
- **Implemented:** Yes (`PromptRefinerService`)
- **Class:** `PromptRefinerService.java`
- **What it does:** Takes the raw user prompt and asks an LLM to produce a structured JSON specification with fields like `refined_prompt`, `business_domain`, `departments`, `menu_options`, `greeting`, `closing`, etc.
- **Current behavior:** 
  - Skipped if `PromptCompletenessChecker.isWellSpecified()` returns true
  - Cached in `RefinedSpecCache`
  - If it fails, falls back to raw prompt for Pass 2
  - Uses the same provider chain as Pass 2
  - Does NOT use the refined spec's domain or structure — it only produces a refined text prompt for Pass 2

### Business Understanding
- **Real reasoning stage?** No
- **Current behavior:** There is no explicit business understanding component. The `PromptRefinerService` produces a structured JSON, but this output is only used as a refined text prompt for Pass 2 — the structured fields (`departments`, `menu_options`, etc.) are not parsed or validated. The LLM in Pass 2 does all reasoning implicitly.

### IVR Design Stage
- **Does it create a business flow plan before generation?** No
- **Current behavior:** The system generates VoiceXML directly in Pass 2. There is no intermediate "plan" object. The `FlowModel` is created AFTER VoiceXML generation, via `VxmlToModelConverter`. This means the LLM has to produce valid VoiceXML in one shot, and only then is it parsed into a structured model.

### Flow Generation
- **How is the flow currently generated?**
  1. Pass 1 refines the prompt (optional)
  2. Pass 2 sends the prompt to LLM with `structuredOutput=true`
  3. LLM returns raw text (expected to be VoiceXML wrapped in JSON)
  4. `LlmResponseNormalizer.normalize()` cleans the output
  5. `VxmlToModelConverter` parses VoiceXML → `FlowModel`
  6. `ModelToFlowRenderer` renders `FlowModel` → React Flow JSON
- **Does it generate VXML first?** Yes, VoiceXML is the intermediate canonical format
- **Does it generate JSON directly?** No — JSON is rendered from the FlowModel after VXML parsing

### Validation
- **How does validation work?**
  - `ModelFlowValidator.validate(candidateModel)` validates the Internal Flow Model
  - Checks for structural issues: missing start/end nodes, disconnected nodes, invalid ports, etc.
- **What problems does it detect?**
  - Missing start node
  - Missing end node
  - Disconnected nodes
  - Invalid ports
  - Missing transitions
  - Unreachable nodes

### Auto Repair
- **Is it working?** Yes, but only locally
- **What cases does it repair?**
  - Missing Start node → `ensureStartNode()`
  - Missing End node → `ensureEndNode()`
  - Disconnected nodes → `reconnectOrphanNodes()`
  - Missing references → `fixMissingReferences()`
  - Invalid output ports → `remapInvalidPorts()`
  - Broken transitions → `repairBrokenTransitions()`
  - Unreachable branches → `repairUnreachableBranches()`
- **Limitation:** Repair only happens AFTER LLM output. If the LLM returns completely invalid output (e.g., malformed VXML that can't be parsed), the system throws `ProviderException` rather than trying a different generation approach or template fallback.

---

## 3. AI Provider Status

### Current Default Provider
- **Default:** `gemini` (from `application.properties` and `LlmConfig.getProvider()`)
- **Frontend selection:** Stored in `localStorage` as `ai_provider`, sent via `X-AI-Provider` header

### Available Providers
| Provider | Class | Status |
|----------|-------|--------|
| Groq | `GroqAiProvider` / `OpenAiCompatibleClient` | Active |
| Gemini | `GeminiClient` | Active |
| Ollama | `OpenAiCompatibleClient` (providerName="ollama") | Active but NOT in `PROVIDER_PRIORITY` |
| Mock | `MockLlmClient` | Test only |
| Template Generator | `TemplateGenerator` | Exists but NOT in fallback chain |

### Provider Switching Behavior

**Critical finding:** `PROVIDER_PRIORITY = ["groq", "gemini"]`. Ollama is registered in `PROVIDER_MODELS` but is NOT in `PROVIDER_PRIORITY`. This means:

1. If user selects Ollama explicitly, it gets prepended to the try list
2. If user does not select a provider, only groq → gemini are tried
3. TemplateGenerator is explicitly excluded from `PROVIDER_PRIORITY`

### Fallback Order

```
Explicit provider selection:
  Selected Provider → groq → gemini → ProviderException

No explicit selection:
  groq → gemini → ProviderException
```

**There is NO automatic fallback to TemplateGenerator.** When all providers fail, `ProviderManager.executeWithRetryAndFallback()` throws `ProviderException`. The `TemplateGenerator` exists but is unreachable in normal operation.

### Circuit Breaker Behavior

- **Class:** `CircuitBreaker.java`
- **States:** CLOSED → OPEN → HALF_OPEN → CLOSED
- **Transition to OPEN:**
  - 401/403 → permanent (600s cooldown)
  - 429 with "quota exceeded" → 300s cooldown
  - 5 consecutive failures → 30s cooldown
- **HALF_OPEN:** After cooldown, 1 probe allowed. 1 success → CLOSED. 1 failure → OPEN.
- **All thresholds are hardcoded** — no configuration via `LlmConfig` or environment variables.

### Log Analysis Explanation

Based on the provided logs:

```
1. Ollama selected → timeout after 30s
   - OllamaClient uses timeoutSeconds=60 by default, but the HTTP connect timeout is capped at 3s
   - The 30s timeout in the log suggests the request timeout was 30s, not 60s
   - Ollama returns mock response (isMock=true) with error content

2. Groq → HTTP 429 quota exceeded
   - Circuit breaker for groq transitions CLOSED → OPEN
   - 300s cooldown

3. Gemini → HTTP 429 quota exceeded  
   - Circuit breaker for gemini transitions CLOSED → OPEN
   - 300s cooldown

4. All providers exhausted → ProviderException thrown
   - ProviderManager throws with provider="all"
   - UnifiedAiEngine catches and re-throws
   - BaseAiServlet maps to HTTP 502 PROVIDER_ERROR
```

**Root cause:** The 30-second timeout for Ollama (despite `ollama.timeout=60` default) combined with quota exhaustion on both Groq and Gemini leads to total provider exhaustion. The circuit breakers then prevent any retry for 5 minutes.

---

## 4. Flow Generation Current Problems

### Missing Start Node
- **Where:** After `VxmlToModelConverter` parses VoiceXML into `FlowModel`
- **Component responsible:** `ModelFlowValidator` detects it, `ModelAutoRepair.ensureStartNode()` fixes it
- **Root cause:** LLM sometimes omits the Start node in VoiceXML

### Missing End Node
- **Where:** Same pipeline stage
- **Component responsible:** `ModelFlowValidator` detects it, `ModelAutoRepair.ensureEndNode()` fixes it
- **Root cause:** LLM sometimes omits the End node

### Invalid Ports
- **Where:** `VxmlToModelConverter` or `ModelFlowValidator`
- **Component responsible:** `ModelAutoRepair.remapInvalidPorts()` fixes it
- **Root cause:** LLM generates DTMF choices with ports that don't match the target node's expected input ports

### Disconnected Nodes
- **Where:** `ModelFlowValidator` detects orphan nodes
- **Component responsible:** `ModelAutoRepair.reconnectOrphanNodes()` fixes it
- **Root cause:** LLM creates nodes but doesn't connect them with `<goto>` elements

### Validation Failures Leading to 502
- **Where:** `UnifiedAiEngine.generateFlow()` lines 244-253
- **Component responsible:** `UnifiedAiEngine`
- **Root cause:** When `VxmlToModelConverter` returns null (can't parse VXML), or when `ModelAutoRepair` can't fix validation issues, the system throws `ProviderException("all", ...)` which becomes HTTP 502
- **Impact:** The API returns 502 instead of a usable fallback flow

### Biggest Generation Failure Pattern
1. LLM returns malformed VXML (missing tags, wrong structure)
2. `LlmResponseNormalizer` cleans it but it's still invalid
3. `VxmlToModelConverter` returns null → throws ProviderException → 502
4. OR: VXML parses but FlowModel has validation errors
5. `ModelAutoRepair` tries to fix it
6. If repair fails → throws ProviderException → 502

**The system never falls back to `TemplateGenerator`.** It only falls back to raw prompt if Pass 1 (refiner) fails.

---

## 5. DomainDetector Status

### Current Role
- **Class:** `DomainDetector.java`
- **Purpose:** Detects business domain from user prompt using keyword matching
- **When used:** 
  - In `UnifiedAiEngine.generateFlow()` — for logging/title only (`detectedDomain` is used for flow title and error messages)
  - In `TemplateGenerator.generateStructuredResponse()` — to select the right template
  - In `AiOperationRouter` — to decide if a prompt should trigger GENERATE_FLOW vs CHAT

### Does it affect generation?
- **Minimally.** The detected domain is used for:
  1. Flow title generation (`generateDescriptiveTitle()`)
  2. Error messages
  3. Template selection (only when TemplateGenerator is used, which is never in normal flow)
- The actual LLM generation prompt does NOT include the detected domain. The LLM infers the domain itself.

### Current behavior alignment with generic AI generator
- **Not aligned.** The `DomainDetector` returns a fixed set of domains. If the user's prompt doesn't match any keyword, it returns "generic".
- **The telecom→restaurant bug:** The prompt "Create a telecom customer support IVR. Billing, Roaming, SIM Support, Broadband" was detected as "restaurant". This happens because:
  1. `restaurant` keywords include "billing", "reservation", "menu"
  2. `telecom` keywords include "telecom", "phone", "mobile", "sim", "roaming", "broadband"
  3. The detector uses first-match, not weighted scoring
  4. "Billing" matches restaurant before "telecom" is checked because `restaurant` appears before `telecom` in the `LinkedHashMap`

### Impact
- **Low impact on generation** because the detected domain doesn't affect the LLM prompt
- **Medium impact on UX:** Wrong domain leads to wrong flow title
- **Medium impact on TemplateGenerator:** If TemplateGenerator were ever used as fallback, it would produce a restaurant flow for a telecom prompt

---

## 6. Conversation Memory Status

### Session Creation
- **Class:** `ChatService.startSession()`
- **Storage:** `AiSessionDao` (JDBC, PostgreSQL)
- **Fields:** id, tenantId, channel, customerIdentifier, status, title, createdAt, updatedAt
- **DB offline fallback:** Returns transient session object if DB is unavailable

### Message Storage
- **Class:** `ChatService.sendMessage()`
- **Storage:** `MessageDao` (JDBC, PostgreSQL)
- **Fields:** id, sessionId, tenantId, turnNumber, role (USER/ASSISTANT), content, modelUsed, tokensInput, tokensOutput, metadata
- **Persistence:** Every user message and AI reply is persisted

### History Retrieval
- **Not implemented for LLM context.** The conversation history is stored in DB but there's no retrieval mechanism that loads previous messages and passes them to the LLM as context.
- **Current behavior:** Each `sendMessage()` call to the LLM uses `List.of()` (empty history). The LLM has no memory of previous turns.

### Tenant Handling
- **Implemented:** Every session and message has a `tenantId`
- **Isolation:** `SessionMemoryStore` is per-session UUID, not per-tenant
- **DB queries:** Use tenantId for filtering

### Memory Usage During Generation
- **SessionMemoryStore** stores per-session:
  - FlowModel (the canonical internal representation)
  - Provider name
  - Summaries
  - Node/edge counts
- **Used in generation?** The `FlowModel` is used for:
  - `improveFlow()` — reads existing model, modifies it
  - `ChatService` — reads flow for context (but doesn't pass to LLM)
- **Not used for:** LLM conversation context. The LLM never sees previous turns.

---

## 7. Current Maturity Level

### Rating: Functional MVP

**Reasons for this rating:**

**What works:**
- End-to-end flow generation from prompt to React Flow JSON
- Multi-provider support (Groq, Gemini, Ollama)
- Circuit breaker prevents cascading failures
- Auto-repair fixes common LLM output issues
- Session memory persists flows
- Chat and flow generation are separated
- Multi-tenant aware

**What's missing for Production Ready:**

| Area | Gap |
|------|-----|
| Reliability | No template fallback when all providers fail → returns 502 |
| Provider resilience | Circuit breaker thresholds hardcoded, no reset endpoint |
| Conversation memory | No LLM context — every message is stateless |
| Domain detection | First-match keyword system, no weighted scoring |
| Error handling | 502 returned for generation failures instead of graceful fallback |
| Observability | No structured request-level logging (requestId, duration, etc.) |
| Configuration | Circuit breaker settings not configurable via env vars |
| Timeout | Ollama timeout may not be properly applied |
| Template fallback | Documented but not implemented in actual code path |

---

## 8. Current Issues Summary

| Issue | Location/Class | Impact | Severity |
|-------|---------------|--------|----------|
| All providers exhausted → HTTP 502 | `ProviderManager.executeWithRetryAndFallback()` | User sees hard error instead of fallback flow | **Critical** |
| No automatic template fallback | `ProviderManager.PROVIDER_PRIORITY` excludes template-generator | System fails instead of generating a basic flow | **Critical** |
| Circuit breaker 5-minute cooldown with no reset | `CircuitBreaker.java` + no reset endpoint | Development blocked after one quota error | **High** |
| Circuit breaker thresholds hardcoded | `CircuitBreaker.java` lines 46, 94, 166-169 | Cannot tune per environment | **High** |
| Ollama not in fallback chain | `ProviderManager.PROVIDER_PRIORITY` | Ollama only works if explicitly selected | **Medium** |
| Telecom detected as restaurant | `DomainDetector.detect()` first-match logic | Wrong domain label in UI/titles | **Medium** |
| No LLM conversation context | `ChatService.sendMessage()` passes `List.of()` | AI has no memory of previous turns | **Medium** |
| Domain detection doesn't affect generation | `UnifiedAiEngine.generateFlow()` | Domain detection is only for logging | **Low** |
| PromptRefinerService uses "all" in ProviderException | `PromptRefinerService.java:89` | Error message misleading | **Low** |
| selectedProvider/actualProviderUsed lost in Flow | `UnifiedAiEngine.java:327-328` (partially fixed) | Incomplete attribution | **Low** |

---

## 9. Final Project Status Summary

### What is Already Completed
- End-to-end AI flow generation pipeline (prompt → VoiceXML → FlowModel → React Flow JSON)
- Multi-provider LLM support (Groq, Gemini, Ollama)
- Circuit breaker with exponential backoff
- Per-provider retry logic (3 retries per provider)
- Auto-repair engine for common FlowModel issues
- Session memory (FlowModel per session)
- Prompt refinement (Pass 1)
- Semantic caching for Pass 2 responses
- Domain detection (basic)
- Multi-tenant support
- Jakarta Servlet-based REST API
- PostgreSQL persistence for sessions and messages

### What is Partially Implemented
- **Template fallback:** `TemplateGenerator` exists and works, but is NOT connected to the automatic fallback chain. It's only reachable if explicitly requested as "template-generator" provider.
- **Provider attribution:** `AiResponse` carries `selectedProvider`/`actualProviderUsed`, and `Flow`/`ChatResponse` have the fields, but some paths don't populate them consistently.
- **Conversation memory:** Messages are stored in DB but never loaded as LLM context.

### What is Missing
1. **Automatic template fallback** when all LLM providers fail
2. **Circuit breaker configuration** via environment variables
3. **Circuit breaker reset endpoint** (`POST /api/v1/ai/providers/reset`)
4. **LLM conversation history** — stateless chat turns
5. **Weighted domain detection** — current first-match is brittle
6. **Structured request-level logging** (requestId, provider, model, duration, success, fallback)
7. **Intermediate business plan validation** — no "design" stage before VoiceXML generation
8. **Ollama in default fallback chain** — currently excluded from `PROVIDER_PRIORITY`
9. **Configurable retry/backoff parameters** — hardcoded in `ProviderManager`

### Biggest Blockers Preventing Reliable AI IVR Generator

1. **No graceful degradation:** When all providers fail, the API returns 502. A production IVR platform must always return a usable flow, even if it's a template.

2. **Circuit breaker prevents recovery:** After quota errors, providers are locked out for 5 minutes with no manual override. In development, this makes the system unusable after a single quota spike.

3. **Stateless chat:** The AI assistant has no memory. Every message is treated independently, making conversational IVR design impossible.

4. **Domain detection is brittle:** First-match keyword logic produces wrong domains, affecting UX and any domain-dependent logic.

5. **No intermediate plan validation:** The system generates VoiceXML in one LLM call. If the output is structurally invalid, repair may fail and the entire request fails. A "plan → validate → render" architecture would be more robust.

---

**End of Audit Report**