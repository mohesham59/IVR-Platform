package com.nexusivr.ai.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Concrete {@link LlmClient} implementation for Groq Cloud API (e.g. llama-3.3-70b-versatile).
 * Connects to Groq OpenAI-compatible REST API (/chat/completions).
 */
public class GroqClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(GroqClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final Gson gson;

    public GroqClient() {
        this(
            LlmConfig.getGroqApiKey(),
            LlmConfig.getGroqBaseUrl(),
            LlmConfig.getGroqModel(),
            LlmConfig.getGroqTimeout()
        );
    }

    public GroqClient(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this(
            apiKey,
            baseUrl,
            model,
            timeoutSeconds,
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 5)))
                    .build()
        );
    }

    public GroqClient(String apiKey, String baseUrl, String model, int timeoutSeconds, HttpClient httpClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : LlmConfig.getGroqBaseUrl();
        this.model = (model != null && !model.isBlank()) ? model.trim() : LlmConfig.getGroqModel();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : LlmConfig.getGroqTimeout();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.gson = new Gson();
        logger.info("GroqClient initialized. Provider: groq, Model: {}, BaseUrl: {}, HasKey: {}",
                this.model, this.baseUrl, !this.apiKey.isBlank());
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "";
        int len = apiKey.length();
        if (len <= 4) return "****";
        return "****" + apiKey.substring(len - 4);
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeGroqChat(systemPrompt, userPrompt, history, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeGroqChat(systemPrompt, userPrompt, history, true);
    }

    private AiResponse executeGroqChat(String systemPrompt, String userPrompt, List<Message> history, boolean jsonMode) {
        long startTime = System.currentTimeMillis();

        if (apiKey.isBlank()) {
            logger.warn("Groq API key is missing. Returning auth failure response.");
            return new AiResponse("Groq authentication failed.", model, 0, 0, true);
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", 0.7);
            requestBody.addProperty("max_tokens", LlmConfig.getMaxTokens());


            if (jsonMode) {
                JsonObject responseFormat = new JsonObject();
                responseFormat.addProperty("type", "json_object");
                requestBody.add("response_format", responseFormat);
            }

            JsonArray messagesArray = new JsonArray();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messagesArray.add(sysMsg);
            }

            if (history != null) {
                for (Message msg : history) {
                    JsonObject msgObj = new JsonObject();
                    String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
                    msgObj.addProperty("role", role);
                    msgObj.addProperty("content", msg.getContent() != null ? msg.getContent() : "");
                    messagesArray.add(msgObj);
                }
            }

            if (userPrompt != null && !userPrompt.isBlank()) {
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userPrompt);
                messagesArray.add(userMsg);
            }

            requestBody.add("messages", messagesArray);

            String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            if (response.statusCode() == 401) {
                logger.error("Groq API authentication failed (401). Model: {}, Latency: {}ms", model, latencyMs);
                return new AiResponse("Groq authentication failed.", model, 0, 0, true, null, null, response.statusCode());
            }

            if (response.statusCode() == 429) {
                logger.error("Groq API quota or rate limit exceeded (429). Model: {}, Latency: {}ms", model, latencyMs);
                return new AiResponse("Groq quota exceeded.", model, 0, 0, true, null, null, response.statusCode());
            }

            // Fix #11: Groq json_validate_failed — extract usable content from failed_generation.
            // When structured output mode (json_object) is enabled and the LLM output fails JSON
            // schema validation, Groq returns HTTP 400 with error code "json_validate_failed".
            // The response body includes a "failed_generation" field containing the raw LLM output,
            // which for VoiceXML generation is typically valid and usable VXML content.
            if (response.statusCode() == 400) {
                try {
                    JsonObject errorBody = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (errorBody.has("error")) {
                        JsonObject errorObj = errorBody.getAsJsonObject("error");
                        String errorCode = errorObj.has("code") ? errorObj.get("code").getAsString() : "";
                        if ("json_validate_failed".equals(errorCode) && errorObj.has("failed_generation")) {
                            String failedGeneration = errorObj.get("failed_generation").getAsString();
                            if (failedGeneration != null && !failedGeneration.isBlank()) {
                                logger.warn("[GroqClient] HTTP 400 json_validate_failed — extracting failed_generation content ({} chars). Model: {}, Latency: {}ms",
                                        failedGeneration.length(), model, latencyMs);
                                // Return as a successful (non-mock) response since the content is usable
                                return new AiResponse(failedGeneration, model, 0, 0, false);
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    logger.debug("[GroqClient] Could not parse 400 error body for json_validate_failed: {}", parseEx.getMessage());
                }
            }

            if (response.statusCode() >= 400) {
                logger.error("Groq API returned HTTP {}. Model: {}, Latency: {}ms, Body: {}",
                        response.statusCode(), model, latencyMs, response.body());
                return new AiResponse("Groq Cloud API error (HTTP " + response.statusCode() + ").", model, 0, 0, true, null, null, response.statusCode());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = "";
            String finishReason = null;

            if (responseJson.has("choices") && responseJson.getAsJsonArray("choices").size() > 0) {
                JsonObject firstChoice = responseJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()) {
                    finishReason = firstChoice.get("finish_reason").getAsString();
                }
                if (firstChoice.has("message") && !firstChoice.get("message").isJsonNull()) {
                    JsonObject msgObj = firstChoice.getAsJsonObject("message");
                    if (msgObj.has("content") && !msgObj.get("content").isJsonNull()) {
                        content = msgObj.get("content").getAsString();
                    }
                }
            }

            int promptTokens = 0;
            int completionTokens = 0;
            if (responseJson.has("usage")) {
                JsonObject usage = responseJson.getAsJsonObject("usage");
                if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").getAsInt();
                if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").getAsInt();
            }

            String returnedModel = responseJson.has("model") ? responseJson.get("model").getAsString() : model;

            boolean isTruncated = "length".equalsIgnoreCase(finishReason) || "max_tokens".equalsIgnoreCase(finishReason);
            if (isTruncated) {
                logger.warn("[GroqClient] Response truncated due to token limit — finish_reason='{}', outputTokens={}, maxTokens={}. Response may be incomplete.",
                        finishReason, completionTokens, LlmConfig.getMaxTokens());
            }

            logger.info("[GroqClient] LLM Latency: {}ms. Token Usage: input={}, output={}, total={}. Model: {}. FinishReason: {}.",
                    latencyMs, promptTokens, completionTokens, promptTokens + completionTokens, returnedModel, finishReason != null ? finishReason : "unknown");

            if (content == null || content.isBlank()) {
                if (isTruncated) {
                    logger.warn("[GroqClient] Provider returned HTTP 200 OK but content was truncated due to token limit (finish_reason='{}').", finishReason);
                    return new AiResponse("Groq response truncated due to token limit (finish_reason='" + finishReason + "', " + completionTokens + " output tokens generated) — consider a shorter flow request or increasing max_tokens.", returnedModel, promptTokens, completionTokens, true, false, null, null, 502);
                }
                logger.debug("[GroqClient] Provider returned HTTP 200 OK but content is empty. Raw HTTP body: {}", response.body());
                return new AiResponse("Groq returned empty response (0 output tokens).", returnedModel, promptTokens, completionTokens, true, false, null, null, 502);
            }

            return new AiResponse(content, returnedModel, promptTokens, completionTokens, false);


        } catch (ConnectException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[GroqClient] LLM Latency: {}ms. Error: Server offline or unreachable. Model: {}.", latencyMs, model);
            return new AiResponse("Groq Cloud API is offline or unreachable.", model, 0, 0, true, null, null, 0);

        } catch (HttpTimeoutException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[GroqClient] LLM Latency: {}ms. Error: Request timed out after {}s. Model: {}.", latencyMs, timeoutSeconds, model);
            return new AiResponse("Groq request timed out after " + timeoutSeconds + "s.", model, 0, 0, true, null, null, 0);

        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[GroqClient] LLM Latency: {}ms. Error: IO failure communicating with server. Model: {}.", latencyMs, model, e);
            return new AiResponse("Network error communicating with Groq service: " + e.getMessage(), model, 0, 0, true, null, null, 0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[GroqClient] LLM Latency: {}ms. Error: Request interrupted.", System.currentTimeMillis() - startTime, e);
            return new AiResponse("Groq execution interrupted.", model, 0, 0, true, null, null, 0);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[GroqClient] LLM Latency: {}ms. Error: Unexpected error processing response. Model: {}.", latencyMs, model, e);
            return new AiResponse("Error processing response from Groq: " + e.getMessage(), model, 0, 0, true, null, null, 0);
        }
    }
}
