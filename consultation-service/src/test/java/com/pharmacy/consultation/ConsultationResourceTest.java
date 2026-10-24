package com.pharmacy.consultation;

import com.pharmacy.consultation.client.MedicineServiceClient;
import com.pharmacy.consultation.dto.AskRequest;
import com.pharmacy.consultation.dto.MedicineResponse;
import com.pharmacy.consultation.dto.PagedResponse;
import com.pharmacy.consultation.repository.ConversationRepository;
import com.pharmacy.consultation.repository.KnowledgeChunkRepository;
import com.pharmacy.consultation.service.KnowledgeIngestionService;
import com.pharmacy.consultation.service.RateLimiterService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Bộ kiểm thử toàn diện cho consultation-service (Tầng 1 Template, Nhận diện thuốc, An toàn, Lịch sử hội thoại).
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
                        "Điều trị các bệnh nhiễm trùng đường hô hấp, tai mũi họng",
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
                        "Giảm đau hạ sốt nhanh chóng",
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
                        "Hạ sốt, giảm các cơn đau vừa và nhẹ",
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

    @Test
    @Order(1)
    @DisplayName("1. Khớp tên thuốc có dấu và không dấu")
    void testMatchMedicineWithDiacriticsAndNoDiacritics() {
        // Có dấu
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

        // Không dấu
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
        // "panadoll" thay vì "Panadol"
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
        // Cả Panadol Extra và Efferalgan 500mg đều chứa Paracetamol
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
    @DisplayName("8. Câu hỏi không rõ thuốc hoặc ý định KHAC trả về chưa đủ thông tin kèm disclaimer")
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

        // User 1 hỏi và tạo phiên convId
        AskRequest req = new AskRequest(convId, "Panadol Extra uống như thế nào?");
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(200);

        // User 1 xem chi tiết phiên convId -> 200 OK
        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().get("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(200)
                .body("conversationId", equalTo(convId))
                .body("messages", hasSize(2));

        // User 2 cố tình xem phiên của User 1 -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().get("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // User 2 cố tình xóa phiên của User 1 -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().delete("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // User 1 xem danh sách phiên -> có convId
        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().get("/api/v1/consultations/conversations")
                .then().statusCode(200)
                .body("conversationId", hasItem(convId));

        // User 2 xem danh sách phiên -> KHÔNG có convId
        given()
                .header("Authorization", "Bearer " + user2Token)
                .when().get("/api/v1/consultations/conversations")
                .then().statusCode(200)
                .body("conversationId", not(hasItem(convId)));

        // User 1 xóa phiên của mình -> 204 No Content
        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().delete("/api/v1/consultations/conversations/" + convId)
                .then().statusCode(204);
    }

    @Test
    @Order(10)
    @DisplayName("10. API reindex chỉ ADMIN mới được gọi")
    void testReindexOnlyAdmin() {
        // Khách hàng gọi -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + user1Token)
                .when().post("/api/v1/consultations/admin/reindex")
                .then().statusCode(403);

        // Admin gọi -> 200 OK
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
        // Trống
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(new AskRequest(null, "   "))
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Dài hơn 500 ký tự
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

        // Gửi 20 câu hỏi liên tiếp (ngưỡng tối đa 20 req / 60s)
        for (int i = 0; i < 20; i++) {
            given()
                    .header("Authorization", "Bearer " + user1Token)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .when().post("/api/v1/consultations/ask")
                    .then().statusCode(200);
        }

        // Lượt gọi thứ 21 -> Bị từ chối với HTTP 429
        given()
                .header("Authorization", "Bearer " + user1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/api/v1/consultations/ask")
                .then().statusCode(429)
                .body("code", equalTo("RATE_LIMIT_EXCEEDED"));
    }
}

