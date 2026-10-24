package com.pharmacy.consultation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực EmbeddingClient tương thích chuẩn OpenAI (endpoint /v1/embeddings).
 */
@ApplicationScoped
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger LOG = Logger.getLogger(OpenAiEmbeddingClient.class);

    @ConfigProperty(name = "consultation.ai.base-url", defaultValue = "http://localhost:11434/v1")
    String baseUrl;

    @ConfigProperty(name = "consultation.ai.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "consultation.ai.embedding-model", defaultValue = "text-embedding-3-small")
    String model;

    @ConfigProperty(name = "consultation.ai.timeout-seconds", defaultValue = "10")
    int timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
        return httpClient;
    }

    @Override
    public List<Double> embedText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<List<Double>> result = embedBatch(List.of(text));
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return List.of();
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            String endpoint = normalizeEndpoint("/embeddings");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", texts);

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
            }

            HttpResponse<String> response = getHttpClient().send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataNode = root.path("data");

                List<IndexedEmbedding> indexedList = new ArrayList<>();
                if (dataNode.isArray()) {
                    for (JsonNode item : dataNode) {
                        int index = item.path("index").asInt(indexedList.size());
                        List<Double> vector = new ArrayList<>();
                        for (JsonNode val : item.path("embedding")) {
                            vector.add(val.asDouble());
                        }
                        indexedList.add(new IndexedEmbedding(index, vector));
                    }
                }

                indexedList.sort(Comparator.comparingInt(a -> a.index));
                return indexedList.stream().map(a -> a.vector).toList();
            } else {
                LOG.errorf("Dịch vụ Embedding trả về lỗi HTTP %d", response.statusCode());
                throw new RuntimeException("Lỗi từ dịch vụ Embedding AI: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            LOG.warnf("Gọi API Embedding AI thất bại: %s", e.getMessage());
            throw new RuntimeException("Không thể kết nối đến dịch vụ Embedding AI: " + e.getMessage(), e);
        }
    }

    private record IndexedEmbedding(int index, List<Double> vector) {}

    private String normalizeEndpoint(String path) {
        String base = baseUrl != null ? baseUrl.trim() : "http://localhost:11434/v1";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}

