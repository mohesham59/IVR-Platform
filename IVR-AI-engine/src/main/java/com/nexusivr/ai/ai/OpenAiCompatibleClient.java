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
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec))
                .build();
        this.gson = new Gson();
        logger.info("OpenAiCompatibleClient: Initialized for {} using model={}", this.providerName, this.model);
    }

    public HttpClient getHttpClient() {
        return httpClient;
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
        if ("groq".equals(providerName)) {
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

    private JsonArray buildMessagesArray(String systemPrompt, String userPrompt, List<Message> history, boolean foldSystemPrompt) {
        JsonArray messagesArray = new JsonArray();

        if (foldSystemPrompt) {
            String systemText = (systemPrompt != null) ? systemPrompt : "";
            boolean folded = false;

            if (history != null) {
                for (Message msg : history) {
                    JsonObject msgObj = new JsonObject();
                    String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
                    msgObj.addProperty("role", role);
                    
                    String content = msg.getContent() != null ? msg.getContent() : "";
                    if (!folded && "user".equals(role) && !systemText.isBlank()) {
                        content = "System Instructions:\n" + systemText + "\n\nUser Request:\n" + content;
                        folded = true;
                    }
                    msgObj.addProperty("content", content);
                    messagesArray.add(msgObj);
                }
            }

            if (userPrompt != null && !userPrompt.isBlank()) {
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                String content = userPrompt;
                if (!folded && !systemText.isBlank()) {
                    content = "System Instructions:\n" + systemText + "\n\nUser Request:\n" + content;
                    folded = true;
                }
                userMsg.addProperty("content", content);
                messagesArray.add(userMsg);
            }

            if (!folded && !systemText.isBlank()) {
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", systemText);
                messagesArray.add(userMsg);
            }
        } else {
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
        }

        return messagesArray;
    }

    private AiResponse executeChat(String systemPrompt, String userPrompt, List<Message> history, boolean jsonMode) {
        long startTime = System.currentTimeMillis();

        if ("groq".equals(providerName) && apiKey.isBlank()) {
            logger.warn("OpenAiCompatibleClient [{}]: API key is missing. Returning failure response.", providerName);
            return new AiResponse(providerName.toUpperCase() + " authentication failed (API key missing).", model, 0, 0, true);
        }

        try {
            boolean isStudentProxy = baseUrl.contains("/student");
            boolean isItiApi = baseUrl.contains("apiaccess.iti.net.eg") || isStudentProxy;

            String requestModel = model;
            if ("openrouter".equalsIgnoreCase(providerName)) {
                if (isStudentProxy) {
                    requestModel = com.nexusivr.ai.config.LlmConfig.getOpenrouterModel();
                } else if ("llama-3.3-70b-versatile".equalsIgnoreCase(model) || "llama-3.3-70b".equalsIgnoreCase(model)) {
                    requestModel = "meta-llama/llama-3.3-70b-instruct";
                }
            }

            JsonObject requestBody = new JsonObject();
            if (isStudentProxy) {
                requestBody.addProperty("model_id", requestModel);
            } else if (isItiApi) {
                requestBody.addProperty("model_id", requestModel);
            } else {
                requestBody.addProperty("model", requestModel);
            }
            requestBody.addProperty("temperature", temperature);

            // Handle structured JSON output format
            if (jsonMode && !isStudentProxy) {
                if ("ollama".equals(providerName)) {
                    // Ollama expects format: "json"
                    requestBody.addProperty("format", "json");
                } else {
                    // OpenAI and Groq expect response_format: {type: "json_object"}
                    JsonObject responseFormat = new JsonObject();
                    responseFormat.addProperty("type", "json_object");
                    requestBody.add("response_format", responseFormat);
                }
            }

            JsonArray messagesArray = buildMessagesArray(systemPrompt, userPrompt, history, false);
            requestBody.add("messages", messagesArray);

            // Resolve endpoint URL path
            String endpointUrl = baseUrl.replaceAll("/+$", "");
            if ("ollama".equals(providerName)) {
                endpointUrl += "/api/chat";
                // Ollama expects stream: false
                requestBody.addProperty("stream", false);
            } else if (isItiApi) {
                // The URL is already the full endpoint, do not append anything
            } else {
                endpointUrl += "/chat/completions";
            }

            HttpRequest.Builder httpReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));

            if (!apiKey.isBlank()) {
                httpReqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            if ("openrouter".equalsIgnoreCase(providerName) && !isStudentProxy) {
                httpReqBuilder.header("HTTP-Referer", "https://nexusivr.com");
                httpReqBuilder.header("X-Title", "NexusIVR");
            }

            HttpRequest httpRequest = httpReqBuilder.timeout(Duration.ofSeconds(timeoutSeconds)).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            JsonObject responseJson;
            if (response.statusCode() >= 400) {
                String responseBodyText = response.body();
                logger.error("OpenAiCompatibleClient [{}]: HTTP {} returned. Latency: {}ms, Body: {}",
                        providerName, response.statusCode(), latencyMs, responseBodyText);

                boolean isSystemMsgError = responseBodyText != null && 
                        (responseBodyText.contains("system message") || responseBodyText.contains("ValidationException")) &&
                        (responseBodyText.contains("support") || responseBodyText.contains("doesn't support"));

                if (isSystemMsgError && systemPrompt != null && !systemPrompt.isBlank()) {
                    logger.warn("OpenAiCompatibleClient [{}]: Model does not support system messages. Retrying with system prompt folded into user prompt.", providerName);

                    JsonObject retryRequestBody = new JsonObject();
                    if (isStudentProxy) {
                        retryRequestBody.addProperty("model_id", requestModel);
                    } else if (isItiApi) {
                        retryRequestBody.addProperty("model_id", requestModel);
                    } else {
                        retryRequestBody.addProperty("model", requestModel);
                    }
                    retryRequestBody.addProperty("temperature", temperature);
                    if (jsonMode && !isStudentProxy) {
                        if ("ollama".equals(providerName)) {
                            retryRequestBody.addProperty("format", "json");
                        } else {
                            JsonObject responseFormat = new JsonObject();
                            responseFormat.addProperty("type", "json_object");
                            retryRequestBody.add("response_format", responseFormat);
                        }
                    }
                    if ("ollama".equals(providerName)) {
                        retryRequestBody.addProperty("stream", false);
                    }

                    JsonArray retryMessagesArray = buildMessagesArray(systemPrompt, userPrompt, history, true);
                    retryRequestBody.add("messages", retryMessagesArray);

                    HttpRequest.Builder retryHttpReqBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(endpointUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(retryRequestBody)));

                    if (!apiKey.isBlank()) {
                        retryHttpReqBuilder.header("Authorization", "Bearer " + apiKey);
                    }

                    if ("openrouter".equalsIgnoreCase(providerName) && !isStudentProxy) {
                        retryHttpReqBuilder.header("HTTP-Referer", "https://nexusivr.com");
                        retryHttpReqBuilder.header("X-Title", "NexusIVR");
                    }

                    HttpRequest retryHttpRequest = retryHttpReqBuilder.timeout(Duration.ofSeconds(timeoutSeconds)).build();
                    long retryStartTime = System.currentTimeMillis();
                    response = httpClient.send(retryHttpRequest, HttpResponse.BodyHandlers.ofString());
                    latencyMs = System.currentTimeMillis() - retryStartTime;

                    if (response.statusCode() >= 400) {
                        logger.error("OpenAiCompatibleClient [{}]: Retry HTTP {} returned. Latency: {}ms, Body: {}",
                                providerName, response.statusCode(), latencyMs, response.body());
                        return new AiResponse(providerName.toUpperCase() + " API returned HTTP error status on retry: " + response.statusCode(), requestModel, 0, 0, true, null, null, response.statusCode());
                    }

                    responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                } else {
                    return new AiResponse(providerName.toUpperCase() + " API returned HTTP error status: " + response.statusCode(), requestModel, 0, 0, true, null, null, response.statusCode());
                }
            } else {
                responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            }
            String content = "";

            if (isItiApi && responseJson.has("output_text")) {
                content = responseJson.get("output_text").getAsString();
            } else if (responseJson.has("choices")) {
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (!choices.isEmpty()) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        content = firstChoice.getAsJsonObject("message").get("content").getAsString();
                    }
                }
            } else if (responseJson.has("message")) {
                JsonObject messageObj = responseJson.getAsJsonObject("message");
                if (messageObj.has("content")) {
                    content = messageObj.get("content").getAsString();
                }
            }

            // Extract tokens if present
            int promptTokens = 0;
            int completionTokens = 0;
            if (responseJson.has("usage")) {
                JsonObject usage = responseJson.getAsJsonObject("usage");
                if (isItiApi) {
                    promptTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                    completionTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                } else {
                    promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
                    completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
                }
            } else if (responseJson.has("prompt_eval_count")) {
                promptTokens = responseJson.get("prompt_eval_count").getAsInt();
                completionTokens = responseJson.has("eval_count") ? responseJson.get("eval_count").getAsInt() : 0;
            }

            logger.info("[{}] LLM Latency: {}ms. Token Usage: input={}, output={}, total={}. Model: {}.",
                    providerName, latencyMs, promptTokens, completionTokens, promptTokens + completionTokens, requestModel);

            return new AiResponse(content, requestModel, promptTokens, completionTokens, false);

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
