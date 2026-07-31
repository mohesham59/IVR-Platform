# NexusIVR AI Module — Service Layer

Pure Java (`com.nexusivr.ai.service`), no Spring/JPA/Lombok/annotations —
same Core-Java-only convention as the model and DTO layers.

## 0. What "pure DTOs/service layer only" means here

Per the request, this deliverable contains **no business logic**. Concretely:

- `com.nexusivr.ai.dao.*` and `com.nexusivr.ai.provider.*` are **contracts
  only** — interfaces plus small immutable `Result` carrier classes. Their
  concrete implementations (JDBC/SQL against Postgres for DAOs; LLM API
  calls, rule engines, scoring models for Providers) are **out of scope** —
  that is precisely where the business logic and persistence logic live,
  and writing it would violate "do not implement business logic."
- Every `*ServiceImpl` is fully implemented: constructor-injected
  dependencies, validation call, transaction demarcation, DAO/Provider
  calls, exception translation, and response assembly. This is
  orchestration/plumbing, not business logic — it never decides *what* the
  right answer is, only *how* to move data between layers safely.
- A handful of field-by-field mapping method bodies (model → DTO copies)
  are left as a one-line comment instead of invented `getX()/setX()` calls,
  since the exact model class getters weren't part of the supplied DTO doc.
  These are trivial, deterministic copies — not decisions — so leaving them
  as a comment doesn't hide any logic, it just avoids fabricating APIs.

## 1. Package layout

```
com/nexusivr/ai/
├── service/            10 interfaces — one per module
│   └── impl/           10 implementations
├── service/exception/  ServiceException + 4 subtypes
├── service/support/    TransactionManager, TransactionalWork, Validator<T>
├── dao/                3 DAO interfaces (table-backed modules only)
└── provider/           9 Provider interfaces (one per non-table-backed
                         capability, or the capability inside a
                         table-backed module that isn't persistence)
```

## 2. Hard rules this design follows

- **Services never touch a Servlet/HTTP object.** Request/response DTOs are
  the only things that cross the service boundary; whatever sits above
  (a controller) owns HTTP concerns.
- **Services only call DAO and Provider interfaces**, never a raw
  JDBC `Connection`, an HTTP client, or another service's implementation
  class directly. Cross-module composition (e.g. "run Analytics data
  through Flow Improvement") happens by a caller invoking two services in
  sequence, not by one `*ServiceImpl` depending on another.
- **One Validator per request DTO**, injected rather than hand-rolled
  inline, so validation rules are unit-testable independent of
  orchestration.
- **One exception family.** Every checked/unchecked exception native to a
  DAO or Provider is caught at the point of call and re-thrown as a
  `ServiceException` subtype. Nothing else is allowed to escape a service
  method.

## 3. Exception types (`service/exception/`)

| Type | Thrown when | Carries |
|---|---|---|
| `ServiceException` | base type; never thrown directly | `errorCode` |
| `ValidationException` | request fails shape validation, before any DAO/Provider call | list of violation strings |
| `ResourceNotFoundException` | a referenced id (sessionId, etc.) isn't found by a DAO | resource type + id |
| `ProviderException` | a Provider call throws | provider name + cause |
| `DataAccessException` | a DAO call throws | DAO name + cause |

**Exception flow, uniformly across every service:**
`validate → (throws ValidationException)` → DAO/Provider call wrapped in
`try/catch` → native exception translated to `DataAccessException` /
`ProviderException` / `ResourceNotFoundException` → response assembled →
returned. No service method declares `throws Exception`; all ten interface
methods declare `throws ServiceException` only.

## 4. Transaction boundaries (`service/support/TransactionManager`)

`TransactionManager` is a plain interface (`executeInTransaction` /
`executeReadOnly`) so the service layer stays persistence-technology
agnostic — no JPA/Spring annotations anywhere.

**Rule applied everywhere:** a Provider call (LLM, rule engine, scoring
model) is a slow external call and is **never** enrolled inside a
transaction. A transaction only ever wraps DAO calls.

| Service | Uses TransactionManager? | Boundary |
|---|---|---|
| Chat | Yes | Txn 1: resolve/create session + load transcript. *(Provider call, outside any txn)* Txn 2: persist the new turn. |
| Flow Generator | No | stateless, single Provider call |
| Flow Improvement | No | stateless, single Provider call |
| Validation | No | stateless, single Provider call |
| Router | No | stateless, single Provider call |
| Sentiment | Yes (`executeReadOnly`) | one read-only txn to load transcript, only for SESSION scope |
| Analytics | No (service level) | no direct DAO call in the service; DAO-backed raw reads happen behind the Provider boundary |
| Function Calling | No | stateless, single Provider call |
| Summarization | Yes | read-only txn to load transcript, *(Provider call, outside any txn)*, write txn to persist the summary |
| Conversation | Yes (`executeReadOnly`) | one read-only txn wrapping the page read + count read so they're mutually consistent |

## 5. Validation flow (uniform pattern)

Every service's first line is `validator.validate(request)`. The
`Validator<T>` checks **shape only**:

- Required fields present (e.g. `description` for Flow Generator,
  `sessionId`+`customerIdentifier` for Summarization).
- Mutually-dependent fields (e.g. `ChatRequest.sessionId == null` implies
  `channel` is required).
- Enum-driven required fields (e.g. `SentimentAnalysisRequest.scope ==
  MESSAGE` requires `text`; `== SESSION` requires `sessionId`).
- Paging bounds sane (`page >= 0`, `size` within an allowed range).

It never checks a business rule (e.g. it doesn't decide whether a flow is
*good*, whether a route is *correct*, or whether a summary is *accurate* —
that's what the Provider/rule-engine implementation is for).

## 6. Per-service reference

### 6.1 ChatService
- **Table-backed:** yes (`ai_sessions`, `ai_messages`).
- **Dependencies:** `SessionDao`, `MessageDao`, `LlmProvider`,
  `TransactionManager`, `Validator<ChatRequest>`.
- **Responsibility:** the only operation that spans two tables in one
  call — resolve/open a session, load its transcript, get the assistant's
  turn from the LLM, persist the new turn, return `sessionId` + output.
- **Notably not decided here:** whether to compute the optional inline
  `sentiment` field, and exactly which messages constitute "the new turn"
  (user message only? tool-result messages too?) — both are business
  rules for the Provider/DAO implementations, not the orchestration layer.

### 6.2 FlowGenerationService
- **Table-backed:** no.
- **Dependencies:** `FlowGenerationProvider`, `Validator<FlowGenerationRequest>`.
- **Responsibility:** validate → single Provider call → map `generatedFlow`
  + `warnings` into the response. `flowId` inside the generated `FlowDto`
  stays whatever the Provider set it to (null, per the DTO doc, until v2
  persistence exists).

### 6.3 FlowImprovementService
- **Table-backed:** no.
- **Dependencies:** `FlowImprovementProvider`, `Validator<FlowImprovementRequest>`.
- **Responsibility:** validate → single Provider call with the caller's
  full `FlowDto` + goals + optional analytics context → map `improvedFlow`
  + `changeLog` into the response.

### 6.4 FlowValidationService
- **Table-backed:** no.
- **Dependencies:** `FlowValidationProvider`, `Validator<FlowValidationRequest>`.
- **Responsibility:** validate → single Provider call → map the issue list
  into the response, deriving the `valid` convenience flag as "no issue has
  severity ERROR." This derivation is deterministic data shaping (not a
  business rule) since the doc itself defines `valid` this way.

### 6.5 RouterService
- **Table-backed:** no, and never was even in the full 12-table design.
- **Dependencies:** `RoutingDecisionProvider`, `Validator<RouterRequest>`.
- **Responsibility:** validate → single Provider call → map chosen route +
  every alternative's score into the response, so the caller can implement
  fallback/clarification without a second round trip.

### 6.6 SentimentAnalysisService
- **Table-backed:** no (module has no table), but reads `ai_messages` for
  SESSION scope.
- **Dependencies:** `MessageDao`, `SentimentAnalysisProvider`,
  `TransactionManager`, `Validator<SentimentAnalysisRequest>`.
- **Responsibility:** branch on `scope`. MESSAGE → score `text` directly.
  SESSION → read-only-transaction load of the transcript, then score it in
  transcript order. Leaves `overall` unset for SESSION scope since
  deriving one score from many is a business decision, not plumbing.

### 6.7 AnalyticsService
- **Table-backed:** no analytics table exists yet (deferred to v2).
- **Dependencies:** `AnalyticsAggregationProvider`,
  `Validator<AnalyticsQueryRequest>`.
- **Responsibility:** validate → single Provider call → map `metrics` (and
  `breakdown`, when `groupBy` was supplied) into the response. The raw
  `ai_sessions`/`ai_messages` reads this aggregation is ultimately based on
  happen behind the Provider boundary, so the service itself has no direct
  DAO dependency — keeping to "services only talk to DAO and Providers"
  without also making this service reach past the Provider abstraction.

### 6.8 FunctionCallingService
- **Table-backed:** no, and unlike Chat's inline function-calling support,
  never opens or continues a session.
- **Dependencies:** `FunctionCallingProvider`, `Validator<FunctionCallRequest>`.
- **Responsibility:** validate → single Provider call → map `functionCalls`
  + optional `assistantMessage` into the response.

### 6.9 SummarizationService
- **Table-backed:** yes for reads (`ai_messages`) and, unless preview-only,
  for the write (`conversation_history`).
- **Dependencies:** `MessageDao`, `ConversationHistoryDao`,
  `SummarizationProvider`, `TransactionManager`, `Validator<SummarizationRequest>`.
- **Responsibility:** read-only-transaction load of the transcript →
  Provider call (outside any transaction) → write-transaction persist of
  the summary as a `conversation_history` row, unless the request is
  preview-only, in which case `conversationHistoryId` stays null — matching
  the DTO doc's "summary generated" vs. "summary persisted" distinction.

### 6.10 ConversationHistoryService
- **Table-backed:** yes, purely (`conversation_history`); no Provider at
  all — nothing here is computed or generated, only paged through.
- **Dependencies:** `ConversationHistoryDao`, `TransactionManager`,
  `Validator<ConversationHistoryRequest>`.
- **Responsibility:** validate → one read-only transaction wrapping both
  the page read and the count read (so they can't disagree with each
  other) → wrap into the module's `ConversationHistoryResponse`, itself a
  thin named wrapper around `PageResponse<ConversationHistoryEntryDto>`.

## 7. Business-logic separation, summarized

| Lives in the Service (written here) | Lives in DAO/Provider impl (NOT written here) |
|---|---|
| Which DTO fields are required/well-formed | Whether a flow is well-designed |
| Which DAO/Provider to call, and in what order | How an LLM generates/improves/summarizes text |
| Transaction boundaries around DAO calls | How sentiment is scored, or a route is decided |
| Translating native exceptions into `ServiceException` subtypes | How analytics metrics are computed/aggregated |
| Copying fields between model/provider-result objects and DTOs | Any rule that decides *correctness*, *quality*, or *meaning* |
