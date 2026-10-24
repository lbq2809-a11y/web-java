package com.pharmacy.consultation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực LlmClient tương thích chuẩn OpenAI (endpoint /v1/chat/completions).
 */
@ApplicationScoped
public class OpenAiLlmClient implements LlmClient {

    private static final Logger LOG = Logger.getLogger(OpenAiLlmClient.class);

    @ConfigProperty(name = "consultation.ai.base-url", defaultValue = "http://localhost:11434/v1")
    String baseUrl;

    @ConfigProperty(name = "consultation.ai.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "consultation.ai.chat-model", defaultValue = "gpt-4o-mini")
    String model;

    @ConfigProperty(name = "consultation.ai.timeout-seconds", defaultValue = "10")
    int timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    private String systemPrompt = "Bạn là Trợ lý AI tư vấn thuốc. Chỉ trả lời dựa vào ngữ cảnh <context> được cung cấp.";
    private HttpClient httpClient;

    void onStart(@Observes StartupEvent ev) {
        loadSystemPrompt();
    }

    public synchronized void loadSystemPrompt() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("system-prompt.txt")) {
            if (is != null) {
                systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                LOG.info("Đã nạp system-prompt.txt cho LlmClient thành công.");
            } else {
                LOG.warn("Không tìm thấy file system-prompt.txt, sử dụng prompt mặc định.");
            }
        } catch (Exception e) {
            LOG.error("Lỗi khi đọc system-prompt.txt", e);
        }
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
        return httpClient;
    }

    @Override
    public String generateAnswer(String question, List<String> contextChunks, String conversationHistory) {
        if (question == null || question.isBlank()) {
            return "";
        }

        try {
            String endpoint = normalizeEndpoint("/chat/completions");

            StringBuilder contextBuilder = new StringBuilder("<context>\n");
            if (contextChunks != null) {
                for (int i = 0; i < contextChunks.size(); i++) {
                    contextBuilder.append(String.format("[Đoạn %d]\n%s\n\n", i + 1, contextChunks.get(i)));
                }
            }
            contextBuilder.append("</context>");

            StringBuilder userContent = new StringBuilder();
            userContent.append(contextBuilder).append("\n\n");
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                userContent.append("Lịch sử hội thoại trước đó:\n").append(conversationHistory).append("\n\n");
            }
            userContent.append("Câu hỏi của người dùng:\n").append(question.trim());

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent.toString())
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.0);
            requestBody.put("messages", messages);

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
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText("");
                }
                return "";
            } else {
                LOG.errorf("Dịch vụ LLM trả về mã lỗi HTTP %d", response.statusCode());
                throw new RuntimeException("Lỗi từ dịch vụ LLM: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            LOG.warnf("Gọi API LLM AI thất bại: %s", e.getMessage());
            throw new RuntimeException("Không thể nhận câu trả lời từ LLM: " + e.getMessage(), e);
        }
    }

    @Override
    public String streamAnswer(String question, List<String> contextChunks, String conversationHistory) {
        return generateAnswer(question, contextChunks, conversationHistory);
    }

    private String normalizeEndpoint(String path) {
        String base = baseUrl != null ? baseUrl.trim() : "http://localhost:11434/v1";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}

