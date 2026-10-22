package com.pharmacy.auth.resource;

import com.pharmacy.auth.config.DataInitializer;
import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthResourceTest {

    private static final String DEFAULT_CUSTOMER_EMAIL = "khach_hang@pharmacy.local";
    private static final String DEFAULT_PHARMACIST_EMAIL = "duoc_si@pharmacy.local";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@pharmacy.local";
    private static final String DEFAULT_PASSWORD = "123456";

    private static final String CUSTOMER_UUID = "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PHARMACIST_UUID = "22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String ADMIN_UUID = "00000000-0000-0000-0000-000000000000";

    @Inject
    DataInitializer dataInitializer;

    @Inject
    EntityManager em;

    private static boolean initialized = false;

    @BeforeEach
    @Transactional
    void setUp() {
        if (!initialized) {
            em.createNativeQuery("DELETE FROM users WHERE email NOT IN ('"
                    + DEFAULT_CUSTOMER_EMAIL + "', '"
                    + DEFAULT_PHARMACIST_EMAIL + "', '"
                    + DEFAULT_ADMIN_EMAIL + "')").executeUpdate();
            dataInitializer.onStart(null);
            initialized = true;
        }
    }

    // =========================================================================================
    // Ca 1: Đăng ký tài khoản khách hàng mới thành công -> 201 Created kèm JWT token
    // =========================================================================================
    @Test
    @Order(1)
    void testCase01_RegisterNewCustomer_Success() {
        RegisterRequest request = new RegisterRequest(
                "new_customer@pharmacy.local",
                "password123",
                "Lê Thị Mới",
                "0911223344"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("user.id", notNullValue())
                .body("user.email", equalTo("new_customer@pharmacy.local"))
                .body("user.fullName", equalTo("Lê Thị Mới"))
                .body("user.role", equalTo("KHACH_HANG"))
                .body("user.active", equalTo(true));
    }

    // =========================================================================================
    // Ca 2: Đăng ký lại email đã tồn tại -> Bị từ chối 400 Bad Request (EMAIL_ALREADY_EXISTS)
    // =========================================================================================
    @Test
    @Order(2)
    void testCase02_RegisterDuplicateEmail_FailsWith400() {
        RegisterRequest request = new RegisterRequest(
                DEFAULT_CUSTOMER_EMAIL,
                "password123",
                "Nguyễn Văn Trùng",
                "0988776655"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400)
                .body("code", equalTo("EMAIL_ALREADY_EXISTS"));
    }

    // =========================================================================================
    // Ca 3: Đăng ký với dữ liệu không hợp lệ (sai email, mật khẩu ngắn) -> 400 Validation Error
    // =========================================================================================
    @Test
    @Order(3)
    void testCase03_RegisterInvalidData_FailsWith400() {
        RegisterRequest request = new RegisterRequest(
                "invalid-email-format",
                "123", // ít hơn 6 ký tự
                "",    // họ tên để trống
                null
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    // =========================================================================================
    // Ca 4: Đăng nhập tài khoản Khách hàng mặc định -> 200 OK, role KHACH_HANG
    // =========================================================================================
    @Test
    @Order(4)
    void testCase04_LoginSuccess_DefaultCustomer() {
        LoginRequest request = new LoginRequest(DEFAULT_CUSTOMER_EMAIL, DEFAULT_PASSWORD);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("user.id", equalTo(CUSTOMER_UUID))
                .body("user.email", equalTo(DEFAULT_CUSTOMER_EMAIL))
                .body("user.role", equalTo("KHACH_HANG"));
    }

    // =========================================================================================
    // Ca 5: Đăng nhập tài khoản Dược sĩ mặc định -> 200 OK, role DUOC_SI
    // =========================================================================================
    @Test
    @Order(5)
    void testCase05_LoginSuccess_DefaultPharmacist() {
        LoginRequest request = new LoginRequest(DEFAULT_PHARMACIST_EMAIL, DEFAULT_PASSWORD);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("user.id", equalTo(PHARMACIST_UUID))
                .body("user.email", equalTo(DEFAULT_PHARMACIST_EMAIL))
                .body("user.role", equalTo("DUOC_SI"));
    }

    // =========================================================================================
    // Ca 6: Đăng nhập tài khoản Admin mặc định -> 200 OK, role ADMIN
    // =========================================================================================
    @Test
    @Order(6)
    void testCase06_LoginSuccess_DefaultAdmin() {
        LoginRequest request = new LoginRequest(DEFAULT_ADMIN_EMAIL, DEFAULT_PASSWORD);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("user.id", equalTo(ADMIN_UUID))
                .body("user.email", equalTo(DEFAULT_ADMIN_EMAIL))
                .body("user.role", equalTo("ADMIN"));
    }

    // =========================================================================================
    // Ca 7: Đăng nhập sai mật khẩu -> Bị từ chối 401 Unauthorized (INVALID_CREDENTIALS)
    // =========================================================================================
    @Test
    @Order(7)
    void testCase07_LoginWrongPassword_FailsWith401() {
        LoginRequest request = new LoginRequest(DEFAULT_CUSTOMER_EMAIL, "wrongpassword");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    // =========================================================================================
    // Ca 8: Đăng nhập email không tồn tại -> Bị từ chối 401 Unauthorized (INVALID_CREDENTIALS)
    // =========================================================================================
    @Test
    @Order(8)
    void testCase08_LoginNonExistentEmail_FailsWith401() {
        LoginRequest request = new LoginRequest("nonexistent@pharmacy.local", "anyPassword");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    // =========================================================================================
    // Ca 9: Gọi API /me bằng JWT token vừa đăng nhập -> 200 OK, trả về thông tin hồ sơ
    // =========================================================================================
    @Test
    @Order(9)
    void testCase09_GetMeWithValidToken_Success() {
        // 1. Đăng nhập lấy token
        LoginRequest loginReq = new LoginRequest(DEFAULT_CUSTOMER_EMAIL, DEFAULT_PASSWORD);
        String token = given()
                .contentType(ContentType.JSON)
                .body(loginReq)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract().path("accessToken");

        // 2. Gọi /me với Authorization Bearer token
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(CUSTOMER_UUID))
                .body("email", equalTo(DEFAULT_CUSTOMER_EMAIL))
                .body("role", equalTo("KHACH_HANG"))
                .body("fullName", equalTo("Nguyễn Văn A"));
    }

    // =========================================================================================
    // Ca 10: Gọi API /me khi không có token (chưa đăng nhập) -> 401 Unauthorized
    // =========================================================================================
    @Test
    @Order(10)
    void testCase10_GetMeWithoutToken_FailsWith401() {
        given()
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }
}

