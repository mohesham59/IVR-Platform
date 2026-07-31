# NexusIVR AI Module — DTOs (MVP)

Plain Java (`com.nexusivr.ai.dto`), no Lombok/Jackson/JPA annotations — same
Core-Java-only convention as `com.nexusivr.ai.model`. Every class has a
no-arg constructor (with defensive empty-collection defaults, same pattern
as the model layer), an all-args constructor, getters/setters, `toString()`,
and `equals()`/`hashCode()` over every field.

**39 files** across three packages:

```
com/nexusivr/ai/dto/
├── common/    19 files — shared building blocks, reused across modules
├── request/   10 files — one per module's inbound payload
└── response/  10 files — one per module's outbound payload
```

## Why three packages, and how they relate to the model layer

The model layer (`com.nexusivr.ai.model`) mirrors seven Postgres tables
1:1. This DTO layer is the API contract on top of it, and the two
diverge in an important way: **only Chat, Summarization, and Conversation
operate on data that has a table behind it** (`ai_sessions`, `ai_messages`,
`conversation_history`). Flow Generator, Flow Improvement, Validation,
Router, Sentiment, Analytics, and Function Calling are all real modules
with no corresponding table in the MVP schema — the database design doc
explicitly defers `flow_generations`, `intent_logs`, `function_logs`, and
`analytics` to v2, and sentiment/router/function-calling were never table-
backed even in the full 12-table design (they're computed, not stored).

That split drives most of the design decisions below:

- Where a table exists, request/response DTOs stay close to the model
  shape and reuse the model layer's enums directly (`Channel`,
  `MessageRole`) rather than duplicating them — those enums are shared
  vocabulary, not persistence-specific detail.
- Where no table exists yet, DTOs lean on `Map<String,Object>` for
  open-ended data (analytics metrics, flow metadata, validation
  constraints) instead of inventing a rigid shape today that would just
  get thrown away once a v2 schema exists to constrain it properly.
- Ids that would normally be a DB-generated `UUID` (a flow's id, a flow
  node's id) are either nullable `UUID` (populated only once something
  is actually persisted) or a plain `String` (client/generator-assigned,
  for graph structures — flows — that don't have a row to point to at all
  in this schema version).

## common/ — shared building blocks

These exist so two different modules never independently reinvent the
same concept with slightly different fields.

| File | Purpose |
|---|---|
| `SentimentLabel.java` (enum) | POSITIVE / NEUTRAL / NEGATIVE / MIXED — the categorical read alongside a continuous score. |
| `FlowNodeType.java` (enum) | The kind of step a flow node represents (MENU, PROMPT, COLLECT_INPUT, CONDITION, FUNCTION_CALL, TRANSFER, HANGUP). |
| `ValidationSeverity.java` (enum) | ERROR / WARNING / INFO — lets a caller decide what blocks publishing vs. what's advisory. |
| `AnalysisScope.java` (enum) | MESSAGE vs. SESSION — tells Sentiment whether to score raw text or an entire transcript. |
| `SummaryType.java` (enum) | SESSION vs. ROLLING — tells Summarization whether this is a one-off summary or folding into existing cross-session memory. |
| `PageRequest.java` | Generic page/size/sort input, reused by every list-style request instead of each feature declaring its own paging fields. |
| `PageResponse<T>.java` | Generic paginated result envelope, generic over content type so every paginated response looks identical to a client. |
| `ErrorResponse.java` | Uniform error envelope (code, message, details, timestamp) for every endpoint across all ten modules. |
| `TokenUsageDto.java` | modelUsed/tokensInput/tokensOutput for one LLM call — used far more broadly than just Chat, since Flow Generator, Router, and Summarization all call an LLM without ever writing to `ai_messages`. |
| `MessageDto.java` | API view of one transcript turn. Reuses `model.MessageRole` directly; omits sessionId/tenantId since it's always nested inside a response that already carries those. |
| `SentimentScoreDto.java` | One sentiment reading (label + continuous score + confidence) — the atomic unit both Sentiment and Analytics return. |
| `FlowNodeDto.java` | One node in a flow graph. Uses a String id (not UUID) because generated/in-progress flows have no DB row to key off of in this schema version. |
| `FlowEdgeDto.java` | One directed transition between two flow nodes; separates the machine-evaluated `condition` from the human-readable `label`. |
| `FlowDto.java` | A full flow graph (nodes + edges + metadata). `flowId` is a nullable UUID — null until/unless a v2 persistence layer assigns one. |
| `ValidationIssueDto.java` | One validation finding: severity, code, message, and an optional pointer to the specific node/edge it's about. |
| `FunctionDefinitionDto.java` | Describes one callable tool (name, description, JSON-Schema-shaped parameters) — the *inbound* half of function calling. |
| `FunctionCallDto.java` | One concrete, filled-in function invocation chosen by the model — the *outbound* half. |
| `AnalyticsMetricDto.java` | One named metric (name/value/unit). Generic on purpose — there's no `analytics` table yet to pin a fixed report shape to. |
| `ConversationHistoryEntryDto.java` | API view of one `conversation_history` row; `sessionId` stays nullable, mirroring the model class, since the source session can be deleted (`ON DELETE SET NULL`) while the memory survives. |

## request/ — one inbound DTO per module

| File | Module | Notes |
|---|---|---|
| `ChatRequest.java` | Chat | `sessionId` nullable = start a new session (then `channel` is required); non-null = continue an existing one. Carries optional `availableFunctions` for inline tool use. |
| `FlowGenerationRequest.java` | Flow Generator | Natural-language `description` plus optional constraints (language, maxDepth, requiredIntents) — generates from scratch, no existing-flow input. |
| `FlowImprovementRequest.java` | Flow Improvement | Takes a full `FlowDto` plus `improvementGoals`; optional `analyticsContext` lets the improvement be evidence-driven. |
| `FlowValidationRequest.java` | Validation | Takes a `FlowDto`; optional `rulesetsToApply` lets a caller run a cheap partial check instead of the full suite. |
| `RouterRequest.java` | Router | Current node, the customer's utterance, and the explicit candidate set (`availableRouteIds`) to choose from — a pure decision function over caller-supplied state. |
| `SentimentAnalysisRequest.java` | Sentiment | `scope` selects whether `text` (ad hoc) or `sessionId` (full transcript) drives the analysis. |
| `AnalyticsQueryRequest.java` | Analytics | Ad hoc query shape (date range, channel filter, `groupBy` dimensions, optional pagination) rather than fixed report types — there's no table to pin a report shape to. |
| `FunctionCallRequest.java` | Function Calling | Standalone tool-selection request, for callers who want function selection as an isolated capability outside a full chat turn. |
| `SummarizationRequest.java` | Summarization | Requires `sessionId` (what to summarize) and `customerIdentifier` (how the resulting memory gets found again later), plus `summaryType` and an optional length cap. |
| `ConversationHistoryRequest.java` | Conversation | tenantId + customerIdentifier + pagination — maps directly onto the `(tenant_id, customer_identifier, created_at DESC)` index defined in the DB design doc. |

## response/ — one outbound DTO per module

| File | Module | Notes |
|---|---|---|
| `ChatResponse.java` | Chat | Always returns `sessionId` (even if the request's was null, so the caller learns the new id). `functionCalls` populated instead of/alongside `assistantMessage` when the model calls a tool. Optional inline `sentiment` avoids a second round-trip for the common "is this user upset" check. |
| `FlowGenerationResponse.java` | Flow Generator | `generatedFlow.flowId` is null (nothing persisted yet); `warnings` are soft generator observations, distinct from Validation's stricter `ValidationIssueDto`s. |
| `FlowImprovementResponse.java` | Flow Improvement | `changeLog` is human-readable (for a review UI); `improvedFlow` reuses input node/edge ids wherever a node was kept, so callers can still diff structurally if needed. |
| `FlowValidationResponse.java` | Validation | `valid` is a derived convenience flag — true iff no issue has severity ERROR — so callers don't have to scan the list themselves. |
| `RouterResponse.java` | Router | Returns the chosen route plus every alternative's score, so callers can implement their own fallback/clarification logic without a second call. |
| `SentimentAnalysisResponse.java` | Sentiment | `overall` is always populated; `perMessageScores` only for SESSION scope, in transcript order, so sentiment drift across a call can be plotted. |
| `AnalyticsResponse.java` | Analytics | `metrics` (always present) plus an optional paginated `breakdown` for when `groupBy` was specified — rows are generic `Map`s for the same reason `AnalyticsMetricDto` is generic. |
| `FunctionCallResponse.java` | Function Calling | `functionCalls` plus an optional `assistantMessage`, since a model can talk and call a tool in the same turn. |
| `SummarizationResponse.java` | Summarization | `conversationHistoryId` nullable — separates "summary generated" from "summary persisted," so a preview-before-save flow is possible without a fake id. |
| `ConversationHistoryResponse.java` | Conversation | Thin named wrapper around `PageResponse<ConversationHistoryEntryDto>`, kept as its own class so the module's return type is independently versionable rather than a raw generic on the controller signature. |

## What's deliberately not here

No `dao`/`repository`/`service` classes, per the request — this is DTOs
only. No mapper classes either (the model docs mention a `mapper` package
that hand-maps model ⇄ DTO ⇄ DB rows; that package would consume these
DTOs but isn't part of this deliverable). No DTOs for `KnowledgeDocument`,
`KnowledgeChunk`, `Embedding`, or `PromptTemplate` — those back the RAG /
prompt-authoring surface, which wasn't in the requested list of ten modules.
