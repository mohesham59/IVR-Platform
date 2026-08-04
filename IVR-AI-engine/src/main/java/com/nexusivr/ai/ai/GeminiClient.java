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
 * Adapter client connecting to Google Gemini API (generateContent endpoint).
 * Implements the same {@link LlmClient} contract as {@link GroqClient} so that
 * {@code UnifiedAiEngine} can detect 429 / auth errors via {@code AiResponse.isMock()}
 * and route them to the Groq fallback provider transparently.
 */
public class GeminiClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final double temperature;
    private final HttpClient httpClient;
    private final Gson gson;

    public GeminiClient(String apiKey, String baseUrl, String model, int timeoutSeconds, double temperature) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim()
                : "https://generativelanguage.googleapis.com/v1beta";
        this.model = Objects.requireNonNull(model, "model cannot be null").trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.temperature = temperature >= 0 ? temperature : 0.7;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(this.timeoutSeconds, 5)))
                .build();
        this.gson = new Gson();
        logger.info("GeminiClient initialized. Provider: gemini, Model: {}, BaseUrl: {}, HasKey: {}",
                this.model, this.baseUrl, !this.apiKey.isBlank());
    }

    @Override
    public String getProviderName() { return "gemini"; }

    @Override
    public String getModelName() { return model; }

    @Override
    public boolean isAvailable() { return !apiKey.isBlank(); }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeGemini(systemPrompt, userPrompt, history, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return executeGemini(systemPrompt, userPrompt, history, true);
    }

    private AiResponse executeGemini(String systemPrompt, String userPrompt,
                                     List<Message> history, boolean jsonMode) {
        long startTime = System.currentTimeMillis();

        if (apiKey.isBlank()) {
            logger.warn("Gemini API key is missing. Returning auth failure response.");
            return new AiResponse("Gemini authentication failed.", model, 0, 0, true);
        }

        try {
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();

            if (history != null) {
                for (Message msg : history) {
                    JsonObject contentObj = new JsonObject();
                    String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
                    // Gemini API uses "model" where OpenAI-compatible APIs use "assistant"
                    String geminiRole = "assistant".equals(role) ? "model" : "user";
                    contentObj.addProperty("role", geminiRole);

                    JsonArray parts = new JsonArray();
                    JsonObject part = new JsonObject();
                    part.addProperty("text", msg.getContent() != null ? msg.getContent() : "");
                    parts.add(part);
                    contentObj.add("parts", parts);
                    contents.add(contentObj);
                }
            }

            if (userPrompt != null && !userPrompt.isBlank()) {
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", userPrompt);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
            }

            requestBody.add("contents", contents);

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                JsonObject systemInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                sysParts.add(sysPart);
                systemInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", systemInstruction);
            }

            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", temperature);
            generationConfig.addProperty("maxOutputTokens", com.nexusivr.ai.config.LlmConfig.getMaxTokens());

            if (jsonMode) {
                generationConfig.addProperty("responseMimeType", "application/json");
                JsonObject responseSchema = new JsonObject();
                responseSchema.addProperty("type", "object");
                JsonObject properties = new JsonObject();
                JsonObject vxmlProperty = new JsonObject();
                vxmlProperty.addProperty("type", "string");
                properties.add("vxml", vxmlProperty);
                responseSchema.add("properties", properties);
                JsonArray required = new JsonArray();
                required.add("vxml");
                responseSchema.add("required", required);
                generationConfig.add("responseSchema", responseSchema);
            }
            requestBody.add("generationConfig", generationConfig);

            String geminiUrl = baseUrl.replaceAll("/+$", "")
                    + "/models/" + model + ":generateContent?key=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(geminiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            // Granular status-code handling — mirrors GroqClient so UnifiedAiEngine's
            // quota-detection logic (isMock()==true + content.contains("quota exceeded"))
            // fires correctly and routes to the Groq fallback provider.
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                logger.error("Gemini API authentication failed ({}). Model: {}, Latency: {}ms",
                        response.statusCode(), model, latencyMs);
                return new AiResponse("Gemini authentication failed.", model, 0, 0, true, null, null, response.statusCode());
            }

            if (response.statusCode() == 429) {
                logger.error("Gemini API quota or rate limit exceeded (429). Model: {}, Latency: {}ms",
                        model, latencyMs);
                return new AiResponse("Gemini quota exceeded.", model, 0, 0, true, null, null, response.statusCode());
            }

            if (response.statusCode() >= 400) {
                logger.error("Gemini API returned HTTP {}. Model: {}, Latency: {}ms, Body: {}",
                        response.statusCode(), model, latencyMs, response.body());
                return new AiResponse("Gemini Cloud API error (HTTP " + response.statusCode() + ").",
                        model, 0, 0, true, null, null, response.statusCode());
            }

            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String contentText = "";

            if (responseJson.has("candidates")) {
                JsonArray candidates = responseJson.getAsJsonArray("candidates");
                if (!candidates.isEmpty()) {
                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    if (firstCandidate.has("content")) {
                        JsonObject candidateContent = firstCandidate.getAsJsonObject("content");
                        if (candidateContent.has("parts")) {
                            JsonArray parts = candidateContent.getAsJsonArray("parts");
                            if (!parts.isEmpty()) {
                                JsonObject firstPart = parts.get(0).getAsJsonObject();
                                if (firstPart.has("text") && !firstPart.get("text").isJsonNull()) {
                                    contentText = firstPart.get("text").getAsString();
                                }
                            }
                        }
                    }
                }
            }

            int promptTokens = 0;
            int completionTokens = 0;
            if (responseJson.has("usageMetadata")) {
                JsonObject usage = responseJson.getAsJsonObject("usageMetadata");
                promptTokens = usage.has("promptTokenCount")
                        ? usage.get("promptTokenCount").getAsInt() : 0;
                completionTokens = usage.has("candidatesTokenCount")
                        ? usage.get("candidatesTokenCount").getAsInt() : 0;
            }

            logger.info("[GeminiClient] LLM Latency: {}ms. Token Usage: input={}, output={}, total={}. Model: {}.",
                    latencyMs, promptTokens, completionTokens, promptTokens + completionTokens, model);

            if (contentText == null || contentText.isBlank()) {
                logger.debug("[GeminiClient] Provider returned HTTP 200 OK but content is empty. Raw HTTP body: {}", response.body());
                return new AiResponse("Gemini returned empty response (0 output tokens).", model, promptTokens, completionTokens, true, false, null, null, 502);
            }

            return new AiResponse(contentText, model, promptTokens, completionTokens, false);


        } catch (ConnectException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[GeminiClient] LLM Latency: {}ms. Error: Server offline or unreachable. Model: {}.", latencyMs, model);
            return new AiResponse("Gemini API is offline or unreachable.", model, 0, 0, true, null, null, 0);

        } catch (HttpTimeoutException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.warn("[GeminiClient] LLM Latency: {}ms. Error: Request timed out after {}s. Model: {}.", latencyMs, timeoutSeconds, model);
            return new AiResponse("Gemini request timed out after " + timeoutSeconds + "s.", model, 0, 0, true, null, null, 0);

        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[GeminiClient] LLM Latency: {}ms. Error: IO failure communicating with server. Model: {}.", latencyMs, model, e);
            return new AiResponse("Network error communicating with Gemini service: " + e.getMessage(),
                    model, 0, 0, true, null, null, 0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[GeminiClient] LLM Latency: {}ms. Error: Request interrupted.", System.currentTimeMillis() - startTime, e);
            return new AiResponse("Gemini execution interrupted.", model, 0, 0, true, null, null, 0);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            logger.error("[GeminiClient] LLM Latency: {}ms. Error: Unexpected error processing response. Model: {}.", latencyMs, model, e);
            return new AiResponse("Error processing response from Gemini: " + e.getMessage(),
                    model, 0, 0, true, null, null, 0);
        }
    }
}
