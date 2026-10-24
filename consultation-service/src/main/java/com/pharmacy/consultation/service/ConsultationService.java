package com.pharmacy.consultation.service;

import com.pharmacy.consultation.client.EmbeddingClient;
import com.pharmacy.consultation.client.LlmClient;
import com.pharmacy.consultation.dto.AskRequest;
import com.pharmacy.consultation.dto.AskResponse;
import com.pharmacy.consultation.dto.ConversationResponse;
import com.pharmacy.consultation.dto.ConversationSummaryResponse;
import com.pharmacy.consultation.dto.SuggestedMedicineDto;
import com.pharmacy.consultation.entity.ConversationDocument;
import com.pharmacy.consultation.entity.KnowledgeSource;
import com.pharmacy.consultation.entity.MessageItem;
import com.pharmacy.consultation.entity.RagCacheDocument;
import com.pharmacy.consultation.exception.ApiException;
import com.pharmacy.consultation.repository.ConversationRepository;
import com.pharmacy.consultation.repository.RagCacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ tư vấn thuốc thông minh kết hợp 2 tầng (Tầng 1 Template và Tầng 2 AI RAG + LLM).
 */
@ApplicationScoped
public class ConsultationService {

    private static final Logger LOG = Logger.getLogger(ConsultationService.class);

    @Inject
    RateLimiterService rateLimiterService;

    @Inject
    SafetyGuard safetyGuard;

    @Inject
    MedicineMatcher medicineMatcher;

    @Inject
    IntentClassifier intentClassifier;

    @Inject
    TemplateAnswerGenerator templateGenerator;

    @Inject
    ConversationRepository conversationRepository;

    @Inject
    RagCacheRepository ragCacheRepository;

    @Inject
    VectorSearchService vectorSearchService;

    @Inject
    EmbeddingClient embeddingClient;

    @Inject
    LlmClient llmClient;

    @Inject
    ExtractiveFallbackService extractiveFallbackService;

    @Inject
    TextNormalizer textNormalizer;

    @ConfigProperty(name = "consultation.question.max-length", defaultValue = "500")
    int maxQuestionLength;

    @ConfigProperty(name = "consultation.rag.top-k", defaultValue = "4")
    int topK;

    @ConfigProperty(name = "consultation.rag.similarity-threshold", defaultValue = "0.65")
    double similarityThreshold;

    /**
     * Xử lý hỏi đáp tư vấn thuốc (Tự động định tuyến giữa Tầng 1 và Tầng 2).
     */
    public AskResponse ask(String userId, AskRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Kiểm tra rate limit
        rateLimiterService.checkRateLimit(userId);

        // 2. Kiểm tra độ dài câu hỏi
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "Câu hỏi không được để trống", Response.Status.BAD_REQUEST);
        }
        if (request.getQuestion().length() > maxQuestionLength) {
            throw new ApiException("VALIDATION_ERROR",
                    "Câu hỏi không được vượt quá " + maxQuestionLength + " ký tự", Response.Status.BAD_REQUEST);
        }

        String rawQuestion = request.getQuestion().trim();
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        // 3. Kiểm tra an toàn trước tiên (Cấp cứu 115, tự hại, mang thai, trẻ sơ sinh)
        SafetyGuard.SafetyStatus safety = safetyGuard.check(rawQuestion);
        if (safety == SafetyGuard.SafetyStatus.EMERGENCY) {
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateEmergencyAnswer();
            saveConversation(conversationId, userId, rawQuestion, gen.answer(), List.of(), 1);
            logConsultation(userId, conversationId, "CAP_CUU", 1, startTime);
            return new AskResponse(conversationId, gen.answer(), 1, "CAP_CUU", List.of(), List.of(), gen.disclaimer());
        }

        if (safety == SafetyGuard.SafetyStatus.VULNERABLE) {
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateVulnerableAnswer();
            saveConversation(conversationId, userId, rawQuestion, gen.answer(), List.of(), 1);
            logConsultation(userId, conversationId, "NHOM_NHAY_CAM", 1, startTime);
            return new AskResponse(conversationId, gen.answer(), 1, "NHOM_NHAY_CAM", List.of(), List.of(), gen.disclaimer());
        }

        // 4. Kiểm tra Tầng 1: Nhận diện thuốc & phân loại ý định
        MedicineMatcher.MatchResult matchResult = medicineMatcher.findMatchingMedicines(rawQuestion);

        if (matchResult.getMatches().size() > 1) {
            // Khớp nhiều thuốc (ví dụ cùng hoạt chất) -> Trả danh sách gợi ý để người dùng chọn
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateMultipleMatchAnswer(matchResult.getMatches());
            List<SuggestedMedicineDto> suggestions = matchResult.getMatches().stream()
                    .map(m -> new SuggestedMedicineDto(m.getMedicineId(), m.getName(), m.getActiveIngredient(), m.getPrescriptionType()))
                    .toList();
            saveConversation(conversationId, userId, rawQuestion, gen.answer(), List.of(), 1);
            logConsultation(userId, conversationId, "GOI_Y_THUOC", 1, startTime);
            return new AskResponse(conversationId, gen.answer(), 1, "GOI_Y_THUOC", List.of(), suggestions, gen.disclaimer());
        }

        if (matchResult.isSingleMatch()) {
            CachedMedicine medicine = matchResult.getFirst();
            String intent = intentClassifier.classify(rawQuestion);

            // Nếu câu hỏi khớp rõ 1 thuốc và ý định thuộc 6 nhóm chuẩn -> Trả lời Tầng 1 siêu tốc
            if (!"KHAC".equalsIgnoreCase(intent)) {
                TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateForMedicine(medicine, intent);
                saveConversation(conversationId, userId, rawQuestion, gen.answer(), gen.sources(), 1);
                logConsultation(userId, conversationId, intent, 1, startTime);
                return new AskResponse(conversationId, gen.answer(), 1, intent, gen.sources(), List.of(), gen.disclaimer());
            }
        }

        // 5. Chuyển tiếp sang TẦNG 2 (RAG + Cosine Search + LLM / Extractive Fallback)
        return processTier2Rag(userId, conversationId, rawQuestion, startTime);
    }

    private AskResponse processTier2Rag(String userId, String conversationId, String rawQuestion, long startTime) {
        String normQ = textNormalizer.normalize(rawQuestion);

        // 1. Sinh vector nhúng cho câu hỏi đã chuẩn hóa
        List<Double> queryVector = List.of();
        try {
            queryVector = embeddingClient.embedText(normQ);
        } catch (Exception e) {
            LOG.warnf("Không thể embedding câu hỏi qua API: %s", e.getMessage());
        }

        // 2. Tìm kiếm Top-K đoạn tương đồng bằng Cosine trong RAM
        List<VectorSearchService.ScoredChunk> topChunks = List.of();
        if (queryVector != null && !queryVector.isEmpty()) {
            topChunks = vectorSearchService.search(queryVector, topK, similarityThreshold);
        }

        // 3. Nếu không có đoạn nào đạt ngưỡng tối thiểu -> Trả về chưa đủ thông tin
        if (topChunks.isEmpty()) {
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateUnknownAnswer();
            saveConversation(conversationId, userId, rawQuestion, gen.answer(), List.of(), 1);
            logConsultation(userId, conversationId, "KHAC", 1, startTime);
            return new AskResponse(conversationId, gen.answer(), 1, "KHAC", List.of(), List.of(), gen.disclaimer());
        }

        // 4. Kiểm tra Cache Tầng 2 trong MongoDB
        List<String> sortedMedicineIds = topChunks.stream()
                .map(c -> c.item().medicineId())
                .distinct()
                .sorted()
                .toList();

        String chunksCombined = topChunks.stream()
                .map(c -> c.item().contentHash() != null ? c.item().contentHash() : c.item().text())
                .collect(Collectors.joining(";"));
        String chunksHash = computeSha256(chunksCombined);

        String intent = intentClassifier.classify(rawQuestion);
        if ("KHAC".equalsIgnoreCase(intent)) {
            intent = "RAG_CONSULTATION";
        }

        String cacheKey = computeSha256(intent + "::" + String.join(",", sortedMedicineIds) + "::" + chunksHash);

        Optional<RagCacheDocument> cachedOpt = ragCacheRepository.findByCacheKey(cacheKey);
        if (cachedOpt.isPresent()) {
            RagCacheDocument cached = cachedOpt.get();
            saveConversation(conversationId, userId, rawQuestion, cached.answer, cached.sources, 2);
            logConsultation(userId, conversationId, cached.intent + "_CACHED", 2, startTime);
            return new AskResponse(conversationId, cached.answer, 2, cached.intent, cached.sources, List.of(), templateGenerator.getDisclaimer());
        }

        // 5. Chuẩn bị ngữ cảnh cho LLM
        List<String> contextList = topChunks.stream()
                .map(sc -> String.format("Thuốc: %s | Mục: %s | ID: %s\nNội dung: %s",
                        sc.item().medicineName(), sc.item().section(), sc.item().medicineId(), sc.item().text()))
                .toList();

        List<KnowledgeSource> sources = topChunks.stream()
                .map(sc -> new KnowledgeSource(sc.item().medicineId(), sc.item().medicineName(), sc.item().section()))
                .toList();

        String conversationHistory = getRecentConversationHistory(conversationId);

        // 6. Gọi LLM hoặc Chế độ trích đoạn dự phòng (Extractive Fallback) khi LLM lỗi
        String answerText;
        try {
            answerText = llmClient.generateAnswer(rawQuestion, contextList, conversationHistory);
            if (answerText == null || answerText.isBlank()) {
                throw new RuntimeException("LLM trả về câu trả lời rỗng");
            }
        } catch (Exception e) {
            LOG.warnf("Gọi LLM gián đoạn, kích hoạt Chế độ trích đoạn (Extractive Fallback): %s", e.getMessage());
            ExtractiveFallbackService.FallbackResult fallback = extractiveFallbackService.generateFallback(topChunks);
            answerText = fallback.answer();
            sources = fallback.sources();
        }

        // 7. Lưu câu trả lời vào Cache Tầng 2 trong MongoDB
        try {
            RagCacheDocument newCache = new RagCacheDocument(
                    cacheKey,
                    intent,
                    sortedMedicineIds,
                    chunksHash,
                    answerText,
                    sources,
                    2,
                    Instant.now()
            );
            ragCacheRepository.persist(newCache);
        } catch (Exception e) {
            LOG.warnf("Không thể lưu cache RAG: %s", e.getMessage());
        }

        // 8. Lưu phiên hội thoại
        saveConversation(conversationId, userId, rawQuestion, answerText, sources, 2);
        logConsultation(userId, conversationId, intent, 2, startTime);

        return new AskResponse(
                conversationId,
                answerText,
                2,
                intent,
                sources,
                List.of(),
                templateGenerator.getDisclaimer()
        );
    }

    private String getRecentConversationHistory(String conversationId) {
        Optional<ConversationDocument> docOpt = conversationRepository.findByConversationId(conversationId);
        if (docOpt.isEmpty() || docOpt.get().messages == null || docOpt.get().messages.isEmpty()) {
            return "";
        }
        List<MessageItem> msgs = docOpt.get().messages;
        int start = Math.max(0, msgs.size() - 4);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < msgs.size(); i++) {
            MessageItem m = msgs.get(i);
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    private void saveConversation(String conversationId, String userId, String question,
                                  String answer, List<KnowledgeSource> sources, int tier) {
        Instant now = Instant.now();
        Optional<ConversationDocument> existingOpt = conversationRepository.findByConversationId(conversationId);

        ConversationDocument doc;
        if (existingOpt.isPresent()) {
            doc = existingOpt.get();
        } else {
            String title = question.length() > 50 ? question.substring(0, 50) + "..." : question;
            doc = new ConversationDocument(conversationId, userId, title, now, now);
        }

        doc.messages.add(new MessageItem("user", question, List.of(), 0, now));
        doc.messages.add(new MessageItem("assistant", answer, sources, tier, now));
        doc.updatedAt = now;

        if (existingOpt.isPresent()) {
            conversationRepository.update(doc);
        } else {
            conversationRepository.persist(doc);
        }
    }

    private void logConsultation(String userId, String conversationId, String intent, int tier, long startTime) {
        long timeMs = System.currentTimeMillis() - startTime;
        // TUYỆT ĐỐI KHÔNG log nội dung câu hỏi ở mức INFO để bảo vệ bí mật thông tin y tế
        LOG.infof("Tư vấn hoàn tất: userId=%s, conversationId=%s, intent=%s, tier=%d, timeMs=%d",
                userId, conversationId, intent, tier, timeMs);
    }

    private String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public List<ConversationSummaryResponse> getConversations(String userId) {
        List<ConversationDocument> docs = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return docs.stream()
                .map(d -> new ConversationSummaryResponse(
                        d.conversationId,
                        d.title,
                        d.messages != null ? d.messages.size() : 0,
                        d.createdAt,
                        d.updatedAt
                ))
                .toList();
    }

    public ConversationResponse getConversation(String userId, String conversationId, boolean isAdmin) {
        ConversationDocument doc = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new ApiException("CONVERSATION_NOT_FOUND", "Không tìm thấy phiên hội thoại", Response.Status.NOT_FOUND));

        if (!isAdmin && (doc.userId == null || !doc.userId.equals(userId))) {
            throw new ApiException("FORBIDDEN", "Bạn không có quyền truy cập phiên hội thoại này", Response.Status.FORBIDDEN);
        }

        return new ConversationResponse(
                doc.conversationId,
                doc.title,
                doc.messages,
                doc.createdAt,
                doc.updatedAt
        );
    }

    public void deleteConversation(String userId, String conversationId, boolean isAdmin) {
        ConversationDocument doc = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new ApiException("CONVERSATION_NOT_FOUND", "Không tìm thấy phiên hội thoại", Response.Status.NOT_FOUND));

        if (!isAdmin && (doc.userId == null || !doc.userId.equals(userId))) {
            throw new ApiException("FORBIDDEN", "Bạn không có quyền xóa phiên hội thoại này", Response.Status.FORBIDDEN);
        }

        conversationRepository.deleteByConversationId(conversationId);
    }
}
