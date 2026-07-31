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
 * Concrete {@link LlmClient} implementation for Ollama (e.g. granite3.2:2b).
 * Connects to Ollama REST API (/api/chat) without requiring external API keys.
 */
public class OllamaClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final Gson gson;

    public OllamaClient() {
        this(
            LlmConfig.getOllamaBaseUrl(),
            LlmConfig.getOllamaModel(),
            LlmConfig.getOllamaTimeout()
        );
    }

    public OllamaClient(String baseUrl, String model, int timeoutSeconds) {
        this(
            baseUrl,
            model,
            timeoutSeconds,
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 3)))
                    .build()
        );
    }

    public OllamaClient(String baseUrl, String model, int timeoutSeconds, HttpClient httpClient) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : LlmConfig.getOllamaBaseUrl();
        this.model = (model != null && !model.isBlank()) ? model.trim() : LlmConfig.getOllamaModel();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : LlmConfig.getOllamaTimeout();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.gson = new Gson();
        logger.info("OllamaClient initialized. Provider: ollama, Model: {}, BaseUrl: {}", this.model, this.baseUrl);
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeOllamaChat(systemPrompt, userPrompt, history, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeOllamaChat(systemPrompt, userPrompt, history, true);
    }

    private AiResponse executeOllamaChat(String systemPrompt, String userPrompt, List<Message> history, boolean jsonMode) {
        long startTime = System.currentTimeMillis();
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("stream", false);

            if (jsonMode) {
                requestBody.addProperty("format", "json");
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

            String chatEndpoint = baseUrl.replaceAll("/+$", "") + "/api/chat";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(chatEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 400) {
                logger.error("Ollama API returned HTTP {}. Provider: ollama, Model: {}, Latency: {}ms, Body: {}",
                        response.statusCode(), model, latencyMs, response.body());

                if (response.statusCode() == 404) {
                    String fallbackText = "Ollama model '" + model + "' is not installed or endpoint not found. Run 'ollama pull " + model + "' to pull the model.";
                    return new AiResponse(fallbackText, model, 0, 0, true, null, null, response.statusCode());
                }
                String fallbackText = "Ollama service at " + baseUrl + " returned error HTTP " + response.statusCode() + ".";
                return new AiResponse(fallbackText, model, 0, 0, true, null, null, response.statusCode());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = "";

            if (responseJson.has("message")) {
                JsonObject messageObj = responseJson.getAsJsonObject("message");
                if (messageObj.has("content")) {
                    content = messageObj.get("content").getAsString();
                }
            } else if (responseJson.has("response")) {
                content = responseJson.get("response").getAsString();
            }

            int promptTokens = responseJson.has("prompt_eval_count") ? responseJson.get("prompt_eval_count").getAsInt() : 0;
            int completionTokens = responseJson.has("eval_count") ? responseJson.get("eval_count").getAsInt() : 0;
            String returnedModel = responseJson.has("model") ? responseJson.get("model").getAsString() : model;

            logger.info("[OllamaClient] LLM Latency: {}ms. Token Usage: input={}, output={}, total={}. Model: {}.",
                    latencyMs, promptTokens, completionTokens, promptTokens + completionTokens, returnedModel);

            return new AiResponse(content, returnedModel, promptTokens, completionTokens, false);

        } catch (ConnectException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[OllamaClient] LLM Latency: {}ms. Error: Server offline or unreachable. Provider: ollama, Model: {}.", latencyMs, model);
            String friendlyMsg = "Ollama service is currently offline or unreachable at " + baseUrl + ". Please verify Ollama is running (e.g. 'ollama serve') and model '" + model + "' is loaded.";
            return new AiResponse(friendlyMsg, model, 0, 0, true, null, null, 0);

        } catch (HttpTimeoutException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[OllamaClient] LLM Latency: {}ms. Error: Request timed out after {}s. Provider: ollama, Model: {}.", latencyMs, timeoutSeconds, model);
            String friendlyMsg = "Ollama request timed out after " + timeoutSeconds + "s for model '" + model + "'. The model may still be loading into GPU/VRAM.";
            return new AiResponse(friendlyMsg, model, 0, 0, true, null, null, 0);

        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[OllamaClient] LLM Latency: {}ms. Error: IO failure communicating with server. Provider: ollama, Model: {}.", latencyMs, model, e);
            String friendlyMsg = "Network error communicating with Ollama service at " + baseUrl + ": " + e.getMessage();
            return new AiResponse(friendlyMsg, model, 0, 0, true, null, null, 0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[OllamaClient] LLM Latency: {}ms. Error: Request interrupted.", System.currentTimeMillis() - startTime, e);
            return new AiResponse("Ollama execution interrupted.", model, 0, 0, true, null, null, 0);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[OllamaClient] LLM Latency: {}ms. Error: Unexpected error processing response. Provider: ollama, Model: {}.", latencyMs, model, e);
            return new AiResponse("Error processing response from Ollama: " + e.getMessage(), model, 0, 0, true, null, null, 0);
        }
    }
}
