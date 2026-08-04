# NexusIVR AI Module — Java Model Classes (MVP)

Plain Java (`com.nexusivr.ai.model`), matching the MVP schema 1:1. No Hibernate, no JPA, no Lombok — every class has hand-written fields, a no-arg constructor, an all-args constructor, getters/setters, `toString()`, `equals()`, and `hashCode()`, consistent with the Core-Java-only constraint from the package structure doc (`mapper` package hand-maps these to/from `dto`/DB rows; `dao`/`repository` are intentionally not generated here, per your request).

All 13 files live under `com/nexusivr/ai/model/` — 7 model classes + 6 supporting enums.

---

## 1. Design Decisions Applied to Every Class

- **Column → field mapping is literal.** `UUID` for every id/FK, `java.time.Instant` for `TIMESTAMPTZ`, `Map<String,Object>` for `JSONB`, `float[]` for the pgvector column. No type is invented that the database doesn't already imply.
- **CHECK constraints → enums, not Strings.** `channel`, `status`, `role`, `source_type`, `module` were all `VARCHAR + CHECK` in SQL because native Postgres `ENUM` is costly to alter; in Java there's no such migration cost, so each becomes a real `enum` (`Channel`, `SessionStatus`, `MessageRole`, `DocumentStatus`, `SourceType`, `PromptModule`). This catches invalid values at compile time instead of only at insert time.
- **Foreign keys → plain `UUID` fields, not embedded objects.** e.g. `AiMessage.sessionId` is a `UUID`, not an `AiSession sessionId` reference. These are plain data holders with no persistence context behind them, so there is nothing to lazily resolve a parent object through — embedding the parent would either require eager-loading every relationship on every read, or a fake proxy, both of which reintroduce ORM-like behavior through the back door. The relationship is exactly as real as it is in the row: an id.
- **`equals()`/`hashCode()` use every field, not just `id`.** This is a deliberate departure from typical JPA-entity convention (which often uses id-only equality to stay stable across lazy-loading proxies). These classes have no proxies and no persistence context, so they behave as **value objects**: two instances are equal when their data is equal. This makes them safe to use in `Set`s/as `Map` keys for deduplication and testing without surprises.
- **Nullable vs. primitive fields reflect nullable vs. `NOT NULL` columns.** `AiMessage.turnNumber` is `int` (`NOT NULL` in SQL); `AiMessage.tokensInput` is `Integer` (nullable in SQL). Same pattern throughout.
- **Defensive defaults in constructors.** `metadata`/`variables` (`JSONB` columns) default to an empty `HashMap` rather than `null`, so callers never need a null-check before reading them.

---

## 2. The Classes

### 2.1 `AiSession`
Maps to `ai_sessions` — the root aggregate of the conversational graph. Holds `channel` (`Channel` enum), `status` (`SessionStatus` enum), the session window (`startedAt`/`endedAt`), and free-form `metadata`. Every other model in this set either belongs to a session directly (`AiMessage`) or optionally references one (`ConversationHistory`).

### 2.2 `AiMessage`
Maps to `ai_messages` — one turn of a transcript. Carries `sessionId` (FK to `AiSession`), `turnNumber`, `role` (`MessageRole` enum), the turn's `content`, and lightweight cost-tracking fields (`modelUsed`, `tokensInput`, `tokensOutput`). `tenantId` is duplicated here even though it's derivable via `sessionId` — this mirrors the intentional denormalization in the SQL schema, done so tenant-scoped queries never require a join.

### 2.3 `ConversationHistory`
Maps to `conversation_history` — distilled, cross-session memory per customer. `sessionId` is nullable (an `AiSession` may be deleted while the memory it produced is retained), which is why the field is a plain `UUID` object rather than a primitive — there's no primitive `null` in Java, so nullable ids are always boxed/object types in this model set.

### 2.4 `KnowledgeDocument`
Maps to `knowledge_documents` — the ingestion registry for RAG source material, before chunking. Tracks `sourceType` (`SourceType` enum), `status` (`DocumentStatus` enum) through the ingestion pipeline, and `version`/`checksum` for re-ingestion and dedup logic.

### 2.5 `KnowledgeChunk`
Maps to `knowledge_chunks` — the actual retrieval unit for RAG. `documentId` is the FK back to `KnowledgeDocument`; `chunkIndex` preserves ordering within the source document.

### 2.6 `Embedding`
Maps to `embeddings` — the vector representation of exactly one chunk. `chunkId` is the FK to `KnowledgeChunk` (1:1 in the schema). `embedding` is a `float[]`, chosen because it maps directly to a pgvector column with no dependency on any pgvector/JDBC driver type — keeping this class genuinely persistence-agnostic. Because `float[]` doesn't have value-based `equals()`/`hashCode()` by default, this class explicitly uses `Arrays.equals()`/`Arrays.hashCode()` rather than `Objects.equals()` for that one field — the only class in the set that needs this. `toString()` also deliberately prints `float[1536]` rather than dumping 1536 numbers, to keep logs readable.

### 2.7 `PromptTemplate`
Maps to `prompt_templates` — a versioned, tenant-or-global prompt asset. `tenantId` is nullable (`null` = global default template), and `module` is a `PromptModule` enum. This class has **no relationship fields** to any other model — matching the schema, where `prompt_templates` deliberately carries no FK into the session graph.

---

## 3. Relationships Between Models

```mermaid
classDiagram
    class AiSession {
        +UUID id
        +UUID tenantId
        +Channel channel
        +SessionStatus status
    }
    class AiMessage {
        +UUID id
        +UUID sessionId
        +MessageRole role
    }
    class ConversationHistory {
        +UUID id
        +UUID sessionId
        +String customerIdentifier
    }
    class KnowledgeDocument {
        +UUID id
        +SourceType sourceType
        +DocumentStatus status
    }
    class KnowledgeChunk {
        +UUID id
        +UUID documentId
        +int chunkIndex
    }
    class Embedding {
        +UUID id
        +UUID chunkId
        +float[] embedding
    }
    class PromptTemplate {
        +UUID id
        +PromptModule module
    }

    AiSession "1" --> "0..*" AiMessage : sessionId
    AiSession "1" --> "0..*" ConversationHistory : sessionId (nullable)
    KnowledgeDocument "1" --> "0..*" KnowledgeChunk : documentId
    KnowledgeChunk "1" --> "0..1" Embedding : chunkId
    PromptTemplate ..> "no relationship" : standalone
```

| From | To | Field carrying the relationship | Cardinality | Notes |
|---|---|---|---|---|
| `AiSession` | `AiMessage` | `AiMessage.sessionId` | 1 : N | Every message belongs to exactly one session. |
| `AiSession` | `ConversationHistory` | `ConversationHistory.sessionId` | 1 : N (optional) | `sessionId` may be `null` — memory can outlive its source session. |
| `KnowledgeDocument` | `KnowledgeChunk` | `KnowledgeChunk.documentId` | 1 : N | A document is split into ordered chunks. |
| `KnowledgeChunk` | `Embedding` | `Embedding.chunkId` | 1 : 1 | Enforced at the DB level, not in Java — nothing in these classes prevents constructing two `Embedding`s with the same `chunkId`; that invariant belongs to the repository/service layer, not the model. |
| `PromptTemplate` | — | — | none | Intentionally standalone, same as in the SQL design. |

**Why relationships are expressed as raw `UUID` fields instead of object graphs:** without JPA/Hibernate there is no lazy-loading, no persistence context, and no session cache — so an embedded `AiSession session` field on `AiMessage` would have to be either always fully populated (expensive, and a decision that belongs to the repository layer, not the model) or `null` until someone manually wires it up (fragile, and easy to NPE on). Keeping relationships as ids keeps these classes honest about what they actually are: an in-memory mirror of one database row, nothing more. If you want a "session with its messages loaded" view for a specific use case, that's a job for a small aggregate/service-layer class assembled explicitly — not something these model classes should hide.

---

## 4. Files

```
com/nexusivr/ai/model/
├── Channel.java             (enum)
├── SessionStatus.java       (enum)
├── MessageRole.java         (enum)
├── SourceType.java          (enum)
├── DocumentStatus.java      (enum)
├── PromptModule.java        (enum)
├── AiSession.java
├── AiMessage.java
├── ConversationHistory.java
├── KnowledgeDocument.java
├── KnowledgeChunk.java
├── Embedding.java
└── PromptTemplate.java
```
