package com.nexusivr.ai.rag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nexusivr.ai.dto.SourceCitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for communicating with the Python RAG Microservice (port 8085).
 */
public class RagClient {

    private static final Logger logger = LoggerFactory.getLogger(RagClient.class);
    private final String ragServiceUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    private static String resolveDefaultUrl() {
        String envUrl = System.getenv("RAG_SERVICE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl.endsWith("/query") ? envUrl : envUrl + "/query";
        }
        return "http://localhost:8085/query";
    }

    public RagClient() {
        this(resolveDefaultUrl());
    }

    public RagClient(String ragServiceUrl) {
        this.ragServiceUrl = ragServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.gson = new Gson();
    }

    public static class RagQueryResult {
        private final List<SourceCitation> citations;
        private final boolean fallbackRequired;
        private final double maxScore;

        public RagQueryResult(List<SourceCitation> citations, boolean fallbackRequired, double maxScore) {
            this.citations = citations != null ? citations : new ArrayList<>();
            this.fallbackRequired = fallbackRequired;
            this.maxScore = maxScore;
        }

        public List<SourceCitation> getCitations() {
            return citations;
        }

        public boolean isFallbackRequired() {
            return fallbackRequired;
        }

        public double getMaxScore() {
            return maxScore;
        }
    }

    /**
     * Executes RAG query against microservice.
     */
    public RagQueryResult queryRag(String userQuery, int topK, double minScore) {
        if (userQuery == null || userQuery.isBlank()) {
            return new RagQueryResult(List.of(), true, 0.0);
        }

        try {
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("query", userQuery);
            requestJson.addProperty("top_k", topK);
            requestJson.addProperty("min_score", minScore);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ragServiceUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("[RagClient] RAG service returned status {}: {}", response.statusCode(), response.body());
                return new RagQueryResult(List.of(), true, 0.0);
            }

            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            boolean fallbackRequired = responseJson.has("fallback_required") && responseJson.get("fallback_required").getAsBoolean();
            double maxScore = responseJson.has("max_score") ? responseJson.get("max_score").getAsDouble() : 0.0;

            List<SourceCitation> citations = new ArrayList<>();
            if (responseJson.has("chunks") && responseJson.get("chunks").isJsonArray()) {
                JsonArray chunksArray = responseJson.getAsJsonArray("chunks");
                for (JsonElement elem : chunksArray) {
                    JsonObject obj = elem.getAsJsonObject();
                    SourceCitation citation = new SourceCitation(
                            obj.has("source_name") ? obj.get("source_name").getAsString() : "",
                            obj.has("rel_path") ? obj.get("rel_path").getAsString() : "",
                            obj.has("file_type") ? obj.get("file_type").getAsString() : "",
                            obj.has("section_or_page") ? obj.get("section_or_page").getAsString() : "",
                            obj.has("score") ? obj.get("score").getAsDouble() : 0.0,
                            obj.has("unique_doc_id") ? obj.get("unique_doc_id").getAsString() : "",
                            obj.has("chunk_id") ? obj.get("chunk_id").getAsString() : "",
                            obj.has("content") ? obj.get("content").getAsString() : ""
                    );
                    citations.add(citation);
                }
            }

            logger.info("[RagClient] RAG search returned {} chunk(s), fallbackRequired={}, maxScore={}",
                    citations.size(), fallbackRequired, maxScore);

            return new RagQueryResult(citations, fallbackRequired, maxScore);

        } catch (Exception e) {
            logger.warn("[RagClient] Error calling RAG microservice at {}: {}", ragServiceUrl, e.getMessage());
            return new RagQueryResult(List.of(), true, 0.0);
        }
    }

    /**
     * Ingests a local file path into the RAG vector store.
     */
    public boolean ingestFile(String filePath) {
        try {
            String ingestUrl = ragServiceUrl.replace("/query", "/ingest");
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("file_path", filePath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.error("[RagClient] Error ingesting file '{}': {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Ingests text content directly into the RAG vector store.
     */
    public boolean ingestText(String sourceName, String text) {
        try {
            String ingestTextUrl = ragServiceUrl.replace("/query", "/ingest_text");
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("source_name", sourceName);
            requestJson.addProperty("text", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestTextUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.error("[RagClient] Error ingesting text for '{}': {}", sourceName, e.getMessage());
            return false;
        }
    }
}
