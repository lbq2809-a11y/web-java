package com.pharmacy.consultation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.consultation.client.MedicineServiceClient;
import com.pharmacy.consultation.client.MockLlmClient;
import com.pharmacy.consultation.dto.AskRequest;
import com.pharmacy.consultation.dto.MedicineResponse;
import com.pharmacy.consultation.dto.PagedResponse;
import com.pharmacy.consultation.repository.ConversationRepository;
import com.pharmacy.consultation.repository.KnowledgeChunkRepository;
import com.pharmacy.consultation.repository.RagCacheRepository;
import com.pharmacy.consultation.service.KnowledgeIngestionService;
import com.pharmacy.consultation.service.RateLimiterService;
import com.pharmacy.consultation.service.VectorItem;
import com.pharmacy.consultation.service.VectorSearchService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Bộ kiểm thử toàn diện cho consultation-service (Tầng 1 Template + Tầng 2 AI RAG, Cache, Paraphrase, Evaluation).
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConsultationResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";

    @InjectMock
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @Inject
    KnowledgeIngestionService knowledgeIngestionService;

    @Inject
    RateLimiterService rateLimiterService;

    @Inject
    ConversationRepository conversationRepository;

    @Inject
    KnowledgeChunkRepository chunkRepository;

    @Inject
    RagCacheRepository ragCacheRepository;

    @Inject
    VectorSearchService vectorSearchService;

    @Inject
    MockLlmClient mockLlmClient;

    @Inject
    ObjectMapper objectMapper;

    private static final String USER_1_ID = "11111111-1111-1111-1111-111111111111";
    private static final String USER_2_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";

    private String user1Token;
    private String user2Token;
    private String adminToken;

    private String generateToken(String userId, String role, String fullName) {
        return Jwt.issuer(ISSUER)
                .subject(userId)
                .upn(role.toLowerCase() + "@pharmacy.local")
                .claim("email", role.toLowerCase() + "@pharmacy.local")
                .claim("name", fullName)
                .groups(Set.of(role))
                .expiresIn(Duration.ofHours(1))
                .sign();
    }

    @BeforeEach
    void setup() {
        user1Token = generateToken(USER_1_ID, "KHACH_HANG", "Khách Hàng 1");
        user2Token = generateToken(USER_2_ID, "KHACH_HANG", "Khách Hàng 2");
        adminToken = generateToken(ADMIN_ID, "ADMIN", "Quản Trị Viên");

        rateLimiterService.reset();
        mockLlmClient.setSimulateFailure(false);

        List<MedicineResponse> mockMedicines = List.of(
                new MedicineResponse(
                        "6ad23be6c034632667baaa01",
                        "MED-AUG-1G",
                        "Augmentin 1g",
                        "Amoxicillin + Clavulanic acid",
                        "GSK",
                        "Hộp",
                        BigDecimal.valueOf(285000),
                        "https://img.local/aug.jpg",
                        "Điều trị các bệnh nhiễm trùng đường hô hấp, tai mũi họng, viêm phế quản",
                        "Dị ứng với penicillin hoặc beta-lactam, suy gan nặng",
                        "Người lớn uống 1 viên mỗi 12 giờ sau bữa ăn",
                        "ETC",
                        "cat01",
                        "Kháng sinh",
                        true,
                        4.5,
                        10,
                        Instant.now(),
                        Instant.now()
                ),
                new MedicineResponse(
                        "6ad23be6c034632667baaa02",
                        "MED-PAN-EXT",
                        "Panadol Extra",
                        "Paracetamol + Caffeine",
                        "GSK",
                        "Hộp",
                        BigDecimal.valueOf(55000),
                        "https://img.local/pan.jpg",
                        "Giảm đau hạ sốt nhanh chóng, đau đầu, đau răng",
                        "Mẫn cảm với paracetamol, suy gan nặng",
                        "Người lớn uống 1-2 viên mỗi 4-6 giờ",
                        "OTC",
                        "cat02",
                        "Giảm đau - Hạ sốt",
                        true,
                        4.8,
                        25,
                        Instant.now(),
                        Instant.now()
                ),
                new MedicineResponse(
                        "6ad23be6c034632667baaa03",
                        "MED-EFF-500",
                        "Efferalgan 500mg",
                        "Paracetamol",
                        "UPSA",
                        "Hộp",
                        BigDecimal.valueOf(68000),
                        "https://img.local/eff.jpg",
                        "Hạ sốt, giảm các cơn đau vừa và nhẹ, đau đầu, đau răng",
                        "Suy gan nặng, dị ứng paracetamol",
                        "Hòa tan viên sủi trong nước, uống cách nhau 4-6 giờ",
                        "OTC",
                        "cat02",
                        "Giảm đau - Hạ sốt",
                        true,
                        4.6,
                        15,
                        Instant.now(),
                        Instant.now()
                )
        );

        Mockito.when(medicineServiceClient.getMedicines(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
                .thenAnswer(inv -> {
                    int page = inv.getArgument(0);
                    if (page == 0) {
                        return new PagedResponse<>(mockMedicines, 0, 100, mockMedicines.size(), 1);
                    }
                    return new PagedResponse<>(List.of(), page, 100, mockMedicines.size(), 1);
                });

        knowledgeIngestionService.reindex();
    }

    // ==================== CÁC TEST TẦNG 1 (1 - 12) ====================

    @Test
    @Order(1)
    @DisplayName("1. Khớp tên thuốc có dấu và không dấu")
    void testMatchMedicineWithDiacriticsAndNoDiacritics() {
        AskRequest req1 = new AskRequest(null, "Augmentin 1g công dụng là gì?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req1)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("tier", equalTo(1))
                .body("intent", equalTo("CHI_DINH"))
                .body("answer", containsString("Augmentin 1g"))
                .body("answer", containsString("nhiễm trùng đường hô hấp"));

        AskRequest req2 = new AskRequest(null, "augmentin 1g uong the nao?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req2)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("tier", equalTo(1))
                .body("intent", equalTo("CACH_DUNG"))
                .body("answer", containsString("1 viên mỗi 12 giờ"));
    }

    @Test
    @Order(2)
    @DisplayName("2. Khớp mờ Levenshtein khi gõ sai chính tả nhẹ")
    void testMatchMedicineFuzzyLevenshtein() {
        AskRequest req = new AskRequest(null, "Thuốc panadoll uống sao?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("tier", equalTo(1))
                .body("intent", equalTo("CACH_DUNG"))
                .body("answer", containsString("Panadol Extra"))
                .body("answer", containsString("1-2 viên mỗi 4-6 giờ"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Phân loại chính xác 6 ý định và điền đúng trường dữ liệu theo mẫu")
    void testClassifyIntentsAndFillTemplate() {
        // THANH_PHAN
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Thành phần của thuốc Panadol Extra là gì?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("THANH_PHAN"))
                .body("answer", containsString("Paracetamol + Caffeine"));

        // CHI_DINH
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Công dụng của thuốc Panadol Extra?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("CHI_DINH"))
                .body("answer", containsString("Giảm đau hạ sốt"));

        // CHONG_CHI_DINH
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Ai không nên dùng Panadol Extra và lưu ý gì?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("CHONG_CHI_DINH"))
                .body("answer", containsString("Mẫn cảm với paracetamol"));

        // CACH_DUNG
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Panadol Extra uống như thế nào?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("CACH_DUNG"))
                .body("answer", containsString("1-2 viên mỗi 4-6 giờ"));

        // LOAI_THUOC
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Panadol Extra là thuốc kê đơn hay không?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("LOAI_THUOC"))
                .body("answer", containsString("thuốc không kê đơn (OTC)"));

        // GIA
        given().header("Authorization", "Bearer " + user1Token).contentType(ContentType.JSON)
                .body(new AskRequest(null, "Panadol Extra giá bao nhiêu tiền?"))
                .when().post("/api/v1/consultations/ask").then().statusCode(200)
                .body("intent", equalTo("GIA"))
                .body("answer", anyOf(containsString("55,000"), containsString("55.000")));
    }

    @Test
    @Order(4)
    @DisplayName("4. Thuốc kê đơn ETC tự động có cảnh báo bắt buộc có đơn bác sĩ")
    void testEtcMedicineWarning() {
        AskRequest req = new AskRequest(null, "Augmentin 1g dùng để làm gì?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("answer", containsString("thuốc kê đơn (ETC)"))
                .body("answer", containsString("bắt buộc phải có"));
    }

    @Test
    @Order(5)
    @DisplayName("5. Khớp nhiều thuốc cùng hoạt chất trả về danh sách gợi ý để người dùng chọn")
    void testMultipleMatchingMedicinesReturnsSuggestions() {
        AskRequest req = new AskRequest(null, "Thuốc paracetamol uống thế nào?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("intent", equalTo("GOI_Y_THUOC"))
                .body("suggestedMedicines", hasSize(greaterThanOrEqualTo(2)))
                .body("suggestedMedicines.name", hasItems("Panadol Extra", "Efferalgan 500mg"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Phát hiện từ khóa cấp cứu / nguy kịch / tự hại và trả lời ngay")
    void testSafetyEmergencyKeywords() {
        AskRequest req1 = new AskRequest(null, "Tôi bị co giật và khó thở cần cấp cứu ngay");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req1)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("intent", equalTo("CAP_CUU"))
                .body("answer", containsString("115"))
                .body("disclaimer", notNullValue());

        AskRequest req2 = new AskRequest(null, "Uống thuốc quá liều muốn tự tử");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req2)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("intent", equalTo("CAP_CUU"))
                .body("answer", containsString("cấp cứu 115"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Phát hiện đối tượng nhạy cảm (bà bầu, trẻ sơ sinh)")
    void testSafetyPregnancyKeywords() {
        AskRequest req = new AskRequest(null, "Phụ nữ mang thai có dùng được thuốc này không?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("intent", equalTo("NHOM_NHAY_CAM"))
                .body("answer", containsString("bác sĩ chuyên khoa"))
                .body("disclaimer", notNullValue());
    }

    @Test
    @Order(8)
    @DisplayName("8. Câu hỏi không rõ thuốc và không đạt ngưỡng trả về chưa đủ thông tin kèm disclaimer")
    void testUnknownMedicineOrIntentKhac() {
        AskRequest req = new AskRequest(null, "Hôm nay thời tiết thế nào?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("intent", equalTo("KHAC"))
                .body("answer", containsString("chưa có đủ thông tin"))
                .body("disclaimer", notNullValue());
    }

    @Test
    @Order(9)
    @DisplayName("9. Phân quyền và cô lập lịch sử hội thoại giữa các người dùng")
    void testConversationHistoryIsolation() {
        String convId = UUID.randomUUID().toString();

        AskRequest req = new AskRequest(convId, "Panadol Extra uống như thế nào?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().get("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(200)
                .body("conversationId", equalTo(convId))
                .body("messages", hasSize(2));

        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().get("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().delete("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().get("/api/v1/consultations/conversations")
                .then().statusCode(200)
                .body("conversationId", hasItem(convId));

        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().get("/api/v1/consultations/conversations")
                .then().statusCode(200)
                .body("conversationId", not(hasItem(convId)));

        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().delete("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(204);
    }

    @Test
    @Order(10)
    @DisplayName("10. API reindex chỉ ADMIN mới được gọi")
    void testReindexOnlyAdmin() {
        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().post("/api/v1/consultations/admin/reindex")
                .then().statusCode(403);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().post("/api/v1/consultations/admin/reindex")
                .then().statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("totalMedicines", greaterThanOrEqualTo(3));
    }

    @Test
    @Order(11)
    @DisplayName("11. Xác thực độ dài câu hỏi (trống hoặc vượt quá 500 ký tự trả về 400)")
    void testQuestionLengthValidation() {
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(new AskRequest(null, "   "))
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        String longQuestion = "a".repeat(501);
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(new AskRequest(null, longQuestion))
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @Order(12)
    @DisplayName("12. Kiểm tra Rate Limiting (vượt quá ngưỡng cho phép trả về HTTP 429)")
    void testRateLimiting() {
        rateLimiterService.reset();
        AskRequest req = new AskRequest(null, "Panadol Extra uống như thế nào?");

        for (int i = 0; i < 20; i++) {
            given()
                    .header("Authorization", "Bearer " + user1Token)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .when().post("/api/v1/consultations/ask")
                    .then().statusCode(200);
        }

        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(429)
                .body("code", equalTo("RATE_LIMIT_EXCEEDED"));
    }

    // ==================== CÁC TEST TẦNG 2 RAG & AI (13 - 20) ====================

    @Test
    @Order(13)
    @DisplayName("13. Kiểm tra tính toán Cosine Similarity và sắp xếp xác định")
    void testCosineSimilarityCalculation() {
        List<Double> v1 = List.of(1.0, 0.0, 0.0);
        List<Double> v2 = List.of(1.0, 0.0, 0.0);
        List<Double> v3 = List.of(0.0, 1.0, 0.0);

        Assertions.assertEquals(1.0, vectorSearchService.cosineSimilarity(v1, v2), 1e-5);
        Assertions.assertEquals(0.0, vectorSearchService.cosineSimilarity(v1, v3), 1e-5);

        // Vector câu hỏi đau đầu
        List<Double> qVec = List.of(0.9, 0.8, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        List<VectorSearchService.ScoredChunk> results = vectorSearchService.search(qVec, 4, 0.5);

        Assertions.assertFalse(results.isEmpty());
        // Khẳng định điểm sắp xếp giảm dần
        for (int i = 0; i < results.size() - 1; i++) {
            Assertions.assertTrue(results.get(i).score() >= results.get(i + 1).score());
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. Câu hỏi mở không nêu tên thuốc kích hoạt Tầng 2 (RAG + LLM, tier = 2)")
    void testTier2OpenQuestionRetrieval() {
        AskRequest req = new AskRequest(null, "Bị đau đầu nên uống thuốc gì để giảm đau nhanh?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("tier", equalTo(2))
                .body("sources", not(empty()))
                .body("sources.medicineName", anyOf(hasItem("Panadol Extra"), hasItem("Efferalgan 500mg")))
                .body("answer", containsString("Tóm tắt thông tin"));
    }

    @Test
    @Order(15)
    @DisplayName("15. Câu hỏi ngoài phạm vi y dược dưới ngưỡng tương đồng trả về chưa đủ thông tin")
    void testTier2BelowSimilarityThresholdReturnsUnknown() {
        AskRequest req = new AskRequest(null, "Thủ đô của nước Pháp là gì và diện tích bao nhiêu?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then()
                .statusCode(200)
                .body("tier", equalTo(1))
                .body("intent", equalTo("KHAC"))
                .body("answer", containsString("chưa có đủ thông tin"));
    }

    @Test
    @Order(16)
    @DisplayName("16. Chế độ trích đoạn (Extractive Fallback) khi LLM lỗi mạng / hết hạn mức (KHÔNG trả 500)")
    void testExtractiveFallbackWhenLlmFails() {
        mockLlmClient.setSimulateFailure(true);
        try {
            AskRequest req = new AskRequest(null, "Bị viêm họng và nhiễm khuẩn đường hô hấp uống thuốc gì?");
            given()
                    .header("Authorization", "Bearer " + user1Token)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .when().post("/api/v1/consultations/ask")
                    .then()
                    .statusCode(200)
                    .body("tier", equalTo(2))
                    .body("answer", containsString("Trích dẫn trực tiếp từ cơ sở dữ liệu nhà thuốc"))
                    .body("sources.medicineName", hasItem("Augmentin 1g"));
        } finally {
            mockLlmClient.setSimulateFailure(false);
        }
    }

    @Test
    @Order(17)
    @DisplayName("17. Cache câu trả lời Tầng 2 trong MongoDB hoạt động")
    void testRagAnswerCaching() {
        // Gửi câu hỏi lần 1
        AskRequest req = new AskRequest(null, "Triệu chứng đau đầu sốt nhẹ nên dùng thuốc nào?");
        Response res1 = given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(200).extract().response();

        String answer1 = res1.path("answer");
        Assertions.assertTrue(ragCacheRepository.count() > 0);

        // Gửi lại cùng nội dung hoặc cùng khóa RAG -> nhận lại câu trả lời từ cache
        Response res2 = given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(200).extract().response();

        String answer2 = res2.path("answer");
        Assertions.assertEquals(answer1, answer2);
    }

    @Test
    @Order(18)
    @DisplayName("18. Vô hiệu hóa và xóa toàn bộ RAG Cache khi ADMIN reindex")
    void testCacheInvalidationOnReindex() {
        // Tạo sẵn cache
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(new AskRequest(null, "Bị đau đầu nên uống thuốc gì để giảm đau nhanh?"))
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(200);

        Assertions.assertTrue(ragCacheRepository.count() > 0);

        // ADMIN reindex -> xóa sạch cache
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().post("/api/v1/consultations/admin/reindex")
                .then().statusCode(200);

        Assertions.assertEquals(0, ragCacheRepository.count());
    }

    @Test
    @Order(19)
    @DisplayName("19. Kiểm tra tính nhất quán qua các nhóm Paraphrase (Test F.2)")
    void testParaphraseConsistency() throws Exception {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("paraphrase-groups.json")) {
            Assertions.assertNotNull(is, "Không tìm thấy file paraphrase-groups.json");
            List<Map<String, Object>> groups = objectMapper.readValue(is, new TypeReference<>() {});
            Assertions.assertTrue(groups.size() >= 10, "Yêu cầu ít nhất 10 nhóm paraphrase");

            for (Map<String, Object> group : groups) {
                rateLimiterService.reset();
                String expectedMedId = (String) group.get("expectedMedicineId");
                @SuppressWarnings("unchecked")
                List<String> questions = (List<String>) group.get("questions");

                String commonFirstMedicineName = null;

                for (String q : questions) {
                    Response res = given()
                            .header("Authorization", "Bearer " + user1Token)
                            .contentType(ContentType.JSON)
                            .body(new AskRequest(null, q))
                            .when().post("/api/v1/consultations/ask")
                            .then().statusCode(200)
                            .extract().response();

                    List<Map<String, Object>> sources = res.path("sources");
                    List<Map<String, Object>> suggestions = res.path("suggestedMedicines");
                    String answer = res.path("answer");

                    boolean matched = false;
                    String matchedName = null;

                    if (sources != null && !sources.isEmpty()) {
                        for (Map<String, Object> s : sources) {
                            if (expectedMedId.equals(s.get("medicineId"))) {
                                matched = true;
                                matchedName = (String) s.get("medicineName");
                                break;
                            }
                        }
                    }

                    if (!matched && suggestions != null && !suggestions.isEmpty()) {
                        for (Map<String, Object> s : suggestions) {
                            if (expectedMedId.equals(s.get("medicineId"))) {
                                matched = true;
                                matchedName = (String) s.get("name");
                                break;
                            }
                        }
                    }

                    if (!matched && answer != null) {
                        if (answer.contains("Augmentin") && "6ad23be6c034632667baaa01".equals(expectedMedId)) matched = true;
                        if (answer.contains("Panadol") && "6ad23be6c034632667baaa02".equals(expectedMedId)) matched = true;
                        if (answer.contains("Efferalgan") && "6ad23be6c034632667baaa03".equals(expectedMedId)) matched = true;
                    }

                    Assertions.assertTrue(matched, String.format("Câu hỏi '%s' không truy xuất được thuốc mong đợi %s", q, expectedMedId));

                    if (commonFirstMedicineName == null && matchedName != null) {
                        commonFirstMedicineName = matchedName;
                    }
                }
            }
        }
    }

    @Test
    @Order(20)
    @DisplayName("20. Đánh giá tỷ lệ truy xuất đúng trên bộ câu hỏi thực tế (Test F.4, Hit Rate >= 85%)")
    void testRetrievalEvaluation() throws Exception {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("eval-questions.json")) {
            Assertions.assertNotNull(is, "Không tìm thấy file eval-questions.json");
            List<Map<String, Object>> evalQuestions = objectMapper.readValue(is, new TypeReference<>() {});
            Assertions.assertTrue(evalQuestions.size() >= 20, "Bộ eval-questions.json cần ít nhất 20 câu hỏi");

            int hits = 0;
            int total = evalQuestions.size();

            for (Map<String, Object> item : evalQuestions) {
                rateLimiterService.reset();
                String question = (String) item.get("question");
                String expectedId = (String) item.get("expectedMedicineId");
                String expectedName = (String) item.get("expectedMedicineName");

                Response res = given()
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(ContentType.JSON)
                        .body(new AskRequest(null, question))
                        .when().post("/api/v1/consultations/ask")
                        .then().statusCode(200)
                        .extract().response();

                List<Map<String, Object>> sources = res.path("sources");
                List<Map<String, Object>> suggestions = res.path("suggestedMedicines");
                String answer = res.path("answer");

                boolean hit = false;
                if (sources != null) {
                    for (Map<String, Object> s : sources) {
                        if (expectedId.equals(s.get("medicineId")) || expectedName.equalsIgnoreCase((String) s.get("medicineName"))) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (!hit && suggestions != null) {
                    for (Map<String, Object> s : suggestions) {
                        if (expectedId.equals(s.get("medicineId")) || expectedName.equalsIgnoreCase((String) s.get("name"))) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (!hit && answer != null && answer.contains(expectedName)) {
                    hit = true;
                }

                if (hit) {
                    hits++;
                }
            }

            double hitRate = ((double) hits / total) * 100.0;
            System.out.printf("=== KẾT QUẢ ĐÁNH GIÁ TRUY XUẤT RAG (TEST F.4): %d/%d (%.2f%%) ===%n", hits, total, hitRate);
            Assertions.assertTrue(hitRate >= 85.0, String.format("Tỷ lệ truy xuất đạt %.2f%%, thấp hơn ngưỡng yêu cầu 85%%", hitRate));
        }
    }
}
