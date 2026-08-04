# NexusIVR AI Backend — Core Java Project Structure

**Constraint:** Core Java only — no Spring / Spring Boot / DI containers / ORM frameworks.
Allowed as **infrastructure libraries** (not frameworks): `jakarta.servlet-api` (we still need something to receive HTTP requests since this is a web backend), a JDBC driver, an HTTP client for calling LLM providers, and a JSON library. Everything else — dependency wiring, routing, validation, tenant context, caching, scheduling, eventing — is hand-rolled Java.

**Scope:** package structure only, with justification per package. No implementations.

---

## 0. Design Assumptions (Core Java implications)

Removing Spring changes how several concerns get solved. These decisions shape the package list below:

| Concern | Framework way (removed) | Core Java way (used here) |
|---|---|---|
| Dependency injection | `@Autowired`, Spring context | Manual DI via a `bootstrap` package — factories/builders wire objects once at startup and pass them through constructors |
| HTTP entry point | `@RestController` | `HttpServlet` subclasses in `servlet`, registered in `web.xml` or via `@WebServlet` |
| Request interception | Spring interceptors/filters | `jakarta.servlet.Filter` implementations in `filter` |
| Tenant context propagation | `ThreadLocal`-backed Spring bean scope | Manual `ThreadLocal<TenantContext>` holder in `tenant` |
| Scheduling (Improve/Analytics batch jobs) | `@Scheduled` | `ScheduledExecutorService` wrapped in `scheduler` |
| Async/event bus | Spring events / Kafka starter | A minimal in-process pub/sub in `event`, with an optional Kafka **client** (raw `kafka-clients` jar, not a framework) plugged in behind the same interface |
| Validation | Bean Validation (`@Valid`) | Hand-written validators in `validator` |
| Object mapping | MapStruct/ModelMapper | Hand-written mappers in `mapper` |

Everything downstream is a plain `.jar`, runnable in a plain Servlet container (Tomcat/Jetty) with no framework runtime attached.

---

## 1. Complete Package List (at a glance)

```
com.nexusivr.ai
├── bootstrap
├── config
├── constant
├── servlet
├── filter
├── listener
├── security
├── tenant
├── router
├── assistant
├── flowgenerator
├── validation
├── validator
├── rag
├── memory
├── functioncalling
├── sentiment
├── summary
├── analytics
├── improve
├── provider
├── prompt
├── guardrails
├── event
├── service
├── dao
├── repository
├── dto
├── mapper
├── model
├── cache
├── scheduler
├── util
└── exception
```

Every package below is explained the same way: **Why it exists → What belongs inside → Dependencies → Best practices.**

---

## 2. Foundation & Bootstrapping Packages

### `bootstrap`
**Why it exists:** With no Spring container, *something* has to wire up every object graph (which `AiModelProvider` implementation, which `Repository` implementations, which module instances) exactly once, at application startup, and hand the finished graph to the servlets. This package is that "composition root."
**What belongs inside:** `ApplicationBootstrap`, `ModuleWiring` (one per module, e.g. `AssistantModuleWiring`), `AppContext` (a plain object holding fully-constructed singletons, injected manually into servlets).
**Dependencies:** Depends on almost everything (it constructs everything), but nothing depends on it — this is intentional; it sits at the top, never imported by domain code.
**Best practices:** Constructor injection only — pass dependencies through constructors, never use static singletons/service locators inside business classes. Keep this package "dumb": it wires, it does not contain logic. Fail fast on startup (throw immediately if a required config/provider is missing) rather than at first request.

### `config`
**Why it exists:** Centralizes reading of configuration (properties files, environment variables, per-tenant overrides) so no other package ever calls `System.getenv()` or reads a `.properties` file directly.
**What belongs inside:** `AppConfig`, `TenantConfigLoader`, `DatabaseConfig`, `LlmProviderConfig`, `KafkaConfig` (if event bus uses Kafka), `ConfigSource` (interface abstracting file/env/remote config).
**Dependencies:** `util` (for parsing), `constant` (default values/keys). Consumed by `bootstrap`.
**Best practices:** Config objects are immutable value objects, built once at startup. Never pass raw `Properties`/`Map` objects into business logic — always a typed config class. Validate config values at load time, not at first use.

### `constant`
**Why it exists:** A single home for fixed values (header names, event topic names, config keys, default limits) so they aren't duplicated as magic strings/numbers across 30+ packages.
**What belongs inside:** `HttpHeaders`, `ConfigKeys`, `EventTopics`, `DefaultLimits` — all `final` classes of `public static final` constants, or enums where the value has behavior.
**Dependencies:** None — this is a leaf package everything else may depend on.
**Best practices:** Prefer typed enums over raw string/int constants wherever the value represents a closed set of options (e.g. `IntentCategory`, `ValidationSeverity`).

---

## 3. Web-Facing Packages

### `servlet`
**Why it exists:** This is the only package allowed to know about `HttpServletRequest`/`HttpServletResponse`. It's the front door — every external call (from the API Gateway) lands here first.
**What belongs inside:** `ConversationTurnServlet`, `FlowGenerationServlet`, `AnalyticsQueryServlet`, `DocumentIngestionServlet`, `HealthCheckServlet` — one servlet per external-facing use case, thin by design.
**Dependencies:** `dto` (parses request into DTOs), `service` (delegates to the relevant module's service), `mapper` (DTO ↔ domain), `exception` (translates exceptions into HTTP status codes). Never talks to `dao`/`repository` directly.
**Best practices:** A servlet method should be ~10-20 lines: parse request → build command DTO → call service → map result → write response. All actual logic belongs in `service`/module packages, never in the servlet itself — this keeps business logic testable without a servlet container.

### `filter`
**Why it exists:** Cross-cutting HTTP concerns (auth, tenant resolution, CORS, request logging, rate limiting) need to run before *every* servlet without each servlet re-implementing them.
**What belongs inside:** `AuthenticationFilter`, `TenantResolutionFilter`, `RequestLoggingFilter`, `RateLimitFilter`, `CorsFilter`.
**Dependencies:** `security`, `tenant`, `exception`.
**Best practices:** Order matters — configure filter chain explicitly (`web.xml` `<filter-mapping>` order or `@WebFilter` with defined priority) so `TenantResolutionFilter` always runs before anything that reads `TenantContext`. Filters should be side-effect-focused (populate context, reject early) not business-logic-focused.

### `listener`
**Why it exists:** Servlet-container lifecycle hooks (app startup/shutdown, session creation) are the Core-Java equivalent of Spring's `ApplicationContext` lifecycle events.
**What belongs inside:** `AppStartupListener` (implements `ServletContextListener`, calls into `bootstrap`), `AppShutdownListener` (closes DB pools, flushes event bus, shuts down `scheduler` executors), `SessionListener`.
**Dependencies:** `bootstrap`, `scheduler`, `cache`.
**Best practices:** Startup listener should be the *only* place `ApplicationBootstrap` is invoked. Shutdown listener must release all resources (thread pools, DB connections, open sockets) to avoid leaks on redeploy.

---

## 4. Identity, Tenancy & Governance Packages

### `security`
**Why it exists:** Authentication/authorization is security-sensitive and must live in exactly one place — nothing about auth token parsing or permission checks should be duplicated across modules.
**What belongs inside:** `AuthToken`, `TokenValidator`, `AuthorizationPolicy`, `Permission` (enum), `FunctionExecutionPolicy` (used specifically by `functioncalling` to gate which tools a tenant/user may invoke).
**Dependencies:** `util` (crypto/JWT parsing helpers), `constant`, `exception`.
**Best practices:** Never trust client-supplied tenant/user IDs without validating the signed token first. Keep authorization *decisions* here, but let each module enforce them at its own boundary (defense in depth) rather than relying solely on the filter layer.

### `tenant`
**Why it exists:** This is a genuinely multi-tenant platform; without Spring's request-scoped beans, tenant context has to be propagated manually and safely across a request's thread (and cleaned up, or it leaks into thread-pool-reused threads).
**What belongs inside:** `TenantContext` (value object: tenantId, plan tier, region, locale), `TenantContextHolder` (`ThreadLocal` wrapper with `set/get/clear`), `TenantAwarePropagator` (helper to carry context into `ExecutorService` tasks/async work).
**Dependencies:** `constant`, `exception`.
**Best practices:** **Always** clear the `ThreadLocal` in a `finally` block (typically in `filter`) — this is the #1 source of tenant-data leakage bugs in thread-pooled servers. Any class that spawns a background thread (`scheduler`, `event`) must explicitly propagate `TenantContext` into that thread — it does not cross thread boundaries automatically.

### `guardrails`
**Why it exists:** Enterprise/regulated tenants require enforceable policy around what the AI can output or collect (PII redaction, disallowed topics, required disclosures) — this needs to be a shared, auditable checkpoint, not logic duplicated inside `assistant`, `flowgenerator`, etc.
**What belongs inside:** `PiiDetector`, `PiiRedactor`, `ContentPolicyEnforcer`, `OutputSanitizer`, `ComplianceRuleSet` (per-tenant rules).
**Dependencies:** `model`, `tenant`, `util`.
**Best practices:** Guardrail checks run both **before** a prompt is sent to a provider (input sanitization) and **after** a response is received (output filtering) — never assume one pass is enough. Log every redaction/block event for audit purposes (via `service`/`repository`, not silently).

---

## 5. AI Capability Packages (the 11 modules)

Each of these mirrors the same internal shape: a `*Service` (orchestration for that capability), request/response-shaped classes kept in `dto`, and persistent state kept in `repository`/`model` — the package itself holds only the module's own logic and interfaces.

### `router`
**Why it exists:** Single decision point for "what happens with this inbound request" — coarse intent classification and model/cost-tier routing, kept separate so no other module has to know about routing rules.
**What belongs inside:** `AiRouterService`, `RoutingRule`, `RouteDecision`, `ModelTierSelector`, `FallbackChain` (LLM provider failover).
**Dependencies:** `assistant`, `flowgenerator` (delegates to these), `tenant`, `provider` (to know which providers/tiers are healthy), `event` (publishes routing decisions for analytics).
**Best practices:** Router must remain stateless — it decides and forwards, it never stores conversation state itself (that's `memory`'s job). Keep routing rules data-driven (loaded via `config`) rather than hardcoded `if/else` chains, so tenants can be onboarded with new rules without a redeploy.

### `assistant`
**Why it exists:** The core conversational orchestrator for a single turn — the one module allowed to call Memory, RAG, Function Calling, and Sentiment together and produce a final answer.
**What belongs inside:** `AiAssistantService`, `ConversationTurnHandler`, `ResponseComposer`, `EscalationPolicy` (when to hand off to a human).
**Dependencies:** `memory`, `rag`, `functioncalling`, `sentiment`, `provider`, `prompt`, `guardrails`, `event`, `model`.
**Best practices:** Keep the "one turn" use case as a single, readable orchestration method — resist the temptation to let this class grow into a god-class; delegate actual reasoning to `provider`, actual grounding to `rag`, etc. Always publish a turn-completed event, even on failure paths (with an error status), so `analytics` sees the full picture.

### `flowgenerator`
**Why it exists:** Converts natural-language/business requirements into a structured IVR flow graph — an authoring-time capability, kept separate from the live-conversation path.
**What belongs inside:** `FlowGeneratorService`, `FlowGraphBuilder`, `FlowNode`/`FlowEdge` (draft-only representations — the canonical flow model lives in the external Flow Store), `FlowGenerationRequest`.
**Dependencies:** `provider`, `prompt`, `validation`, `model`.
**Best practices:** Never persist a generated flow directly to the production flow store from this package — always return a draft object and let the calling `service`/servlet layer route it through approval. Treat the LLM's output as untrusted structured data until `validation` clears it.

### `validation`
**Why it exists:** The AI-generated-content quality gate — structural, semantic, and policy checks on flows, prompts, RAG documents, and Improve suggestions before they go live. (Distinct from `validator`, see below.)
**What belongs inside:** `FlowStructureValidator`, `SemanticValidator` (LLM-as-judge), `PolicyComplianceValidator`, `ValidationReport`, `ValidationIssue`.
**Dependencies:** `provider` (for LLM-as-judge calls), `guardrails`, `model`.
**Best practices:** Return a full report (all issues found) rather than fail-fast on the first problem — authors need the complete picture in one pass. Keep each validator independently testable/pluggable — new validators should be addable without touching existing ones (Strategy pattern: `List<FlowValidationRule>`).

### `validator`
**Why it exists:** Ordinary input/DTO validation (null checks, field length, format, required fields) — the Core-Java replacement for Bean Validation annotations. Kept separate from `validation` (which validates AI-*generated content*, a business concern) because this validates *incoming request shape*, a plumbing concern.
**What belongs inside:** `RequestValidator<T>` (generic interface), `ConversationTurnRequestValidator`, `IngestionRequestValidator`, `ValidationException` triggers.
**Dependencies:** `dto`, `exception`, `constant`.
**Best practices:** Validators should be composable (chain multiple small checks) and called at the `servlet`/`service` boundary — never deep inside domain logic. Fail with a structured error listing *all* violated fields, not just the first one found — better DX for API consumers.

### `rag`
**Why it exists:** Retrieval-augmented generation — grounds AI answers in tenant-specific documents; ingestion and retrieval are distinct enough responsibilities to warrant their own package, kept out of `assistant` so grounding logic is reusable by `flowgenerator`/`improve` too.
**What belongs inside:** `RagService`, `DocumentIngestionPipeline`, `Chunker`, `EmbeddingClient` (interface; concrete provider client lives in `provider`), `VectorSearchClient` (interface), `RetrievedContext`.
**Dependencies:** `provider` (embeddings + vector DB clients), `repository` (document metadata), `tenant`.
**Best practices:** Always scope retrieval queries by tenant at the query-construction level, never filter tenant results *after* retrieval (wasteful and risks leakage). Keep chunking strategy configurable per document type, not one-size-fits-all.

### `memory`
**Why it exists:** Owns short-term (session) and long-term (customer history) conversational state, and the token-budget/summarization logic — so `assistant` never has to reason about context-window math itself.
**What belongs inside:** `ConversationMemoryService`, `SessionMemoryStore` (fast, TTL'd), `LongTermMemoryStore`, `MemorySummarizer`, `TokenBudgetManager`.
**Dependencies:** `cache` (session store backing), `repository` (long-term persistence), `provider` (summarization calls), `tenant`.
**Best practices:** Session memory must always be tenant- and session-scoped in its cache keys (`tenant:{id}:session:{id}`) — never a shared/global key space. Summarize proactively before hitting a model's context limit, not reactively after a failed call.

### `functioncalling`
**Why it exists:** The trust boundary between LLM "I want to call a function" requests and real business systems (CRM, payments, bookings) — arguments must be validated and authorized *outside* the model's control.
**What belongs inside:** `FunctionCallingService`, `FunctionRegistry` (per-tenant available functions + JSON schema), `FunctionExecutor`, `FunctionInvocationAuditLog`.
**Dependencies:** `security` (`FunctionExecutionPolicy`), `validator` (argument schema validation), `event` (audit), `repository`.
**Best practices:** Treat every LLM-proposed function call as an untrusted request: re-validate arguments against the registered schema and re-check authorization even if the model "should" have gotten it right. Every execution — success or failure — must be audit-logged with tenant, user, function name, and outcome.

### `sentiment`
**Why it exists:** Emotional/intent signal extraction, needed both in real time (to steer a live turn) and post-call (to feed Summary/Analytics) — kept as its own module so it can use a lighter/faster model for the real-time path independent of the Assistant's main model choice.
**What belongs inside:** `SentimentAnalysisService`, `SentimentScore`, `EscalationRiskEvaluator`, `SentimentTrendCalculator` (post-call).
**Dependencies:** `provider`, `event`, `model`.
**Best practices:** Keep the real-time path on a strict latency budget (fail open — if sentiment scoring times out, proceed without it rather than blocking the conversation). Publish scores as events rather than only returning them synchronously, so Analytics doesn't need a separate polling mechanism.

### `summary`
**Why it exists:** Produces structured, human-readable call summaries after a conversation ends — entirely async, entirely off the live-call path.
**What belongs inside:** `CallSummaryService`, `SummaryGenerator`, `SummaryReport` (resolution status, entities, action items), `SummaryEventConsumer`.
**Dependencies:** `provider`, `prompt`, `event` (subscribes to `ConversationEnded`), `repository`.
**Best practices:** Trigger strictly off the `ConversationEnded` event, never a direct call from `assistant` — this keeps Assistant's hot path free of any dependency on Summary's availability or latency. Idempotency matters here — the same event might be redelivered, so summary generation should be safe to run twice without duplicating records.

### `analytics`
**Why it exists:** The fleet-level view (containment rate, handling time, sentiment trends, cost-per-conversation, model drift) — a read/aggregation-focused module, downstream of everything else.
**What belongs inside:** `AnalyticsService`, `MetricsAggregator`, `AnalyticsEventConsumer`, `DashboardQueryService`, `CostTracker`.
**Dependencies:** `event` (consumes essentially every event type), `repository`, `tenant`.
**Best practices:** Never let a live conversation path block on Analytics availability — it is purely a consumer, never a synchronous dependency of `assistant`/`router`. Pre-aggregate expensive metrics on write (event consumption) rather than computing them at query time, to keep dashboard queries fast.

### `improve`
**Why it exists:** The continuous-improvement loop — looks at Analytics/Summary output and proposes concrete, human-reviewable changes to prompts, RAG content, or flows.
**What belongs inside:** `AiImproveService`, `ImprovementSuggestionGenerator`, `SuggestionReviewWorkflow` (PENDING_REVIEW/APPROVED/REJECTED state), `TrendDetector`.
**Dependencies:** `analytics`, `flowgenerator` (to draft proposed flow changes), `provider`, `scheduler` (runs on a schedule, not per-event).
**Best practices:** Improve must never write directly to production prompts/flows/RAG content — it always produces a suggestion object requiring explicit human approval. Run as a scheduled batch job (`scheduler`), not an event-per-event reaction, to avoid noisy, low-value micro-suggestions.

---

## 6. Model & Data Access Packages

### `provider`
**Why it exists:** The single abstraction boundary between "our code" and "external AI vendors" (LLM completion APIs, embedding APIs, vector DB APIs). This is what makes swapping OpenAI for Anthropic, or adding a local model, a one-class change.
**What belongs inside:** `AiModelProvider` (interface), `OpenAiModelProvider`, `AnthropicModelProvider`, `AzureOpenAiModelProvider`, `EmbeddingProvider` (interface + implementations), `VectorStoreClient` (interface + implementations), `HttpLlmClient` (shared HTTP plumbing).
**Dependencies:** `config` (API keys/endpoints), `model`, `util` (JSON), `exception`.
**Best practices:** No module outside `provider` should ever import a vendor SDK class directly — everything routes through the `AiModelProvider` interface. Centralize retry/timeout/circuit-breaker logic here once, not per-caller.

### `prompt`
**Why it exists:** Prompt templates are a versioned, tenant-customizable asset (brand voice, language, compliance disclaimers) — treating them as a managed resource, not string literals scattered through code, is essential at enterprise scale.
**What belongs inside:** `PromptTemplate`, `PromptRegistry` (tenant-aware lookup with default fallback), `PromptTemplateEngine` (variable substitution), `PromptVersion`.
**Dependencies:** `repository` (template storage), `tenant`, `util`.
**Best practices:** Templates are data, not code — store them externally (DB or resource files), never hardcode a prompt string inside a service class. Version every template so `analytics`/`improve` can correlate a template version with conversation outcomes.

### `event`
**Why it exists:** The Core-Java substitute for Spring's event publisher / a messaging framework — a minimal, in-process (or Kafka-backed) pub/sub so `summary`, `analytics`, and `improve` can react to what happened without `assistant` calling them directly.
**What belongs inside:** `DomainEvent` (base class), `EventPublisher` (interface), `InMemoryEventPublisher` (simple `ExecutorService`-backed implementation), `KafkaEventPublisher` (optional, if scaling beyond a single instance), `EventSubscriber` (interface), concrete events: `ConversationTurnCompleted`, `ConversationEnded`, `SentimentRecorded`, `FunctionExecuted`.
**Dependencies:** `tenant` (every event carries a `tenantId`), `util`.
**Best practices:** Every event must be immutable and carry `tenantId` + timestamp + correlation/session ID. Keep `EventPublisher` an interface from day one, even if the first implementation is in-memory — this is exactly the seam that lets you swap to Kafka later without touching publishers/consumers.

### `service`
**Why it exists:** Not every operation belongs inside one of the 11 capability packages — this holds cross-cutting or coordinating services that sit above them (e.g., a servlet needs to call two modules and combine results, or a shared "conversation lifecycle" service coordinates Router → Assistant → Memory).
**What belongs inside:** `ConversationLifecycleService`, `TenantOnboardingService`, `HealthCheckService` — coordination-layer classes that call into capability-module services, never business logic that belongs in one specific module.
**Dependencies:** Capability packages (`router`, `assistant`, etc.), `dto`, `mapper`.
**Best practices:** Keep this package small and be strict about what qualifies — if logic clearly belongs to one module, put it there, not here. This package exists for genuine cross-module coordination only, not as a dumping ground.

### `dao`
**Why it exists:** With no ORM/JPA, raw SQL access needs a disciplined home — DAOs are the lowest-level, table-oriented data access classes, wrapping raw JDBC.
**What belongs inside:** `ConversationDao`, `FlowDraftDao`, `PromptTemplateDao`, `AnalyticsEventDao`, `JdbcConnectionPool` (or a thin wrapper around one).
**Dependencies:** `model`, `exception`, `config` (connection settings). JDBC driver only — no other package depends on `dao` except `repository`.
**Best practices:** DAOs speak SQL and return `model` objects — no DTOs, no business logic here. Always use parameterized/prepared statements — never string-concatenated SQL, especially given tenant-scoped multi-tenant queries. Close resources with try-with-resources religiously.

### `repository`
**Why it exists:** Sits above `dao` to add tenant-scoping and higher-level query semantics ("find active conversations for tenant X") — this is the layer capability modules are actually allowed to call, keeping raw SQL/JDBC fully hidden from business logic.
**What belongs inside:** `ConversationRepository`, `FlowDraftRepository`, `PromptTemplateRepository`, `TenantAwareRepository` (abstract base enforcing tenant filtering on every query).
**Dependencies:** `dao`, `tenant`, `model`.
**Best practices:** Every repository method must go through `TenantAwareRepository`'s tenant-scoping logic — never bypass it "just this once" for convenience. Repository interfaces should be defined near their consuming module's needs (query methods named for use cases, not generic CRUD dumps).

---

## 7. Data Shape Packages

### `dto`
**Why it exists:** The shape of data crossing the servlet/HTTP boundary is a different concern from the shape of internal domain objects — DTOs insulate the public API contract from internal refactoring.
**What belongs inside:** `ConversationTurnRequest`, `ConversationTurnResponse`, `FlowGenerationRequest`, `AnalyticsQueryRequest` — plain, framework-free POJOs, typically with builder-style construction.
**Dependencies:** `constant` only, ideally. Should not depend on `model`.
**Best practices:** DTOs are dumb data holders — no business logic, no validation logic beyond basic shape (that's `validator`'s job). Never leak a DTO into `dao`/`repository` — those speak `model`, not `dto`.

### `mapper`
**Why it exists:** Without MapStruct, DTO↔model↔domain-event translation needs an explicit, hand-written home rather than being scattered inline inside services.
**What belongs inside:** `ConversationMapper`, `FlowMapper`, `SentimentMapper` — one mapper class per bounded concept, static or instance methods, `toDto()`/`toModel()` pairs.
**Dependencies:** `dto`, `model`.
**Best practices:** Mappers must be pure functions — no side effects, no I/O, no logic beyond field translation. Keep mapping explicit and readable (no reflection-based generic mapping) — this is Core Java precisely because "explicit over magic" is the point.

### `model`
**Why it exists:** The internal domain/entity representation — the vocabulary every module actually reasons in (as opposed to the wire format in `dto`).
**What belongs inside:** `Conversation`, `Turn`, `FlowDraft`, `SentimentScore`, `TenantContext` (re-exported/used from `tenant`), `RetrievedContext`, `FunctionInvocation` — plain domain objects, ideally immutable value objects where possible.
**Dependencies:** `constant` only. This is one of the lowest-level packages — should not depend on `dto`, `dao`, or any capability package.
**Best practices:** Keep entities free of persistence or transport annotations (no JDBC/JSON annotations bleeding in) — that coupling belongs in `dao`/`mapper`, not here. Favor immutability (`final` fields, no setters) to make concurrent/multi-threaded correctness easier to reason about.

---

## 8. Infrastructure Support Packages

### `cache`
**Why it exists:** Session memory and hot lookups (routing rules, prompt templates) need a fast in-memory or Redis-backed cache — a dedicated package keeps caching concerns (TTL, eviction, key-namespacing) out of business logic.
**What belongs inside:** `CacheClient` (interface), `InMemoryCache` (simple `ConcurrentHashMap`-based, TTL via a background sweep thread), `RedisCacheClient` (thin wrapper if Redis is used), `CacheKeyBuilder` (enforces tenant-namespaced keys).
**Dependencies:** `tenant`, `config`.
**Best practices:** Always build cache keys through `CacheKeyBuilder`, never string-concatenate keys ad hoc — this is where tenant-isolation bugs hide. Keep the interface small enough that swapping in-memory for Redis requires no caller changes.

### `scheduler`
**Why it exists:** Batch/periodic work (Improve's suggestion generation, Analytics roll-ups, cache TTL sweeps) needs scheduling — the Core-Java equivalent of `@Scheduled`.
**What belongs inside:** `TaskScheduler` (wraps `ScheduledExecutorService`), `ScheduledTask` (interface), concrete tasks: `ImprovementSuggestionTask`, `AnalyticsRollupTask`.
**Dependencies:** Capability packages it schedules work for (`improve`, `analytics`), `tenant` (must propagate context per scheduled run, per tenant).
**Best practices:** Every scheduled task must iterate tenants explicitly and set/clear `TenantContext` per iteration — a shared background thread has no natural per-request tenant scoping. Shut the executor down cleanly in `listener`'s `AppShutdownListener`.

### `util`
**Why it exists:** Small, stateless, framework-agnostic helpers used across many packages (JSON parsing, string utilities, ID generation) — this avoids either duplicating helpers or letting incidental utility logic bloat domain classes.
**What belongs inside:** `JsonUtil`, `StringUtil`, `IdGenerator`, `DateTimeUtil`, `HashUtil`.
**Dependencies:** None (or only a JSON library). Everything may depend on `util`; `util` depends on nothing internal.
**Best practices:** Keep this package deliberately boring — if a "utility" starts to encode business rules, it doesn't belong here. Static methods only, no state, no side effects beyond pure computation.

### `exception`
**Why it exists:** A consistent exception hierarchy lets `servlet`/`filter` map errors to HTTP responses uniformly, and lets every module fail in a recognizable, typed way instead of throwing raw `RuntimeException`.
**What belongs inside:** `NexusAiException` (base), `TenantNotFoundException`, `ValidationException`, `ProviderUnavailableException`, `FunctionExecutionException`, `UnauthorizedException` — each mapped to an HTTP status by a central handler in `servlet`/`filter`.
**Dependencies:** `constant` (error codes).
**Best practices:** Exceptions should carry enough structured detail (error code, tenant ID, correlation ID) to be genuinely useful in logs — not just a message string. Never use exceptions for normal control flow (e.g., "not found" in a lookup that's expected to sometimes miss should return `Optional`, not throw).

---

## 9. Package Dependency Rules (summary)

To keep this from decaying into a tangle, these directions are enforced:

- `model`, `constant`, `util`, `exception` → depended on by everyone, depend on nothing internal (leaf packages).
- `dto` → depends only on `constant`; never depended on by `dao`/`repository`/`model`.
- `dao` → depends only on `model`, `config`, `exception`; only `repository` depends on `dao`.
- `repository` → depends on `dao`, `tenant`, `model`; capability packages depend on `repository`, never on `dao` directly.
- Capability packages (`router`, `assistant`, `flowgenerator`, `validation`, `rag`, `memory`, `functioncalling`, `sentiment`, `summary`, `analytics`, `improve`) → depend on `provider`, `prompt`, `repository`, `event`, `tenant`, `guardrails`, `model`, `dto`, `mapper` as needed; **never depend on each other's internals** — only on each other's public `*Service` interfaces, and only where the architecture explicitly allows it (e.g. `assistant` → `memory`/`rag`/`functioncalling`/`sentiment`; `improve` → `analytics`/`flowgenerator`).
- `servlet`, `filter`, `listener` → depend on `service`, capability-module service interfaces, `dto`, `mapper`, `validator`, `security`, `tenant`; nothing depends back on them.
- `bootstrap` → depends on everything (composition root); nothing depends on it.

No package below `bootstrap` may import `jakarta.servlet.*` except `servlet`, `filter`, and `listener` — this keeps business logic testable with plain JUnit, no servlet container required.

---

## 10. Final Folder Structure

```
nexusivr-ai-backend/
├── pom.xml                                   (or build.gradle — plain Java project, no Spring parent)
├── web.xml                                    # servlet/filter registration (if not using @WebServlet annotations)
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── nexusivr/
│   │   │           └── ai/
│   │   │               ├── bootstrap/
│   │   │               │   ├── ApplicationBootstrap.java
│   │   │               │   ├── AppContext.java
│   │   │               │   └── wiring/
│   │   │               │       ├── AssistantModuleWiring.java
│   │   │               │       ├── RouterModuleWiring.java
│   │   │               │       ├── RagModuleWiring.java
│   │   │               │       └── ... (one per module)
│   │   │               │
│   │   │               ├── config/
│   │   │               │   ├── AppConfig.java
│   │   │               │   ├── DatabaseConfig.java
│   │   │               │   ├── LlmProviderConfig.java
│   │   │               │   ├── KafkaConfig.java
│   │   │               │   ├── TenantConfigLoader.java
│   │   │               │   └── ConfigSource.java
│   │   │               │
│   │   │               ├── constant/
│   │   │               │   ├── HttpHeaders.java
│   │   │               │   ├── ConfigKeys.java
│   │   │               │   ├── EventTopics.java
│   │   │               │   └── DefaultLimits.java
│   │   │               │
│   │   │               ├── servlet/
│   │   │               │   ├── ConversationTurnServlet.java
│   │   │               │   ├── FlowGenerationServlet.java
│   │   │               │   ├── AnalyticsQueryServlet.java
│   │   │               │   ├── DocumentIngestionServlet.java
│   │   │               │   └── HealthCheckServlet.java
│   │   │               │
│   │   │               ├── filter/
│   │   │               │   ├── AuthenticationFilter.java
│   │   │               │   ├── TenantResolutionFilter.java
│   │   │               │   ├── RequestLoggingFilter.java
│   │   │               │   ├── RateLimitFilter.java
│   │   │               │   └── CorsFilter.java
│   │   │               │
│   │   │               ├── listener/
│   │   │               │   ├── AppStartupListener.java
│   │   │               │   ├── AppShutdownListener.java
│   │   │               │   └── SessionListener.java
│   │   │               │
│   │   │               ├── security/
│   │   │               │   ├── AuthToken.java
│   │   │               │   ├── TokenValidator.java
│   │   │               │   ├── AuthorizationPolicy.java
│   │   │               │   ├── Permission.java
│   │   │               │   └── FunctionExecutionPolicy.java
│   │   │               │
│   │   │               ├── tenant/
│   │   │               │   ├── TenantContext.java
│   │   │               │   ├── TenantContextHolder.java
│   │   │               │   └── TenantAwarePropagator.java
│   │   │               │
│   │   │               ├── guardrails/
│   │   │               │   ├── PiiDetector.java
│   │   │               │   ├── PiiRedactor.java
│   │   │               │   ├── ContentPolicyEnforcer.java
│   │   │               │   ├── OutputSanitizer.java
│   │   │               │   └── ComplianceRuleSet.java
│   │   │               │
│   │   │               ├── router/
│   │   │               │   ├── AiRouterService.java
│   │   │               │   ├── RoutingRule.java
│   │   │               │   ├── RouteDecision.java
│   │   │               │   ├── ModelTierSelector.java
│   │   │               │   └── FallbackChain.java
│   │   │               │
│   │   │               ├── assistant/
│   │   │               │   ├── AiAssistantService.java
│   │   │               │   ├── ConversationTurnHandler.java
│   │   │               │   ├── ResponseComposer.java
│   │   │               │   └── EscalationPolicy.java
│   │   │               │
│   │   │               ├── flowgenerator/
│   │   │               │   ├── FlowGeneratorService.java
│   │   │               │   ├── FlowGraphBuilder.java
│   │   │               │   ├── FlowNode.java
│   │   │               │   ├── FlowEdge.java
│   │   │               │   └── FlowGenerationRequest.java
│   │   │               │
│   │   │               ├── validation/
│   │   │               │   ├── FlowStructureValidator.java
│   │   │               │   ├── SemanticValidator.java
│   │   │               │   ├── PolicyComplianceValidator.java
│   │   │               │   ├── ValidationReport.java
│   │   │               │   └── ValidationIssue.java
│   │   │               │
│   │   │               ├── validator/
│   │   │               │   ├── RequestValidator.java
│   │   │               │   ├── ConversationTurnRequestValidator.java
│   │   │               │   └── IngestionRequestValidator.java
│   │   │               │
│   │   │               ├── rag/
│   │   │               │   ├── RagService.java
│   │   │               │   ├── DocumentIngestionPipeline.java
│   │   │               │   ├── Chunker.java
│   │   │               │   ├── EmbeddingClient.java
│   │   │               │   ├── VectorSearchClient.java
│   │   │               │   └── RetrievedContext.java
│   │   │               │
│   │   │               ├── memory/
│   │   │               │   ├── ConversationMemoryService.java
│   │   │               │   ├── SessionMemoryStore.java
│   │   │               │   ├── LongTermMemoryStore.java
│   │   │               │   ├── MemorySummarizer.java
│   │   │               │   └── TokenBudgetManager.java
│   │   │               │
│   │   │               ├── functioncalling/
│   │   │               │   ├── FunctionCallingService.java
│   │   │               │   ├── FunctionRegistry.java
│   │   │               │   ├── FunctionExecutor.java
│   │   │               │   └── FunctionInvocationAuditLog.java
│   │   │               │
│   │   │               ├── sentiment/
│   │   │               │   ├── SentimentAnalysisService.java
│   │   │               │   ├── SentimentScore.java
│   │   │               │   ├── EscalationRiskEvaluator.java
│   │   │               │   └── SentimentTrendCalculator.java
│   │   │               │
│   │   │               ├── summary/
│   │   │               │   ├── CallSummaryService.java
│   │   │               │   ├── SummaryGenerator.java
│   │   │               │   ├── SummaryReport.java
│   │   │               │   └── SummaryEventConsumer.java
│   │   │               │
│   │   │               ├── analytics/
│   │   │               │   ├── AnalyticsService.java
│   │   │               │   ├── MetricsAggregator.java
│   │   │               │   ├── AnalyticsEventConsumer.java
│   │   │               │   ├── DashboardQueryService.java
│   │   │               │   └── CostTracker.java
│   │   │               │
│   │   │               ├── improve/
│   │   │               │   ├── AiImproveService.java
│   │   │               │   ├── ImprovementSuggestionGenerator.java
│   │   │               │   ├── SuggestionReviewWorkflow.java
│   │   │               │   └── TrendDetector.java
│   │   │               │
│   │   │               ├── provider/
│   │   │               │   ├── AiModelProvider.java
│   │   │               │   ├── OpenAiModelProvider.java
│   │   │               │   ├── AnthropicModelProvider.java
│   │   │               │   ├── AzureOpenAiModelProvider.java
│   │   │               │   ├── EmbeddingProvider.java
│   │   │               │   ├── VectorStoreClient.java
│   │   │               │   └── HttpLlmClient.java
│   │   │               │
│   │   │               ├── prompt/
│   │   │               │   ├── PromptTemplate.java
│   │   │               │   ├── PromptRegistry.java
│   │   │               │   ├── PromptTemplateEngine.java
│   │   │               │   └── PromptVersion.java
│   │   │               │
│   │   │               ├── event/
│   │   │               │   ├── DomainEvent.java
│   │   │               │   ├── EventPublisher.java
│   │   │               │   ├── InMemoryEventPublisher.java
│   │   │               │   ├── KafkaEventPublisher.java
│   │   │               │   ├── EventSubscriber.java
│   │   │               │   └── events/
│   │   │               │       ├── ConversationTurnCompleted.java
│   │   │               │       ├── ConversationEnded.java
│   │   │               │       ├── SentimentRecorded.java
│   │   │               │       └── FunctionExecuted.java
│   │   │               │
│   │   │               ├── service/
│   │   │               │   ├── ConversationLifecycleService.java
│   │   │               │   ├── TenantOnboardingService.java
│   │   │               │   └── HealthCheckService.java
│   │   │               │
│   │   │               ├── dao/
│   │   │               │   ├── ConversationDao.java
│   │   │               │   ├── FlowDraftDao.java
│   │   │               │   ├── PromptTemplateDao.java
│   │   │               │   ├── AnalyticsEventDao.java
│   │   │               │   └── JdbcConnectionPool.java
│   │   │               │
│   │   │               ├── repository/
│   │   │               │   ├── ConversationRepository.java
│   │   │               │   ├── FlowDraftRepository.java
│   │   │               │   ├── PromptTemplateRepository.java
│   │   │               │   └── TenantAwareRepository.java
│   │   │               │
│   │   │               ├── dto/
│   │   │               │   ├── ConversationTurnRequest.java
│   │   │               │   ├── ConversationTurnResponse.java
│   │   │               │   ├── FlowGenerationRequest.java
│   │   │               │   └── AnalyticsQueryRequest.java
│   │   │               │
│   │   │               ├── mapper/
│   │   │               │   ├── ConversationMapper.java
│   │   │               │   ├── FlowMapper.java
│   │   │               │   └── SentimentMapper.java
│   │   │               │
│   │   │               ├── model/
│   │   │               │   ├── Conversation.java
│   │   │               │   ├── Turn.java
│   │   │               │   ├── FlowDraft.java
│   │   │               │   ├── RetrievedContext.java
│   │   │               │   └── FunctionInvocation.java
│   │   │               │
│   │   │               ├── cache/
│   │   │               │   ├── CacheClient.java
│   │   │               │   ├── InMemoryCache.java
│   │   │               │   ├── RedisCacheClient.java
│   │   │               │   └── CacheKeyBuilder.java
│   │   │               │
│   │   │               ├── scheduler/
│   │   │               │   ├── TaskScheduler.java
│   │   │               │   ├── ScheduledTask.java
│   │   │               │   ├── ImprovementSuggestionTask.java
│   │   │               │   └── AnalyticsRollupTask.java
│   │   │               │
│   │   │               ├── util/
│   │   │               │   ├── JsonUtil.java
│   │   │               │   ├── StringUtil.java
│   │   │               │   ├── IdGenerator.java
│   │   │               │   ├── DateTimeUtil.java
│   │   │               │   └── HashUtil.java
│   │   │               │
│   │   │               └── exception/
│   │   │                   ├── NexusAiException.java
│   │   │                   ├── TenantNotFoundException.java
│   │   │                   ├── ValidationException.java
│   │   │                   ├── ProviderUnavailableException.java
│   │   │                   ├── FunctionExecutionException.java
│   │   │                   └── UnauthorizedException.java
│   │   │
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   │   ├── tenant-defaults.properties
│   │   │   ├── logging.properties
│   │   │   ├── prompts/
│   │   │   │   ├── assistant/default-system-prompt.txt
│   │   │   │   ├── flowgenerator/default-flow-prompt.txt
│   │   │   │   └── summary/default-summary-prompt.txt
│   │   │   └── db/
│   │   │       └── schema.sql
│   │   │
│   │   └── webapp/
│   │       └── WEB-INF/
│   │           └── web.xml
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── nexusivr/
│                   └── ai/
│                       ├── router/AiRouterServiceTest.java
│                       ├── assistant/AiAssistantServiceTest.java
│                       ├── rag/RagServiceTest.java
│                       ├── memory/ConversationMemoryServiceTest.java
│                       ├── functioncalling/FunctionExecutorTest.java
│                       ├── sentiment/SentimentAnalysisServiceTest.java
│                       ├── flowgenerator/FlowGeneratorServiceTest.java
│                       ├── validation/FlowStructureValidatorTest.java
│                       ├── analytics/MetricsAggregatorTest.java
│                       ├── improve/ImprovementSuggestionGeneratorTest.java
│                       ├── tenant/TenantContextHolderTest.java
│                       └── ... (one test package mirroring each main package)
│
└── docs/
    └── architecture/
        └── NexusIVR-AI-Backend-Architecture.md   (prior high-level design doc)
```

---

## 11. Notes on Applying This Structure

- **34 packages is intentional, not excessive** — each earns its place by owning exactly one concern; the temptation in a Core-Java project without framework guardrails is to let packages blur together (e.g., putting SQL inside a service class "just this once"). The dependency rules in §9 are what prevent that drift over time — treat them as enforced conventions (a package-cycle checker in the build, e.g. ArchUnit-style rules written in plain JUnit, is worth adding even without Spring).
- **`validation` vs `validator` is the one pairing worth double-checking in code review** — `validation` judges AI-*generated* content against business/quality rules; `validator` checks that an incoming *request* is well-formed. Mixing these up is the most common naming confusion in this structure.
- **This structure assumes a single deployable WAR/JAR.** If/when any capability module needs to scale independently (as discussed in the earlier Clean Architecture document), the package boundaries here already mark the exact cut lines — each top-level capability package becomes its own module with minimal rework, because inter-package calls already go through service interfaces, not concrete classes.
