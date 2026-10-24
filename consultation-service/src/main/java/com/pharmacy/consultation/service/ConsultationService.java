package com.pharmacy.consultation.service;

import com.pharmacy.consultation.dto.AskRequest;
import com.pharmacy.consultation.dto.AskResponse;
import com.pharmacy.consultation.dto.ConversationResponse;
import com.pharmacy.consultation.dto.ConversationSummaryResponse;
import com.pharmacy.consultation.dto.SuggestedMedicineDto;
import com.pharmacy.consultation.entity.ConversationDocument;
import com.pharmacy.consultation.entity.KnowledgeSource;
import com.pharmacy.consultation.entity.MessageItem;
import com.pharmacy.consultation.exception.ApiException;
import com.pharmacy.consultation.repository.ConversationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Xử lý nghiệp vụ tư vấn thuốc Tầng 1 và quản lý các phiên hội thoại.
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

    @ConfigProperty(name = "consultation.question.max-length", defaultValue = "500")
    int maxQuestionLength;

    /**
     * Xử lý hỏi đáp tư vấn thuốc (Tầng 1 Template-based & An toàn).
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

        String answerText;
        String intent = "KHAC";
        int tier = 1;
        List<KnowledgeSource> sources = new ArrayList<>();
        List<SuggestedMedicineDto> suggestions = new ArrayList<>();
        String disclaimer = templateGenerator.getDisclaimer();

        // 3. Kiểm tra an toàn trước tiên (Cấp cứu 115, tự hại, mang thai, trẻ sơ sinh)
        SafetyGuard.SafetyStatus safety = safetyGuard.check(rawQuestion);
        if (safety == SafetyGuard.SafetyStatus.EMERGENCY) {
            intent = "CAP_CUU";
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateEmergencyAnswer();
            answerText = gen.answer();
            disclaimer = gen.disclaimer();
        } else if (safety == SafetyGuard.SafetyStatus.VULNERABLE) {
            intent = "NHOM_NHAY_CAM";
            TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateVulnerableAnswer();
            answerText = gen.answer();
            disclaimer = gen.disclaimer();
        } else {
            // 4. Nhận diện thuốc
            MedicineMatcher.MatchResult matchResult = medicineMatcher.findMatchingMedicines(rawQuestion);

            if (matchResult.getMatches().size() > 1) {
                // Khớp nhiều thuốc (ví dụ cùng hoạt chất) -> Trả danh sách gợi ý để người dùng chọn
                intent = "GOI_Y_THUOC";
                TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateMultipleMatchAnswer(matchResult.getMatches());
                answerText = gen.answer();
                disclaimer = gen.disclaimer();

                for (CachedMedicine m : matchResult.getMatches()) {
                    suggestions.add(new SuggestedMedicineDto(
                            m.getMedicineId(),
                            m.getName(),
                            m.getActiveIngredient(),
                            m.getPrescriptionType()
                    ));
                }
            } else if (matchResult.isEmpty()) {
                // Không nhận diện được thuốc -> Trả lời không đủ thông tin
                intent = "KHAC";
                TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateUnknownAnswer();
                answerText = gen.answer();
                disclaimer = gen.disclaimer();
            } else {
                // Khớp đúng 1 thuốc -> Phân loại ý định và điền mẫu
                CachedMedicine medicine = matchResult.getFirst();
                intent = intentClassifier.classify(rawQuestion);

                if ("KHAC".equalsIgnoreCase(intent)) {
                    TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateUnknownAnswer();
                    answerText = gen.answer();
                    disclaimer = gen.disclaimer();
                } else {
                    TemplateAnswerGenerator.GeneratedAnswer gen = templateGenerator.generateForMedicine(medicine, intent);
                    answerText = gen.answer();
                    sources = gen.sources();
                    disclaimer = gen.disclaimer();
                }
            }
        }

        // 5. Lưu lịch sử hội thoại vào MongoDB
        saveConversation(conversationId, userId, rawQuestion, answerText, sources, tier);

        long timeMs = System.currentTimeMillis() - startTime;
        // TUYỆT ĐỐI KHÔNG log nội dung câu hỏi ở mức INFO để bảo mật thông tin sức khỏe của người dùng
        LOG.infof("Tư vấn hoàn tất: userId=%s, conversationId=%s, intent=%s, tier=%d, timeMs=%d",
                userId, conversationId, intent, tier, timeMs);

        return new AskResponse(
                conversationId,
                answerText,
                tier,
                intent,
                sources,
                suggestions,
                disclaimer
        );
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

        // Thêm câu hỏi của user và câu trả lời của AI
        doc.messages.add(new MessageItem("user", question, List.of(), 0, now));
        doc.messages.add(new MessageItem("assistant", answer, sources, tier, now));
        doc.updatedAt = now;

        if (existingOpt.isPresent()) {
            conversationRepository.update(doc);
        } else {
            conversationRepository.persist(doc);
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

