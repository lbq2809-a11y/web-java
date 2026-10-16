package com.pharmacy.medicine;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Kiểm thử tích hợp (Integration Test) toàn diện cho medicine-service sử dụng Testcontainers (MongoDB 7).
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MedicineServiceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";

    private static String createdCategoryId;
    private static String createdMedicineId;

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

    @Test
    @Order(1)
    void shouldSeedDemoCategoriesAnd30MedicinesOnStartup() {
        // Kiểm tra 6 danh mục DEMO được khởi tạo
        given()
                .when().get("/api/v1/categories")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(6));

        // Kiểm tra 30 thuốc DEMO (gồm cả OTC và ETC) được nạp khi DB trống
        given()
                .queryParam("page", 0)
                .queryParam("size", 50)
                .when().get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(30))
                .body("items", hasSize(30));
    }

    @Test
    @Order(2)
    void shouldSearchFilterAndSortMedicinesCorrectly() {
        // 1. Lọc theo loại thuốc kê đơn (ETC) -> có 12 thuốc ETC trong bộ dữ liệu mẫu
        given()
                .queryParam("prescriptionType", "ETC")
                .queryParam("size", 50)
                .when().get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(12))
                .body("items.prescriptionType", hasItem("ETC"));

        // 2. Tìm kiếm theo từ khóa hoạt chất "Paracetamol" và sắp xếp theo giá tăng dần
        given()
                .queryParam("keyword", "Paracetamol")
                .queryParam("sortBy", "price")
                .queryParam("sortDir", "asc")
                .when().get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(3))
                .body("items[0].code", equalTo("MED-OTC-001"));
    }

    @Test
    @Order(3)
    void shouldForbidUnauthenticatedOrCustomerFromCreatingCategoryAndMedicine() {
        Map<String, Object> categoryPayload = Map.of(
                "code", "CAT_TEST_DERMA",
                "name", "Da liễu Test",
                "description", "Danh mục test"
        );

        // Không có JWT token -> 401 Unauthorized
        given()
                .contentType(ContentType.JSON)
                .body(categoryPayload)
                .when().post("/api/v1/categories")
                .then()
                .statusCode(401);

        // Có JWT nhưng vai trò KHACH_HANG -> 403 Forbidden
        String customerToken = generateToken(UUID.randomUUID().toString(), "KHACH_HANG", "Khách Hàng A");
        given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body(categoryPayload)
                .when().post("/api/v1/categories")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(4)
    void shouldAllowPharmacistAndAdminToCreateAndUpdateCategoryAndMedicine() {
        String pharmacistToken = generateToken(UUID.randomUUID().toString(), "DUOC_SI", "DS. Nguyễn Văn Dược");

        // 1. DUOC_SI tạo danh mục mới -> 201 Created
        createdCategoryId = given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "code", "CAT_DERMATOLOGY",
                        "name", "Thuốc Da Liễu",
                        "description", "[DEMO] Nhóm thuốc bôi ngoài da"
                ))
                .when().post("/api/v1/categories")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("code", equalTo("CAT_DERMATOLOGY"))
                .extract().path("id");

        // 2. DUOC_SI tạo thuốc mới thuộc danh mục vừa tạo -> 201 Created
        createdMedicineId = given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .contentType(ContentType.JSON)
                .body(Map.ofEntries(
                        Map.entry("code", "MED-OTC-999"),
                        Map.entry("name", "Bepanthen Balm"),
                        Map.entry("activeIngredient", "Dexpanthenol 5%"),
                        Map.entry("manufacturer", "Bayer"),
                        Map.entry("unit", "Tuýp 30g"),
                        Map.entry("price", 69000.00),
                        Map.entry("imageUrl", "https://placehold.co/400x400?text=MED-OTC-999"),
                        Map.entry("indications", "[Dữ liệu demo] Hỗ trợ chăm sóc và bảo vệ da."),
                        Map.entry("contraindications", "Mẫn cảm với Dexpanthenol."),
                        Map.entry("dosage", "Bôi ngoài da 1-2 lần/ngày."),
                        Map.entry("prescriptionType", "OTC"),
                        Map.entry("categoryId", createdCategoryId)
                ))
                .when().post("/api/v1/medicines")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("categoryName", equalTo("Thuốc Da Liễu"))
                .extract().path("id");

        // 3. Khi đổi tên danh mục -> categoryName trên thuốc được đồng bộ tự động (Extended Reference)
        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "code", "CAT_DERMATOLOGY",
                        "name", "Chăm Sóc Da Liễu",
                        "description", "[DEMO] Nhóm thuốc bôi và chăm sóc da"
                ))
                .when().put("/api/v1/categories/" + createdCategoryId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Chăm Sóc Da Liễu"));

        given()
                .when().get("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(200)
                .body("categoryName", equalTo("Chăm Sóc Da Liễu"));
    }

    @Test
    @Order(5)
    void shouldSupportBatchMedicineLookupAndEnforceMax50IdsLimit() {
        // Lấy thêm 1 thuốc từ danh sách hiện có
        String secondId = given()
                .queryParam("size", 1)
                .when().get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .extract().path("items[0].id");

        // Gọi /api/v1/medicines/batch?ids=... với 2 ID hợp lệ
        given()
                .queryParam("ids", createdMedicineId + "," + secondId)
                .when().get("/api/v1/medicines/batch")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].id", equalTo(createdMedicineId))
                .body("[1].id", equalTo(secondId));

        // Gọi quá 50 ID -> trả lỗi 400 BATCH_SIZE_EXCEEDED chuẩn ErrorResponse
        List<String> fiftyOneIds = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            fiftyOneIds.add(String.format("%024x", i + 1));
        }
        given()
                .queryParam("ids", String.join(",", fiftyOneIds))
                .when().get("/api/v1/medicines/batch")
                .then()
                .statusCode(400)
                .body("code", equalTo("BATCH_SIZE_EXCEEDED"));
    }

    @Test
    @Order(6)
    void shouldCreateReviewsRecalculateAverageByAggregationAndPreventDuplicateReview() {
        String user1Id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String user2Id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String tokenUser1 = generateToken(user1Id, "KHACH_HANG", "Nguyễn Văn Một");
        String tokenUser2 = generateToken(user2Id, "KHACH_HANG", "Trần Thị Hai");

        // User 1 đánh giá 5 sao -> 201 Created
        given()
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(ContentType.JSON)
                .body(Map.of("rating", 5, "comment", "Thuốc chính hãng, đóng gói cẩn thận"))
                .when().post("/api/v1/medicines/" + createdMedicineId + "/reviews")
                .then()
                .statusCode(201)
                .body("userId", equalTo(user1Id))
                .body("rating", equalTo(5));

        // User 1 cố tình đánh giá lần 2 cho cùng thuốc đó -> bị chặn 409 Conflict (REVIEW_ALREADY_EXISTS)
        given()
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(ContentType.JSON)
                .body(Map.of("rating", 4, "comment", "Đánh giá lần 2"))
                .when().post("/api/v1/medicines/" + createdMedicineId + "/reviews")
                .then()
                .statusCode(409)
                .body("code", equalTo("REVIEW_ALREADY_EXISTS"));

        // User 2 đánh giá 4 sao -> 201 Created
        given()
                .header("Authorization", "Bearer " + tokenUser2)
                .contentType(ContentType.JSON)
                .body(Map.of("rating", 4, "comment", "Giao nhanh, dễ sử dụng"))
                .when().post("/api/v1/medicines/" + createdMedicineId + "/reviews")
                .then()
                .statusCode(201);

        // Kiểm tra averageRating và reviewCount được tính lại bằng MongoDB Aggregation: (5 + 4) / 2 = 4.5, reviewCount = 2
        given()
                .when().get("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(200)
                .body("averageRating", equalTo(4.5f))
                .body("reviewCount", equalTo(2));

        // Kiểm tra API liệt kê reviews của thuốc
        given()
                .when().get("/api/v1/medicines/" + createdMedicineId + "/reviews")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(2))
                .body("items", hasSize(2));
    }

    @Test
    @Order(7)
    void shouldSoftDeleteMedicineOnlyByAdminAndGenerateDevToken() {
        // Kiểm tra endpoint sinh token dev (/api/v1/dev/tokens)
        String adminToken = given()
                .queryParam("role", "ADMIN")
                .when().get("/api/v1/dev/tokens")
                .then()
                .statusCode(200)
                .body("role", equalTo("ADMIN"))
                .body("token", notNullValue())
                .extract().path("token");

        // DUOC_SI không được phép xóa thuốc -> 403 Forbidden
        String pharmacistToken = generateToken(UUID.randomUUID().toString(), "DUOC_SI", "DS. Lê Văn B");
        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .when().delete("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(403);

        // ADMIN xóa mềm thuốc -> 204 No Content
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(204);

        // Khách thường xem thuốc đã xóa mềm -> 404 Not Found
        given()
                .when().get("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(404)
                .body("code", equalTo("MEDICINE_NOT_FOUND"));

        // ADMIN vẫn xem được thuốc đã ẩn
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/v1/medicines/" + createdMedicineId)
                .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }
}

