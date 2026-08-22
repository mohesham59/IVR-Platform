package com.nexusivr.ai.ai;

import com.nexusivr.ai.ai.optimization.QuotaAwareRouter;
import com.nexusivr.ai.ai.optimization.TokenUsageTracker;
import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.dto.response.QuotaWarning;
import com.nexusivr.ai.service.exception.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and manager for all LLM providers and their supported models.
 * <p>
 * Centralises provider selection, health tracking, exponential backoff,
 * and retry routing. When all configured providers exhaust retries,
 * a {@link ProviderException} is thrown instead of silently falling back
 * to a generic template.
 * <p>
 * Priority chain when all configured providers are healthy:
 * <ol>
 *   <li>Groq</li>
 *   <li>Gemini</li>
 * </ol>
 * <p>
 * Retry rules (see {@link RetryPolicy}):
 * <ul>
 *   <li>Retryable: HTTP 429, HTTP 500+, network timeout, connection error</li>
 *   <li>Non-retryable: all other errors (400, 401, 403, 404, content-level errors)</li>
 * </ul>
 * <p>
 * On total exhaustion: throws {@link ProviderException} with a categorized
 * failure reason so the controller can translate it into an HTTP error.
 * Existing public APIs ({@link #getLlmClient}, {@link #setOverrideClient},
 * {@link #clearCache}, …) are unchanged.
 */
public class ProviderManager {

    private static final Logger logger = LoggerFactory.getLogger(ProviderManager.class);

    private static final Map<String, List<String>> PROVIDER_MODELS = new LinkedHashMap<>();
    private static final Map<String, LlmClient> clientCache = new ConcurrentHashMap<>();
    private static volatile LlmClient overrideClient = null;

    // Health tracking per provider
    private static final Map<String, ProviderHealth> healthMap = new ConcurrentHashMap<>();
    private static final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private static final QuotaAwareRouter quotaRouter = QuotaAwareRouter.getInstance();
    private static final TokenUsageTracker tokenTracker = TokenUsageTracker.getInstance();

    // Deterministic priority order for fallback routing.
    // Only real LLM providers are included; template-generator is intentionally excluded
    // so it is never silently substituted when generation fails.
    private static final List<String> PROVIDER_PRIORITY = List.of(
            "groq",
            "openrouter",
            "gemini",
            "ollama"
    );

    // Retry / backoff knobs
    private static final int MAX_RETRIES_PER_PROVIDER = 3;
    private static final long BASE_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 30_000L;

    static {
        PROVIDER_MODELS.put("groq", List.of("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama-3.1-70b", "mixtral"));
        PROVIDER_MODELS.put("gemini", List.of("gemini-2.0-flash", "gemini-1.5-flash"));
        PROVIDER_MODELS.put("ollama", List.of("granite3.2:2b", "llama3", "qwen", "mistral"));
        PROVIDER_MODELS.put("openrouter", List.of("openai.gpt-oss-20b-1:0", "openai/gpt-oss-20b"));
        PROVIDER_MODELS.put("mock", List.of("mock-model"));
        // NOTE: "openai" and "claude" providers removed — only openrouter, gemini (default) and groq (fallback) are supported.
    }

    /**
     * Returns a map of all supported providers and their models.
     */
    public Map<String, List<String>> getSupportedProvidersAndModels() {
        return Collections.unmodifiableMap(PROVIDER_MODELS);
    }

    /**
     * Resolves and returns the configured LlmClient instance.
     * When providerName is null/blank the configured default from
     * {@code LlmConfig.getProvider()} (currently "gemini") is used.
     */
    public LlmClient getLlmClient(String providerName, String modelName, double temperature, int timeoutSeconds) {
        if (overrideClient != null) {
            return overrideClient;
        }

        String provider = (providerName != null && !providerName.isBlank())
                ? providerName.toLowerCase().trim()
                : com.nexusivr.ai.config.LlmConfig.getProvider();

        List<String> models = PROVIDER_MODELS.get(provider);
        String model;
        if (modelName != null && !modelName.isBlank()) {
            if (models != null && !models.isEmpty() && models.stream().noneMatch(m -> m.equalsIgnoreCase(modelName.trim()))) {
                String configDefault = getProviderDefaultModel(provider);
                logger.warn("ProviderManager: Model '{}' is invalid for provider '{}'. Using provider default '{}'.",
                        modelName.trim(), provider, configDefault);
                model = configDefault;
            } else {
                model = modelName.trim();
            }
        } else {
            model = getProviderDefaultModel(provider);
        }

        if ("ollama".equalsIgnoreCase(provider)) {
            timeoutSeconds = Math.min(timeoutSeconds, com.nexusivr.ai.config.LlmConfig.getOllamaTimeout());
        }

        String cacheKey = String.format("%s:%s:t%.1f:to%d", provider, model, temperature, timeoutSeconds);

        final int effectiveTimeout = timeoutSeconds;
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(provider, model, temperature, effectiveTimeout));
    }

    private String getProviderDefaultModel(String provider) {
        return switch (provider) {
            case "gemini" -> com.nexusivr.ai.config.LlmConfig.getGeminiModel();
            case "groq"   -> com.nexusivr.ai.config.LlmConfig.getGroqModel();
            case "ollama"  -> com.nexusivr.ai.config.LlmConfig.getOllamaModel();
            case "openrouter" -> com.nexusivr.ai.config.LlmConfig.getOpenrouterModel();
            default -> {
                List<String> models = PROVIDER_MODELS.get(provider);
                yield (models != null && !models.isEmpty()) ? models.get(0) : "default";
            }
        };
    }

    /**
     * Returns a Groq client for use as the 429-fallback provider when Gemini
     * (or any primary provider) is rate-limited. Uses the full 70b-versatile
     * model so the fallback attempt is still high quality.
     */
    public LlmClient getGroqFallbackClient(double temperature, int timeoutSeconds) {
        String groqModel = com.nexusivr.ai.config.LlmConfig.getGroqModel();
        return getLlmClient("groq", groqModel, temperature, timeoutSeconds);
    }

    private LlmClient createClient(String provider, String model, double temperature, int timeoutSeconds) {
        logger.info("ProviderManager: Creating client for provider={}, model={}, temperature={}, timeout={}s",
                provider, model, temperature, timeoutSeconds);

        if ("template-generator".equals(provider)) {
            return new TemplateGenerator(model, null);
        }

        return switch (provider) {
            case "groq" -> new GroqAiProvider(
                    com.nexusivr.ai.config.LlmConfig.getGroqApiKey(),
                    com.nexusivr.ai.config.LlmConfig.getGroqBaseUrl(),
                    model,
                    timeoutSeconds
            );
            case "ollama" -> new OpenAiCompatibleClient(
                    "ollama",
                    "", // Ollama doesn't require API Key normally
                    com.nexusivr.ai.config.LlmConfig.getOllamaBaseUrl(),
                    model,
                    timeoutSeconds,
                    temperature
            );
            case "openrouter" -> new OpenAiCompatibleClient(
                    "openrouter",
                    com.nexusivr.ai.config.LlmConfig.getOpenrouterApiKey(),
                    com.nexusivr.ai.config.LlmConfig.getOpenrouterBaseUrl(),
                    model,
                    timeoutSeconds,
                    temperature
            );
            case "gemini" -> new GeminiClient(
                    com.nexusivr.ai.config.LlmConfig.getGeminiApiKey(),
                    com.nexusivr.ai.config.LlmConfig.getGeminiBaseUrl(),
                    model,
                    timeoutSeconds,
                    temperature
            );
            case "mock" -> new MockLlmClient(model);
            default -> {
                logger.warn("Unknown provider '{}' - returning MockLlmClient fallback.", provider);
                yield new MockLlmClient(model);
            }
        };
    }

    public void setOverrideClient(LlmClient client) {
        overrideClient = client;
    }

    public void clearCache() {
        clientCache.clear();
        overrideClient = null;
        healthMap.clear();
        circuitBreakerMap.clear();
    }

    // -----------------------------------------------------------------------
    // Health tracking
    // -----------------------------------------------------------------------

    private ProviderHealth getHealth(String provider) {
        return healthMap.computeIfAbsent(provider, p -> new ProviderHealth());
    }

    /**
     * Marks a provider as rate-limited (HTTP 429). The provider will be
     * skipped until its cooldown window expires.
     */
    public void markRateLimited(String provider) {
        long quotaCooldown = com.nexusivr.ai.config.LlmConfig.getCircuitBreakerQuotaCooldownMs();
        getHealth(provider).markUnhealthy("Quota exceeded (HTTP 429)", quotaCooldown);
        getCircuitBreaker(provider).recordFailure("Quota exceeded (HTTP 429)", 429);
        logger.warn("[ProviderManager] Provider '{}' rate-limited. Circuit breaker updated.", provider);
    }

    /**
     * Marks a provider as failed with an authentication error (HTTP 401/403).
     * The provider is immediately marked unhealthy with a 10-minute cooldown.
     */
    public void markAuthFailure(String provider, String reason) {
        getHealth(provider).markAuthFailure(reason);
        getCircuitBreaker(provider).recordFailure(reason, 401);
        logger.warn("[ProviderManager] Provider '{}' authentication failure: '{}'. Circuit breaker opened.", provider, reason);
    }

    /**
     * Marks a provider as failed with a generic error. Non-rate-limit
     * failures increment the consecutive-failure counter; if the provider
     * exceeds the threshold it will be skipped until explicitly reset.
     */
    public void markFailed(String provider, String reason) {
        getHealth(provider).markServerFailure(reason);
        getCircuitBreaker(provider).recordFailure(reason, 500);
        logger.warn("[ProviderManager] Provider '{}' failed. Reason: '{}'. Consecutive failures: {}.",
                provider, reason, getHealth(provider).getConsecutiveFailures());
    }

    /**
     * Marks a provider as healthy after a successful call.
     */
    public void markSuccess(String provider) {
        getHealth(provider).recordSuccess();
        getCircuitBreaker(provider).recordSuccess();
        logger.debug("[ProviderManager] Provider '{}' marked healthy.", provider);
    }

    /**
     * Returns true if the provider is currently available for calls.
     * Checks both ProviderHealth and CircuitBreaker state.
     */
    public boolean isProviderAvailable(String provider) {
        ProviderHealth health = getHealth(provider);
        CircuitBreaker breaker = getCircuitBreaker(provider);

        if (!breaker.isCallPermitted()) {
            logger.debug("[ProviderManager] Provider '{}' skipped — circuit breaker is OPEN.", provider);
            return false;
        }

        if (health.isUnavailable()) {
            long remaining = health.getCooldownRemainingMs();
            logger.debug("[ProviderManager] Provider '{}' skipped — unhealthy. Cooldown remaining: {}ms.",
                    provider, remaining);
            return false;
        }

        return true;
    }

    /**
     * Returns the remaining cooldown milliseconds for a provider (checking both
     * ProviderHealth and CircuitBreaker), or the maximum remaining cooldown across
     * all providers if provider is null/blank or 'all'.
     */
    public long getCooldownRemainingMs(String provider) {
        if (provider == null || provider.isBlank() || "all".equalsIgnoreCase(provider)) {
            return getMaximumCooldownRemainingMs();
        }
        ProviderHealth health = getHealth(provider);
        CircuitBreaker breaker = getCircuitBreaker(provider);
        long healthCooldown = health != null ? health.getCooldownRemainingMs() : 0;
        long breakerCooldown = breaker != null ? breaker.getCooldownRemainingMs() : 0;
        return Math.max(healthCooldown, breakerCooldown);
    }

    /**
     * Returns the maximum remaining cooldown in milliseconds across all configured providers.
     */
    public long getMaximumCooldownRemainingMs() {
        long maxCooldownMs = 0;
        for (String provider : PROVIDER_PRIORITY) {
            ProviderHealth health = getHealth(provider);
            CircuitBreaker breaker = getCircuitBreaker(provider);
            long healthCooldown = health != null ? health.getCooldownRemainingMs() : 0;
            long breakerCooldown = breaker != null ? breaker.getCooldownRemainingMs() : 0;
            long cooldownMs = Math.max(healthCooldown, breakerCooldown);
            maxCooldownMs = Math.max(maxCooldownMs, cooldownMs);
        }
        return maxCooldownMs;
    }

    /**
     * Returns the maximum cooldown remaining (in seconds) across all configured providers.
     */
    public int getMaximumCooldownSeconds() {
        long maxMs = getMaximumCooldownRemainingMs();
        return maxMs > 0 ? (int) Math.max(1, (maxMs + 999) / 1000) : 0;
    }

    /**
     * Returns the cooldown remaining (in seconds) for a provider, or maximum remaining
     * cooldown across all providers if provider is null/blank or 'all'.
     */
    public int getCooldownSeconds(String provider) {
        long cooldownMs = getCooldownRemainingMs(provider);
        return cooldownMs > 0 ? (int) Math.max(1, (cooldownMs + 999) / 1000) : 0;
    }

    /**
     * Returns the remaining cooldown milliseconds for an unhealthy/OPEN provider,
     * or 0 if the provider is currently healthy.
     */
    public long getBackoffMs(String provider) {
        return getCooldownRemainingMs(provider);
    }

    /**
     * Returns the current circuit breaker state for a provider.
     * If no circuit breaker exists yet for the provider, returns CLOSED.
     */
    public CircuitBreaker.State getCircuitState(String provider) {
        CircuitBreaker breaker = circuitBreakerMap.get(provider);
        if (breaker == null) {
            return CircuitBreaker.State.CLOSED;
        }
        return breaker.getState();
    }

    /**
     * Returns the current circuit breaker state name for a provider,
     * or "UNKNOWN" if no circuit breaker exists yet.
     */
    public String getCircuitStateName(String provider) {
        CircuitBreaker breaker = circuitBreakerMap.get(provider);
        return breaker != null ? breaker.getStateName() : "UNKNOWN";
    }

    /**
     * Returns the minimum cooldown remaining (in seconds) across all configured providers
     * whose circuit breaker is OPEN or whose health is currently unhealthy.
     * Returns 0 if no provider is currently in a cooldown state.
     */
    public int getMinimumCooldownSeconds() {
        long minCooldownMs = Long.MAX_VALUE;
        boolean found = false;
        for (String provider : PROVIDER_PRIORITY) {
            long cooldownMs = getCooldownRemainingMs(provider);
            if (cooldownMs > 0) {
                found = true;
                minCooldownMs = Math.min(minCooldownMs, cooldownMs);
            }
        }
        if (!found) return 0;
        return (int) Math.max(1, (minCooldownMs + 999) / 1000);
    }

    private CircuitBreaker getCircuitBreaker(String provider) {
        return circuitBreakerMap.computeIfAbsent(provider, p -> new CircuitBreaker());
    }

    // -----------------------------------------------------------------------
    // Priority-based provider resolution
    // -----------------------------------------------------------------------

    /**
     * Returns providers in priority order, skipping any that are currently
     * unavailable (rate-limited or saturated).
     */
    public List<String> getAvailableProviders() {
        List<String> available = new ArrayList<>();
        for (String provider : PROVIDER_PRIORITY) {
            if (isProviderAvailable(provider)) {
                available.add(provider);
            }
        }
        return available;
    }

    /**
     * Returns the next available provider in priority order after excluding
     * the specified provider. Useful for fallback routing.
     */
    public String getNextProvider(String excludeProvider) {
        for (String provider : PROVIDER_PRIORITY) {
            if (provider.equals(excludeProvider)) {
                continue;
            }
            if (isProviderAvailable(provider)) {
                return provider;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Centralised retry + cross-provider fallback
    // -----------------------------------------------------------------------

/**
 * Executes an LLM call with automatic retry and cross-provider fallback.
 * <p>
 * Retry rules (see {@link RetryPolicy}):
 * <ul>
 *   <li>Retryable: HTTP 429, HTTP 500+, network timeout, connection error</li>
 *   <li>Non-retryable: all other errors (400, 401, 403, 404, content-level errors)</li>
 * </ul>
 * <p>
 * When all providers exhaust retries, a {@link ProviderException} is thrown.
  *
   * @param provider         primary provider name
   * @param model            model name (may be null to use provider default)
   * @param temp             temperature
   * @param timeout          timeout in seconds
   * @param systemInstruction system prompt
   * @param userPrompt       user/task prompt
   * @param callerLabel      short label for log messages
   * @param domain           detected domain hint
   * @return the first successful {@link AiResponse}
   * @throws ProviderException when every provider is unavailable after retries
   */
    public AiResponse executeWithRetryAndFallback(String provider, String model, double temp,
                                                   int timeout, String systemInstruction,
                                                   String userPrompt, String callerLabel,
                                                   List<QuotaWarning> quotaWarnings, String domain) {
        return executeWithRetryAndFallback(provider, model, temp, timeout, systemInstruction, userPrompt,
                callerLabel, quotaWarnings, domain, true);
    }

    /**
     * Execute an LLM request with retry and provider fallback.
     *
     * @param provider         primary provider name
     * @param model            model name (may be null to use provider default)
     * @param temp             temperature
     * @param timeout          timeout in seconds
     * @param systemInstruction system prompt
     * @param userPrompt       user/task prompt
     * @param callerLabel      short label for log messages
     * @param domain           detected domain hint
     * @param structuredOutput if true, request JSON mode from the provider; if false, allow free-form text
     * @return the first successful {@link AiResponse}
   * @throws ProviderException when every provider is unavailable after retries
     */
    public AiResponse executeWithRetryAndFallback(String provider, String model, double temp,
                                                   int timeout, String systemInstruction,
                                                   String userPrompt, String callerLabel,
                                                   List<QuotaWarning> quotaWarnings, String domain,
                                                   boolean structuredOutput) {
        UUID currentSessionId = com.nexusivr.ai.service.GenerationCancellationRegistry.getCurrentSessionId();
        if (currentSessionId != null && com.nexusivr.ai.service.GenerationCancellationRegistry.isCancelled(currentSessionId)) {
            throw new com.nexusivr.ai.service.exception.GenerationCancelledException("Generation request was cancelled.");
        }

        List<String> providersToTry = new ArrayList<>(PROVIDER_PRIORITY);

        // If a specific provider was requested, try it first, then fall back to priority order.
        if (provider != null && !provider.isBlank()) {
            String requested = provider.toLowerCase().trim();
            providersToTry.remove(requested);
            providersToTry.add(0, requested);
            logger.info("[AI_PROVIDER] [{}] Requested: {}", callerLabel, requested);
        }

        // Only apply quota-aware routing when no explicit provider was requested.
        // When the user explicitly selects a provider, respect that choice as the primary attempt.
        if (provider == null || provider.isBlank()) {
            String quotaSelected = quotaRouter.selectProvider(providersToTry, estimateTokenCount(userPrompt), true);
            if (quotaSelected != null && !quotaSelected.equals(providersToTry.get(0))) {
                providersToTry.remove(quotaSelected);
                providersToTry.add(0, quotaSelected);
                logger.info("[AI_PROVIDER] [{}] Quota routing selected: {}", callerLabel, quotaSelected);
            }
        }

        String originalRequestedProvider = provider != null && !provider.isBlank() ? provider.toLowerCase().trim() : providersToTry.get(0);
        String lastAttemptedProvider = null;
        boolean fallbackOccurred = false;
        String firstFailedProvider = null;
        List<com.nexusivr.ai.dto.common.ProviderAttemptDto> providerAttempts = new ArrayList<>();

        for (String targetProvider : providersToTry) {
            UUID checkSessionId = com.nexusivr.ai.service.GenerationCancellationRegistry.getCurrentSessionId();
            if (checkSessionId != null && com.nexusivr.ai.service.GenerationCancellationRegistry.isCancelled(checkSessionId)) {
                throw new com.nexusivr.ai.service.exception.GenerationCancelledException("Generation request was cancelled.");
            }
            if (!isProviderAvailable(targetProvider)) {
                ProviderHealth health = getHealth(targetProvider);
                CircuitBreaker breaker = getCircuitBreaker(targetProvider);
                long cooldownRemaining = Math.max(health.getCooldownRemainingMs(), breaker.getCooldownRemainingMs());
                int cooldownSecs = (int) Math.max(1, (cooldownRemaining + 999) / 1000);
                int statusCode = (breaker.getState() == CircuitBreaker.State.OPEN) ? 429 : 503;
                String skipReason;
                if (breaker.getState() == CircuitBreaker.State.OPEN) {
                    skipReason = "Quota exceeded, retry after ~" + Math.max(1, (cooldownSecs + 59) / 60) + " min";
                } else if (health.isUnavailable()) {
                    skipReason = health.getLastFailureReason() != null ? health.getLastFailureReason() : "Provider unhealthy";
                } else {
                    skipReason = "Provider unavailable";
                }
                providerAttempts.add(new com.nexusivr.ai.dto.common.ProviderAttemptDto(targetProvider, statusCode, skipReason, cooldownSecs));
                logger.warn("[AI_PROVIDER] [{}] Skipping '{}'. Reason: {}.",
                        callerLabel, targetProvider, skipReason);
                continue;
            }

            logger.info("[AI_PROVIDER] [{}] Attempt: {}Client", callerLabel, capitalize(targetProvider));
            lastAttemptedProvider = targetProvider;

            // Bug 16 fix: Resolve the correct model for the target provider, don't reuse
            // the primary provider's model (which causes "Model 'gemini-2.0-flash' is invalid for provider 'groq'").
            String resolvedModel = model;
            List<String> supportedModels = PROVIDER_MODELS.get(targetProvider);
            if (supportedModels != null && !supportedModels.isEmpty()) {
                if (model == null || model.isBlank() || supportedModels.stream().noneMatch(m -> m.equalsIgnoreCase(model))) {
                    resolvedModel = getProviderDefaultModel(targetProvider);
                }
            }

            logger.info("[ProviderManager] [{}] Resolved model for '{}': {}.",
                    callerLabel, targetProvider, resolvedModel);

            LlmClient client = getLlmClient(targetProvider, resolvedModel, temp, timeout);
            AiResponse response = attemptWithRetries(client, targetProvider, resolvedModel, temp, timeout,
                    systemInstruction, userPrompt, callerLabel, quotaWarnings, structuredOutput);

            if (response != null && !response.isMock()) {
                markSuccess(targetProvider);
                tokenTracker.recordUsage(targetProvider, resolvedModel, response.getPromptTokens(), response.getCompletionTokens());
                if (fallbackOccurred) {
                    logger.info("[AI_PROVIDER] [{}] Fallback: From: {} To: {} Result: SUCCESS", callerLabel, firstFailedProvider, targetProvider);
                } else {
                    logger.info("[AI_PROVIDER] [{}] Attempt: {}Client Result: SUCCESS", callerLabel, capitalize(targetProvider));
                }
                response = new AiResponse(
                        response.getContent(), response.getModel(), response.getPromptTokens(), response.getCompletionTokens(),
                        response.isMock(), response.isTemplateFallback(), response.getFunctionName(), response.getFunctionArguments(),
                        response.getStatusCode(), provider, targetProvider, providerAttempts
                );
                return response;
            }

            String errorContent = response != null ? response.getContent() : "null response";
            if (firstFailedProvider == null) {
                firstFailedProvider = targetProvider;
            }
            fallbackOccurred = true;
            int statusCode = response != null ? response.getStatusCode() : 0;

            if (response != null && response.getStatusCode() == 429) {
                markRateLimited(targetProvider);
            } else if (response != null && (response.getStatusCode() == 401 || response.getStatusCode() == 403)) {
                markAuthFailure(targetProvider, errorContent);
            } else if (RetryPolicy.isRetryable(response)) {
                markFailed(targetProvider, errorContent);
            } else {
                markFailed(targetProvider, errorContent);
            }

            CircuitBreaker breaker = getCircuitBreaker(targetProvider);
            long cooldownRemaining = breaker.getCooldownRemainingMs();
            int cooldownSecs = (statusCode == 429 || breaker.getState() == CircuitBreaker.State.OPEN)
                    ? (int) Math.max(1, (cooldownRemaining + 999) / 1000)
                    : 0;
            String attemptReason;
            if (statusCode == 429) {
                attemptReason = "Quota exceeded, retry after ~" + Math.max(1, (cooldownSecs + 59) / 60) + " min";
            } else if (statusCode == 0 || errorContent.toLowerCase().contains("time")) {
                attemptReason = "Timed out after " + timeout + "s";
            } else if (errorContent.toLowerCase().contains("refused") || errorContent.toLowerCase().contains("unreachable")) {
                attemptReason = "Connection refused / host unreachable";
            } else {
                attemptReason = errorContent;
            }

            providerAttempts.add(new com.nexusivr.ai.dto.common.ProviderAttemptDto(targetProvider, statusCode, attemptReason, cooldownSecs));
            logger.warn("[AI_PROVIDER] [{}] Attempt: {}Client Result: FAILED Reason: {}", callerLabel, capitalize(targetProvider), errorContent);
        }

        // A1 + Addendum: TemplateGenerator is strictly a last-resort circuit-breaker
        // triggered ONLY after all real LLM providers (Groq, Gemini, Ollama) have been attempted and failed.
        // It is valid ONLY for full flow generation (GENERATE_FLOW) where VoiceXML fallback templates exist.
        if ("PROMPT_REFINER".equalsIgnoreCase(callerLabel) ||
            "IMPROVE_FLOW_PATCH".equalsIgnoreCase(callerLabel) ||
            "AGENT_OPTIMIZATION_ADVISOR".equalsIgnoreCase(callerLabel) ||
            "AGENT_VALIDATOR_ASSISTANT".equalsIgnoreCase(callerLabel) ||
            (callerLabel != null && (callerLabel.startsWith("AGENT_") || callerLabel.startsWith("SUGGESTION_")))) {
            logger.warn("[AI_PROVIDER] [{}] All real LLM providers exhausted after retries. Skipping TemplateGenerator fallback for operation '{}'.", callerLabel, callerLabel);
            return null;
        }

        boolean templateGeneratorAttempted = false;
        logger.warn("[AI_PROVIDER] [{}] All real LLM providers exhausted after retries. Invoking last-resort TemplateGenerator circuit-breaker.", callerLabel);
        try {
            templateGeneratorAttempted = true;
            TemplateGenerator templateGen = new TemplateGenerator(getProviderDefaultModel("template-generator"), domain);
            AiResponse templateResponse = structuredOutput
                    ? templateGen.generateStructuredResponse(systemInstruction, userPrompt, List.of(), domain)
                    : templateGen.generateResponse(systemInstruction, userPrompt, List.of());
            if (templateResponse != null && templateResponse.getContent() != null && !templateResponse.getContent().isBlank()) {
                logger.info("[AI_PROVIDER] [{}] TemplateGenerator circuit-breaker generated fallback response successfully for domain='{}'.",
                         callerLabel, domain);
                return new AiResponse(
                        templateResponse.getContent(),
                        "template-generator",
                        0, 0,
                        false, true,
                        null, null,
                        200,
                        originalRequestedProvider,
                        "template-generator",
                        providerAttempts
                );
            }
        } catch (Exception e) {
            logger.error("[AI_PROVIDER] [{}] TemplateGenerator fallback failed: {}", callerLabel, e.getMessage(), e);
        }

        String errorMsg = "All providers exhausted after retries. provider=" + String.join(",", providersToTry);
        if (quotaWarnings != null && !quotaWarnings.isEmpty()) {
            errorMsg += ". Quota warnings: " + quotaWarnings.size();
        }
        logger.error("[AI_PROVIDER] [{}] {}", callerLabel, errorMsg);

        boolean hasQuotaExceeded = false;
        boolean hasAuthFailure = false;
        for (String p : providersToTry) {
            ProviderHealth health = getHealth(p);
            CircuitBreaker breaker = circuitBreakerMap.get(p);
            if (health.isRateLimited() || (breaker != null && breaker.getState() == CircuitBreaker.State.OPEN
                    && breaker.getLastFailureReason() != null && breaker.getLastFailureReason().toLowerCase().contains("quota"))) {
                hasQuotaExceeded = true;
            }
            if (health.isAuthFailure() || (breaker != null && breaker.getState() == CircuitBreaker.State.OPEN
                    && breaker.getLastFailureReason() != null && breaker.getLastFailureReason().toLowerCase().contains("auth"))) {
                hasAuthFailure = true;
            }
        }

        ProviderException.FailureReason reason = ProviderException.FailureReason.PROVIDER_ERROR;
        if (hasQuotaExceeded || (quotaWarnings != null && !quotaWarnings.isEmpty())) {
            reason = ProviderException.FailureReason.QUOTA_EXCEEDED;
        } else if (hasAuthFailure) {
            reason = ProviderException.FailureReason.AUTH_FAILURE;
        }

        Map<String, String> providerStatuses = collectProviderStatuses(providersToTry);
        
        String actualProvider = templateGeneratorAttempted ? "template-generator" : (lastAttemptedProvider != null ? lastAttemptedProvider : originalRequestedProvider);
        boolean fallback = providerAttempts.size() > 1 || templateGeneratorAttempted;
        List<String> attemptedList = new ArrayList<>();
        for (com.nexusivr.ai.dto.common.ProviderAttemptDto attempt : providerAttempts) {
            attemptedList.add(attempt.getProvider());
        }
        if (templateGeneratorAttempted) {
            attemptedList.add("template-generator");
        }

        throw new ProviderException(originalRequestedProvider, errorMsg, reason, providerStatuses, "none", false, attemptedList);
    }

    private AiResponse attemptWithRetries(LlmClient client, String provider, String model, double temp,
                                           int timeout, String systemInstruction, String userPrompt,
                                           String callerLabel, List<QuotaWarning> quotaWarnings,
                                           boolean structuredOutput) {
        String errorContent = null;

        for (int attempt = 1; attempt <= MAX_RETRIES_PER_PROVIDER; attempt++) {
            UUID checkSessionId = com.nexusivr.ai.service.GenerationCancellationRegistry.getCurrentSessionId();
            if (checkSessionId != null && com.nexusivr.ai.service.GenerationCancellationRegistry.isCancelled(checkSessionId)) {
                throw new com.nexusivr.ai.service.exception.GenerationCancelledException("Generation request was cancelled.");
            }
            logger.info("[ProviderManager] [{}] Attempt {}/{} on provider '{}' (model='{}', structuredOutput={}).",
                    callerLabel, attempt, MAX_RETRIES_PER_PROVIDER, provider, model, structuredOutput);

            AiResponse response;
            try {
                if (structuredOutput) {
                    response = client.generateStructuredResponse(systemInstruction, userPrompt, List.of());
                } else {
                    response = client.generateResponse(systemInstruction, userPrompt, List.of());
                }
            } catch (Exception e) {
                logger.warn("[ProviderManager] [{}] Provider '{}' threw exception on attempt {}: {}",
                        callerLabel, provider, attempt, e.getMessage());
                if (RetryPolicy.isRetryable(e)) {
                    String retryReason = "Retryable exception: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    logger.warn("[ProviderManager] [{}] Retry Reason: {}. Backing off before retry.", callerLabel, retryReason);
                    errorContent = "Exception: " + e.getMessage();
                    backoff(attempt);
                    continue;
                } else {
                    logger.warn("[ProviderManager] [{}] Non-retryable exception on '{}' attempt {}: {}",
                            callerLabel, provider, attempt, e.getClass().getSimpleName());
                    return new AiResponse("Non-retryable error: " + e.getMessage(), model, 0, 0, true, null, null, 0);
                }
            }

            String content = response.getContent();

            if (!response.isMock()) {
                return response;
            }

            int statusCode = response.getStatusCode();

            if (!RetryPolicy.isRetryable(statusCode, content)) {
                logger.warn("[ProviderManager] [{}] Non-retryable error on '{}' attempt {} (statusCode={}): {}",
                        callerLabel, provider, attempt, statusCode, content);
                return response;
            }

            errorContent = content;
            String retryReason = statusCode >= 500
                    ? "HTTP " + statusCode + " - Server error"
                    : "Network error: " + content;
            logger.warn("[ProviderManager] [{}] Retry Reason: {} on '{}' attempt {} (statusCode={}). Backing off before retry.",
                    callerLabel, retryReason, provider, attempt, statusCode);

            if (attempt < MAX_RETRIES_PER_PROVIDER) {
                backoff(attempt);
            }
        }

        return errorContent != null
                ? new AiResponse(errorContent, model, 0, 0, true, null, null, 0)
                : new AiResponse("Provider '" + provider + "' failed after " + MAX_RETRIES_PER_PROVIDER + " attempts.", model, 0, 0, true, null, null, 0);
    }

    private void backoff(int attempt) {
        long delay = Math.min(BASE_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void addQuotaWarning(List<QuotaWarning> quotaWarnings, String provider, String model, int attempt) {
        if (quotaWarnings != null) {
            quotaWarnings.add(new QuotaWarning(provider, model, attempt));
        }
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.length() / 4;
    }

    private Map<String, String> collectProviderStatuses(List<String> providers) {
        Map<String, String> statuses = new HashMap<>();
        for (String provider : providers) {
            ProviderHealth health = getHealth(provider);
            CircuitBreaker breaker = circuitBreakerMap.get(provider);
            String status;
            if (breaker != null && breaker.getState() == CircuitBreaker.State.OPEN) {
                status = "CIRCUIT_OPEN";
            } else if (health.isUnavailable()) {
                status = "UNHEALTHY";
            } else if (health.isAuthFailure()) {
                status = "AUTH_FAILURE";
            } else if (health.isRateLimited()) {
                status = "RATE_LIMITED";
            } else {
                status = "UNKNOWN";
            }
            statuses.put(provider, status);
        }
        return statuses;
    }

    public Map<String, Map<String, Object>> getProviderHealthOverview() {
        Map<String, Map<String, Object>> overview = new LinkedHashMap<>();
        for (String provider : List.of("groq", "gemini", "openrouter", "ollama")) {
            Map<String, Object> item = new HashMap<>();
            ProviderHealth health = getHealth(provider);
            CircuitBreaker breaker = circuitBreakerMap.get(provider);

            boolean available = isProviderAvailable(provider);
            String state = breaker != null ? breaker.getStateName() : "CLOSED";
            long cooldownMs = getCooldownRemainingMs(provider);

            item.put("provider", provider);
            item.put("status", available ? "HEALTHY" : (state.equals("OPEN") ? "CIRCUIT_OPEN" : "UNHEALTHY"));
            item.put("circuitState", state);
            item.put("cooldownRemainingSeconds", (int) Math.max(0, cooldownMs / 1000));
            item.put("consecutiveFailures", health != null ? health.getConsecutiveFailures() : 0);
            item.put("lastFailureReason", (health != null && health.getLastFailureReason() != null) ? health.getLastFailureReason() : "None");
            overview.put(provider, item);
        }
        return overview;
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
