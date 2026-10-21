package com.pharmacy.prescription.resource;

import com.pharmacy.prescription.dto.ApproveItemRequest;
import com.pharmacy.prescription.dto.ApprovePrescriptionRequest;
import com.pharmacy.prescription.dto.LinkOrderRequest;
import com.pharmacy.prescription.dto.RejectPrescriptionRequest;
import com.pharmacy.prescription.dto.ValidateItemRequest;
import com.pharmacy.prescription.dto.ValidatePrescriptionRequest;
import com.pharmacy.prescription.service.MinioStorageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Bộ kiểm thử tích hợp (Integration Test) cho prescription-service.
 * Bao phủ đầy đủ 11 ca kiểm thử theo yêu cầu: Upload, Kiểm tra định dạng/kích thước,
 * Phân quyền, Duyệt/Từ chối, Duyệt lại, Hết hạn, Validate và Idempotent Link Order.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrescriptionResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";

    private static final String CUSTOMER_A_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String CUSTOMER_B_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PHARMACIST_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String ADMIN_ID      = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    private static String createdPrescriptionId;
    private static String prescriptionToRejectId;
    private static String expiredPrescriptionId;

    @InjectMock
    MinioStorageService minioStorageService;

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
    void setUpMock() {
        // Mặc định mock MinioStorageService tải ảnh và upload trả về kết quả hợp lệ
        Mockito.when(minioStorageService.uploadPrescriptionImage(any()))
                .thenReturn(new MinioStorageService.UploadResult(
                        "prescriptions/2026/10/mock-image.jpg",
                        "image/jpeg",
                        1024L
                ));

        Mockito.when(minioStorageService.getPrescriptionImageStream(any()))
                .thenReturn(new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    // =========================================================================================
    // Ca 1: Khách hàng upload đơn thuốc thành công -> Trạng thái CHO_DUYET
    // =========================================================================================
    @Test
    @Order(1)
    void testCase01_CustomerUploadsPrescriptionSuccessfully() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        byte[] fakeJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02};

        createdPrescriptionId = given()
                .header("Authorization", "Bearer " + token)
                .multiPart("image", "don_thuoc.jpg", fakeJpgBytes, "image/jpeg")
                .multiPart("notes", "Don thuoc viem hong cap")
                .when()
                .post("/api/v1/prescriptions")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("customerId", equalTo(CUSTOMER_A_ID))
                .body("status", equalTo("CHO_DUYET"))
                .body("notes", equalTo("Don thuoc viem hong cap"))
                .extract().path("id");

        // Tạo thêm 1 đơn thuốc để dùng cho ca kiểm thử Từ chối (Ca 6 & Ca 7)
        prescriptionToRejectId = given()
                .header("Authorization", "Bearer " + token)
                .multiPart("image", "don_thuoc_2.jpg", fakeJpgBytes, "image/jpeg")
                .multiPart("notes", "Don thuoc 2 can kiem tra tu choi")
                .when()
                .post("/api/v1/prescriptions")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    // =========================================================================================
    // Ca 2: Upload sai định dạng (tệp .txt hoặc magic bytes sai) -> 400 Bad Request (INVALID_FILE_TYPE)
    // =========================================================================================
    @Test
    @Order(2)
    void testCase02_UploadInvalidFileType_FailsWith400() {
        // Cho gọi phương thức thực tế để kiểm tra logic validate magic bytes
        Mockito.when(minioStorageService.uploadPrescriptionImage(any())).thenCallRealMethod();

        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        byte[] textBytes = "This is a plain text file, not an image".getBytes(StandardCharsets.UTF_8);

        given()
                .header("Authorization", "Bearer " + token)
                .multiPart("image", "document.txt", textBytes, "text/plain")
                .multiPart("notes", "Gửi file sai định dạng")
                .when()
                .post("/api/v1/prescriptions")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_FILE_TYPE"));
    }

    // =========================================================================================
    // Ca 3: Upload tệp vượt quá 5MB -> 400 Bad Request (FILE_SIZE_EXCEEDED)
    // =========================================================================================
    @Test
    @Order(3)
    void testCase03_UploadFileSizeExceeded_FailsWith400() {
        Mockito.when(minioStorageService.uploadPrescriptionImage(any())).thenCallRealMethod();

        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // Tệp kích thước 5MB + 1024 bytes (5,243,904 bytes)
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1024];
        oversizedBytes[0] = (byte) 0xFF;
        oversizedBytes[1] = (byte) 0xD8;
        oversizedBytes[2] = (byte) 0xFF;

        given()
                .header("Authorization", "Bearer " + token)
                .multiPart("image", "large_prescription.jpg", oversizedBytes, "image/jpeg")
                .when()
                .post("/api/v1/prescriptions")
                .then()
                .statusCode(400)
                .body("code", equalTo("FILE_SIZE_EXCEEDED"));
    }

    // =========================================================================================
    // Ca 4: Phân quyền - Khách hàng B xem đơn thuốc của Khách hàng A -> Bị từ chối 403 Forbidden
    // =========================================================================================
    @Test
    @Order(4)
    void testCase04_CustomerCannotViewAnotherCustomerPrescription() {
        String tokenCustomerB = generateToken(CUSTOMER_B_ID, "KHACH_HANG", "Trần Văn B");

        // Khách B xem chi tiết đơn của Khách A
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .when()
                .get("/api/v1/prescriptions/" + createdPrescriptionId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // Khách B xem ảnh đơn của Khách A
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .when()
                .get("/api/v1/prescriptions/" + createdPrescriptionId + "/image")
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // =========================================================================================
    // Ca 5: Dược sĩ xem chi tiết đơn thuốc và xem danh sách chờ duyệt -> Thành công 200 OK
    // =========================================================================================
    @Test
    @Order(5)
    void testCase05_PharmacistCanViewPendingListAndDetails() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        // Xem danh sách chờ duyệt
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .when()
                .get("/api/v1/prescriptions/pending")
                .then()
                .statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("items.id", hasItem(createdPrescriptionId));

        // Xem chi tiết đơn thuốc của khách A
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .when()
                .get("/api/v1/prescriptions/" + createdPrescriptionId)
                .then()
                .statusCode(200)
                .body("id", equalTo(createdPrescriptionId))
                .body("customerId", equalTo(CUSTOMER_A_ID))
                .body("status", equalTo("CHO_DUYET"));

        // Xem luồng ảnh đơn thuốc
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .when()
                .get("/api/v1/prescriptions/" + createdPrescriptionId + "/image")
                .then()
                .statusCode(200);
    }

    // =========================================================================================
    // Ca 6: Dược sĩ từ chối đơn thuốc không có lý do -> Bị chặn 400 Bad Request
    // =========================================================================================
    @Test
    @Order(6)
    void testCase06_RejectPrescriptionWithoutReason_FailsWith400() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        RejectPrescriptionRequest request = new RejectPrescriptionRequest("");

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/api/v1/prescriptions/" + prescriptionToRejectId + "/reject")
                .then()
                .statusCode(400);
    }

    // =========================================================================================
    // Ca 7: Dược sĩ từ chối đơn thuốc có lý do hợp lệ -> Thành công TU_CHOI
    // =========================================================================================
    @Test
    @Order(7)
    void testCase07_RejectPrescriptionWithReason_SucceedsWithTU_CHOI() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        RejectPrescriptionRequest request = new RejectPrescriptionRequest("Ảnh chụp bị mờ không đọc rõ tên thuốc và liều lượng");

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/api/v1/prescriptions/" + prescriptionToRejectId + "/reject")
                .then()
                .statusCode(200)
                .body("id", equalTo(prescriptionToRejectId))
                .body("status", equalTo("TU_CHOI"))
                .body("rejectionReason", equalTo("Ảnh chụp bị mờ không đọc rõ tên thuốc và liều lượng"))
                .body("pharmacistId", equalTo(PHARMACIST_ID));
    }

    // =========================================================================================
    // Ca 8: Dược sĩ duyệt đơn thuốc thành công với danh mục thuốc -> Thành công DA_DUYET
    // =========================================================================================
    @Test
    @Order(8)
    void testCase08_ApprovePrescription_SucceedsWithDA_DUYET() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        ApprovePrescriptionRequest request = new ApprovePrescriptionRequest(
                Instant.now().plus(Duration.ofDays(30)),
                List.of(
                        new ApproveItemRequest("6ad23be6c034632667baaa05", "Augmentin 1g", 2)
                )
        );

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/api/v1/prescriptions/" + createdPrescriptionId + "/approve")
                .then()
                .statusCode(200)
                .body("id", equalTo(createdPrescriptionId))
                .body("status", equalTo("DA_DUYET"))
                .body("pharmacistId", equalTo(PHARMACIST_ID))
                .body("items", notNullValue())
                .body("items[0].medicineId", equalTo("6ad23be6c034632667baaa05"))
                .body("items[0].maxAllowedQuantity", equalTo(2));
    }

    // =========================================================================================
    // Ca 9: Dược sĩ cố tình duyệt lại đơn đã xử lý -> Bị chặn 409 Conflict (PRESCRIPTION_ALREADY_PROCESSED)
    // =========================================================================================
    @Test
    @Order(9)
    void testCase09_ReApproveProcessedPrescription_FailsWithConflict() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        ApprovePrescriptionRequest request = new ApprovePrescriptionRequest(
                Instant.now().plus(Duration.ofDays(30)),
                List.of(
                        new ApproveItemRequest("6ad23be6c034632667baaa05", "Augmentin 1g", 2)
                )
        );

        // Duyệt lại đơn đã DA_DUYET
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/api/v1/prescriptions/" + createdPrescriptionId + "/approve")
                .then()
                .statusCode(409)
                .body("code", equalTo("PRESCRIPTION_ALREADY_PROCESSED"));

        // Duyệt lại đơn đã TU_CHOI
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/api/v1/prescriptions/" + prescriptionToRejectId + "/approve")
                .then()
                .statusCode(409)
                .body("code", equalTo("PRESCRIPTION_ALREADY_PROCESSED"));
    }

    // =========================================================================================
    // Ca 10: API nội bộ validate-for-order: Kiểm tra đơn hết hạn và đơn hợp lệ
    // =========================================================================================
    @Test
    @Order(10)
    void testCase10_ValidatePrescriptionForOrder() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        // 10a. Kiểm tra đơn hợp lệ
        ValidatePrescriptionRequest validReq = new ValidatePrescriptionRequest(
                UUID.fromString(CUSTOMER_A_ID),
                List.of(new ValidateItemRequest("6ad23be6c034632667baaa05", 2))
        );

        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(validReq)
                .when()
                .post("/api/v1/prescriptions/" + createdPrescriptionId + "/validate-for-order")
                .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("status", equalTo("DA_DUYET"))
                .body("violations", empty());

        // 10b. Kiểm tra vượt quá số lượng cho phép (cho phép 2, mua 3) -> 422
        ValidatePrescriptionRequest exceededReq = new ValidatePrescriptionRequest(
                UUID.fromString(CUSTOMER_A_ID),
                List.of(new ValidateItemRequest("6ad23be6c034632667baaa05", 3))
        );

        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(exceededReq)
                .when()
                .post("/api/v1/prescriptions/" + createdPrescriptionId + "/validate-for-order")
                .then()
                .statusCode(422)
                .body("code", equalTo("PRESCRIPTION_INVALID"));

        // 10c. Tạo đơn thuốc có hạn đã hết trong quá khứ để kiểm tra tình huống hết hạn
        byte[] fakeJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        expiredPrescriptionId = given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .multiPart("image", "expired.jpg", fakeJpgBytes, "image/jpeg")
                .multiPart("notes", "Don thuoc het han")
                .when()
                .post("/api/v1/prescriptions")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Dược sĩ duyệt với ngày hết hạn trong quá khứ (-1 ngày)
        ApprovePrescriptionRequest expiredApproveReq = new ApprovePrescriptionRequest(
                Instant.now().minus(Duration.ofDays(1)),
                List.of(new ApproveItemRequest("6ad23be6c034632667baaa05", "Augmentin 1g", 2))
        );

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(expiredApproveReq)
                .when()
                .put("/api/v1/prescriptions/" + expiredPrescriptionId + "/approve")
                .then()
                .statusCode(200);

        // Gọi validate-for-order trên đơn thuốc đã hết hạn -> 422
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(validReq)
                .when()
                .post("/api/v1/prescriptions/" + expiredPrescriptionId + "/validate-for-order")
                .then()
                .statusCode(422)
                .body("code", equalTo("PRESCRIPTION_INVALID"));
    }

    // =========================================================================================
    // Ca 11: API nội bộ link-order: Gắn đơn thuốc vào đơn hàng (Lần đầu -> 201, Idempotent -> 200/201, Gắn trùng -> 409)
    // =========================================================================================
    @Test
    @Order(11)
    void testCase11_LinkPrescriptionToOrder() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        LinkOrderRequest linkReq1 = new LinkOrderRequest(orderId1, UUID.fromString(CUSTOMER_A_ID));

        // Gắn lần 1 vào orderId1 -> Thành công
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(linkReq1)
                .when()
                .post("/api/v1/prescriptions/" + createdPrescriptionId + "/order-links")
                .then()
                .statusCode(201)
                .body("orderId", equalTo(orderId1.toString()))
                .body("prescriptionId", equalTo(createdPrescriptionId));

        // Gọi lại cùng orderId1 (Idempotent) -> Thành công (200 hoặc 201)
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(linkReq1)
                .when()
                .post("/api/v1/prescriptions/" + createdPrescriptionId + "/order-links")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("orderId", equalTo(orderId1.toString()))
                .body("prescriptionId", equalTo(createdPrescriptionId));

        // Gắn đơn thuốc đã dùng này vào một đơn hàng khác (orderId2) -> 409 Conflict
        LinkOrderRequest linkReq2 = new LinkOrderRequest(orderId2, UUID.fromString(CUSTOMER_A_ID));
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(linkReq2)
                .when()
                .post("/api/v1/prescriptions/" + createdPrescriptionId + "/order-links")
                .then()
                .statusCode(409)
                .body("code", equalTo("PRESCRIPTION_ALREADY_USED"));
    }
}
