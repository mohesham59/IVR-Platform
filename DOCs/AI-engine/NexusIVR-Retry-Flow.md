# NexusIVR AI Engine — Retry Flow Documentation

## Overview

The AI Engine uses a two-layer retry architecture that strictly separates **infrastructure errors** (retryable) from **validation/content errors** (never retried). This prevents wasting API quota on LLM calls that will inevitably fail again for the same content-level reason.

---

## Layer 1: ProviderManager Retry (Infrastructure Errors Only)

`ProviderManager.executeWithRetryAndFallback()` handles retries at the HTTP/network level.

### Retryable Errors (per `RetryPolicy`)

| Condition | Classification | Action |
|-----------|---------------|--------|
| HTTP 429 | Rate limit | Retry with exponential backoff |
| HTTP 500+ | Server error | Retry with exponential backoff |
| `HttpTimeoutException` | Network timeout | Retry with exponential backoff |
| `ConnectException` | Connection refused / unreachable | Retry with exponential backoff |
| `IOException` | Network I/O failure | Retry with exponential backoff |
| `InterruptedException` | Thread interrupted | Retry with exponential backoff |
| Status code 0 + content matches network patterns | Network error | Retry with exponential backoff |

### Non-Retryable Errors (fail fast)

| Condition | Classification | Action |
|-----------|---------------|--------|
| HTTP 400 | Bad request | Fail fast, try next provider |
| HTTP 401 | Unauthorized | Fail fast, try next provider |
| HTTP 403 | Forbidden | Fail fast, try next provider |
| HTTP 404 | Not found | Fail fast, try next provider |
| HTTP 422 | Unprocessable entity | Fail fast, try next provider |
| Any other 4xx | Client error | Fail fast, try next provider |
| Content-level errors | Invalid XML, JSON, schema, etc. | Fail fast, try next provider |

### Retry Policy Details

- **Max retries per provider**: 3
- **Backoff strategy**: Exponential — 500ms, 1s, 2s, capped at 30s
- **Provider fallback**: When a provider exhausts retries, the next provider in priority order is tried
- **Final fallback**: When all providers are exhausted, `TemplateGenerator` produces a deterministic domain-specific VoiceXML flow

---

## Layer 2: UnifiedAiEngine Validation & Repair (Never Retry LLM)

`UnifiedAiEngine.generateFlow()` uses a **single-pass pipeline** for flow generation. Validation failures are handled locally — the LLM is never called again for a validation failure.

### Pipeline (Single Pass)

```
User Prompt
    │
    ▼
ProviderManager.executeWithRetryAndFallback()  ← Infrastructure retries only (429/500+/timeout/connection)
    │
    ▼
Raw VoiceXML from LLM
    │
    ▼
VxmlValidator.validate()  ──┐
    │                       │
    ▼                       ▼
Valid VXML?          Invalid VXML?
    │                       │
    ▼                       ▼
Continue            attemptVxmlRepair()  ← Local repair, no LLM call
    │                       │
    │               ┌───────┴───────┐
    │               │               │
    │               ▼               ▼
    │         Repaired?       Unrepairable
    │               │               │
    │               ▼               ▼
    │         Continue          Use domain fallback (TemplateGenerator)
    │
    ▼
VxmlToModelConverter.convert()  ──┐
    │                              │
    ▼                              ▼
Model parsed?              Parse failure
    │                              │
    ▼                              ▼
Continue               Use domain fallback (TemplateGenerator)
    │
    ▼
ModelFlowValidator.validate()  ──┐
    │                              │
    ▼                              ▼
Valid model?             Invalid model?
    │                              │
    ▼                              ▼
Render to JSON           ModelAutoRepair.repair()  ← Local repair, no LLM call
                               │
                               ▼
                          Repaired model valid?
                               │
                          ┌────┴────┐
                          │         │
                          ▼         ▼
                        Yes        No
                          │         │
                          ▼         ▼
                        Render   Use domain fallback (TemplateGenerator)
                        to JSON
```

### Key Rules

1. **The LLM is called exactly once** per flow generation request (via `ProviderManager.executeWithRetryAndFallback()`).
2. **Infrastructure retries** (429, 500+, timeout, connection) are handled by `ProviderManager` before the response reaches `UnifiedAiEngine`.
3. **Validation failures** (invalid VXML, invalid model structure, missing nodes/edges, unsupported types) are repaired locally using `ModelAutoRepair`.
4. **If local repair fails**, the pipeline falls through to `TemplateGenerator` (domain-specific fallback) — the LLM is never called again.
5. **`improveFlow()`** follows the same pattern: single LLM call, then local validate+repair.

---

## RetryPolicy Class

`RetryPolicy` is the single source of truth for retry classification. It replaces the previous string-based `isRetryableError()` and `isRateLimitError()` methods in `ProviderManager`.

### API

```java
// Check if an HTTP response is retryable
RetryPolicy.isRetryable(int statusCode, String content)

// Check if an AiResponse is retryable (uses status code + content)
RetryPolicy.isRetryable(AiResponse response)

// Check if an exception is retryable (timeout, connection, I/O, interrupt)
RetryPolicy.isRetryable(Exception e)
```

### Classification Logic

| Input | Retryable? | Reason |
|-------|-----------|--------|
| `statusCode == 429` | Yes | Rate limit |
| `statusCode >= 500` | Yes | Server error |
| `statusCode == 0` + content contains "timeout" | Yes | Network timeout |
| `statusCode == 0` + content contains "connectexception" | Yes | Connection error |
| `statusCode == 0` + content contains "unreachable" | Yes | Server offline |
| `statusCode == 0` + content contains "network error" | Yes | Network failure |
| `statusCode == 0` + content contains "connection refused" | Yes | Connection refused |
| `statusCode == 0` + content contains "broken pipe" | Yes | Broken pipe |
| `statusCode == 400` | No | Bad request |
| `statusCode == 401` | No | Unauthorized |
| `statusCode == 403` | No | Forbidden |
| `statusCode == 404` | No | Not found |
| `statusCode == 422` | No | Unprocessable entity |
| Any other 4xx | No | Client error |
| Content contains "Invalid XML" | No | Content error |
| Content contains "Invalid JSON" | No | Content error |
| Content contains "Unsupported node type" | No | Content error |
| Content contains "Missing node" | No | Content error |
| Content contains "Invalid flow structure" | No | Content error |

---

## HTTP Status Code Propagation

All LLM client implementations now propagate HTTP status codes through `AiResponse.statusCode`:

| Client | Status Code Setting |
|--------|-------------------|
| `GeminiClient` | 401, 429, ≥400 from HTTP responses; 0 for network exceptions |
| `GroqClient` | 401, 429, ≥400 from HTTP responses; 0 for network exceptions |
| `OpenAiCompatibleClient` | ≥400 from HTTP responses; 0 for network exceptions |
| `OllamaClient` | 404, ≥400 from HTTP responses; 0 for network exceptions |
| `TemplateGenerator` | N/A (no HTTP call) |
| `MockLlmClient` | N/A (no HTTP call) |

A `statusCode` of `0` indicates no HTTP context (e.g., network-level error before receiving an HTTP response).

---

## Migration Notes

- `ProviderManager.isRetryableError()` and `ProviderManager.isRateLimitError()` have been removed. Use `RetryPolicy` instead.
- `UnifiedAiEngine.generateFlow()` no longer has a multi-attempt retry loop that re-calls the LLM on validation failures.
- `AiResponse` now includes a `statusCode` field (default 0 for backward compatibility with existing constructors).