package com.nexusivr.ai.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * Pluggable, unified client for OpenAI-compatible APIs (Groq and Ollama).
 * Note: Direct OpenAI provider support has been removed; this class is retained
 * because Groq and Ollama use the OpenAI-compatible chat/completions protocol.
 * Eliminates duplicate endpoint invocation logic.
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String providerName;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final double temperature;
    private final HttpClient httpClient;
    private final Gson gson;

    public OpenAiCompatibleClient(String providerName, String apiKey, String baseUrl, String model, int timeoutSeconds, double temperature) {
        this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null").toLowerCase().trim();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null").trim();
        this.model = Objects.requireNonNull(model, "model cannot be null").trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.temperature = temperature >= 0 ? temperature : 0.7;
        int connectTimeoutSec = "ollama".equalsIgnoreCase(this.providerName) ? 3 : Math.min(this.timeoutSeconds, 5);
        // Force HTTP/1.1 to prevent protocol negotiation failure with non-compliant HTTP/2 servers (e.g. uvicorn gateway)
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec))
                .build();
        this.gson = new Gson();
        String resolvedEndpoint = resolveEndpointUrl(this.baseUrl, this.providerName);
        logger.info("[{}] Resolved request URL: {}", this.providerName, resolvedEndpoint);
        logger.info("OpenAiCompatibleClient: Initialized for {} using model={} at {}", this.providerName, this.model, resolvedEndpoint);
    }

    public static String resolveEndpointUrl(String baseUrl, String providerName) {
        String endpointUrl = baseUrl.replaceAll("/+$", "");
        if ("ollama".equalsIgnoreCase(providerName)) {
            if (!endpointUrl.endsWith("/api/chat")) {
                endpointUrl += "/api/chat";
            }
        } else if ("openrouter".equalsIgnoreCase(providerName) || endpointUrl.contains("/student/chat") || endpointUrl.endsWith("/chat")) {
            // Base URL is complete endpoint (e.g. ITI Gateway http://apiaccess.iti.net.eg/api/v1/student/chat)
            // Do not append /chat/completions
        } else if (!endpointUrl.endsWith("/chat/completions")) {
            endpointUrl += "/chat/completions";
        }
        return endpointUrl;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        if ("groq".equals(providerName) || "openrouter".equals(providerName)) {
            return !apiKey.isBlank();
        }
        return true; // Ollama is local, always assume available or checked dynamically
    }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeChat(systemPrompt, userPrompt, history, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeChat(systemPrompt, userPrompt, history, true);
    }

    private AiResponse executeChat(String systemPrompt, String userPrompt, List<Message> history, boolean jsonMode) {
        long startTime = System.currentTimeMillis();

        if (("groq".equals(providerName) || "openrouter".equals(providerName)) && apiKey.isBlank()) {
            logger.warn("OpenAiCompatibleClient [{}]: API key is missing. Returning failure response.", providerName);
            return new AiResponse(providerName.toUpperCase() + " authentication failed (API key missing).", model, 0, 0, true, null, null, 401);
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("model_id", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", com.nexusivr.ai.config.LlmConfig.getMaxTokens());


            // Handle structured JSON output format
            if (jsonMode) {
                if ("ollama".equals(providerName)) {
                    // Ollama expects format: "json"
                    requestBody.addProperty("format", "json");
                } else {
                    // OpenAI, Groq, and OpenRouter expect response_format: {type: "json_object"}
                    JsonObject responseFormat = new JsonObject();
                    responseFormat.addProperty("type", "json_object");
                    requestBody.add("response_format", responseFormat);
                }
            }

            JsonArray messagesArray = new JsonArray();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                if ("openrouter".equals(providerName)) {
                    // Bedrock backend for openrouter (gpt-oss-20b) does not support role: "system"
                    userPrompt = (userPrompt != null && !userPrompt.isBlank())
                            ? systemPrompt + "\n\n" + userPrompt
                            : systemPrompt;
                } else {
                    JsonObject sysMsg = new JsonObject();
                    sysMsg.addProperty("role", "system");
                    sysMsg.addProperty("content", systemPrompt);
                    messagesArray.add(sysMsg);
                }
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

            // Resolve endpoint URL path
            String endpointUrl = resolveEndpointUrl(baseUrl, providerName);
            if ("ollama".equals(providerName)) {
                requestBody.addProperty("stream", false);
            }

            HttpRequest.Builder httpReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));

            if (!apiKey.isBlank()) {
                httpReqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            if ("openrouter".equals(providerName)) {
                httpReqBuilder.header("HTTP-Referer", "http://localhost:3000");
                httpReqBuilder.header("X-Title", "NexusIVR");
            }

            HttpRequest httpRequest = httpReqBuilder.timeout(Duration.ofSeconds(timeoutSeconds)).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 400) {
                logger.error("OpenAiCompatibleClient [{}]: HTTP {} returned. Latency: {}ms, Body: {}",
                        providerName, response.statusCode(), latencyMs, response.body());
                return new AiResponse(providerName.toUpperCase() + " API returned HTTP error status: " + response.statusCode(), model, 0, 0, true, null, null, response.statusCode());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = "";
            String finishReason = null;
            if (responseJson.has("choices")) {
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (!choices.isEmpty()) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()) {
                        finishReason = firstChoice.get("finish_reason").getAsString();
                    }
                    if (firstChoice.has("message") && !firstChoice.get("message").isJsonNull()) {
                        JsonObject msgObj = firstChoice.getAsJsonObject("message");
                        if (msgObj.has("content") && !msgObj.get("content").isJsonNull()) {
                            content = msgObj.get("content").getAsString();
                        }
                    } else if (firstChoice.has("text") && !firstChoice.get("text").isJsonNull()) {
                        content = firstChoice.get("text").getAsString();
                    }
                }
            } else if (responseJson.has("message")) {
                JsonObject messageObj = responseJson.getAsJsonObject("message");
                if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                    content = messageObj.get("content").getAsString();
                }
            } else if (responseJson.has("output_text") && !responseJson.get("output_text").isJsonNull()) {
                content = responseJson.get("output_text").getAsString();
            }

            // Extract tokens if present
            int promptTokens = 0;
            int completionTokens = 0;
            if (responseJson.has("usage")) {
                JsonObject usage = responseJson.getAsJsonObject("usage");
                if (usage.has("prompt_tokens")) {
                    promptTokens = usage.get("prompt_tokens").getAsInt();
                } else if (usage.has("input_tokens")) {
                    promptTokens = usage.get("input_tokens").getAsInt();
                }

                if (usage.has("completion_tokens")) {
                    completionTokens = usage.get("completion_tokens").getAsInt();
                } else if (usage.has("output_tokens")) {
                    completionTokens = usage.get("output_tokens").getAsInt();
                }
            } else if (responseJson.has("prompt_eval_count")) {
                promptTokens = responseJson.get("prompt_eval_count").getAsInt();
                completionTokens = responseJson.has("eval_count") ? responseJson.get("eval_count").getAsInt() : 0;
            }

            boolean isTruncated = "length".equalsIgnoreCase(finishReason) || "max_tokens".equalsIgnoreCase(finishReason);
            if (isTruncated) {
                logger.warn("[{}] Response truncated due to token limit — finish_reason='{}', outputTokens={}, maxTokens={}. Response may be incomplete.",
                        providerName, finishReason, completionTokens, com.nexusivr.ai.config.LlmConfig.getMaxTokens());
            }

            logger.info("[{}] LLM Latency: {}ms. Token Usage: input={}, output={}, total={}. Model: {}. FinishReason: {}.",
                    providerName, latencyMs, promptTokens, completionTokens, promptTokens + completionTokens, model, finishReason != null ? finishReason : "unknown");

            if (content == null || content.isBlank()) {
                if (isTruncated) {
                    logger.warn("[{}] Provider returned HTTP 200 OK but content was truncated due to token limit (finish_reason='{}').",
                            providerName, finishReason);
                    return new AiResponse("Response truncated due to token limit (finish_reason='" + finishReason + "', " + completionTokens + " output tokens generated) — consider a shorter/simpler flow request or increasing max_tokens.", model, promptTokens, completionTokens, true, false, null, null, 502);
                }
                logger.debug("[{}] Provider returned HTTP 200 OK but content is empty. Raw HTTP body: {}",
                        providerName, response.body());
                return new AiResponse("Provider '" + providerName + "' returned empty response (0 output tokens).", model, promptTokens, completionTokens, true, false, null, null, 502);
            }

            return new AiResponse(content, model, promptTokens, completionTokens, false);


        } catch (ConnectException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[{}] LLM Latency: {}ms. Error: Server unreachable at {}. Model: {}.", providerName, latencyMs, baseUrl, model);
            return new AiResponse("Connection to " + providerName.toUpperCase() + " failed: server is unreachable.", model, 0, 0, true, null, null, 0);

        } catch (HttpTimeoutException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[{}] LLM Latency: {}ms. Error: Request timed out after {}s. Model: {}.", providerName, latencyMs, timeoutSeconds, model);
            return new AiResponse(providerName.toUpperCase() + " request timed out.", model, 0, 0, true, null, null, 0);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[{}] LLM Latency: {}ms. Error: Unexpected failure. Model: {}.", providerName, latencyMs, model, e);
            return new AiResponse("Unexpected error communicating with " + providerName.toUpperCase() + ": " + e.getMessage(), model, 0, 0, true, null, null, 0);
        }
    }
}
