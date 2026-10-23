package com.pharmacy.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.auth.dto.response.ConfirmQrResponse;
import com.pharmacy.auth.dto.response.CreateQrSessionResponse;
import com.pharmacy.auth.dto.response.QrSessionStatusResponse;
import com.pharmacy.auth.dto.response.UserResponse;
import com.pharmacy.auth.entity.User;
import com.pharmacy.auth.exception.ApiException;
import com.pharmacy.auth.mapper.UserMapper;
import com.pharmacy.auth.repository.UserRepository;
import com.pharmacy.auth.websocket.QrWebSocketRegistry;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý toàn bộ logic nghiệp vụ Đăng nhập QR Code (Giai đoạn 3):
 * Quản lý phiên trên Redis, Giới hạn tần suất IP (Rate limit), Xác nhận nguyên tử và Đồng bộ WebSocket
 */
@ApplicationScoped
public class QrSessionService {

    private static final Logger LOG = Logger.getLogger(QrSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Lua script giới hạn tần suất tạo phiên theo IP nguyên tử (10 lượt/phút)
    private static final String RATE_LIMIT_LUA = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local current = redis.call('INCR', key)
        if current == 1 or redis.call('TTL', key) == -1 then
            redis.call('EXPIRE', key, 60)
        end
        if current > limit then
            return 0
        else
            return 1
        end
    """;

    // Lua script tạo mới phiên QR nguyên tử kèm TTL (mặc định 120s)
    private static final String CREATE_SESSION_LUA = """
        local key = KEYS[1]
        local ttl = tonumber(ARGV[1])
        redis.call('HSET', key, 'sessionId', ARGV[2], 'pollSecret', ARGV[3], 'status', 'PENDING', 'expiresAt', ARGV[4])
        redis.call('EXPIRE', key, ttl)
        return 1
    """;

    // Lua script xác nhận phiên nguyên tử chống Race Condition (chỉ 1 thiết bị thắng)
    private static final String CONFIRM_LUA = """
        local key = KEYS[1]
        local userId = ARGV[1]
        if redis.call('EXISTS', key) == 0 then
            return 'NOT_FOUND'
        end
        local currentStatus = redis.call('HGET', key, 'status')
        if currentStatus == 'CONFIRMED' then
            return 'ALREADY_CONFIRMED'
        end
        if currentStatus ~= 'PENDING' then
            return 'NOT_FOUND'
        end
        redis.call('HSET', key, 'status', 'CONFIRMED', 'userId', userId)
        return 'OK'
    """;

    // Lua script lấy kết quả phiên và xóa nguyên tử (Single-use token delivery)
    private static final String CONSUME_SESSION_LUA = """
        local key = KEYS[1]
        if redis.call('EXISTS', key) == 0 then
            return 'NOT_FOUND'
        end
        local status = redis.call('HGET', key, 'status')
        if status == 'PENDING' then
            return 'PENDING'
        end
        if status == 'CONFIRMED' then
            local userId = redis.call('HGET', key, 'userId')
            redis.call('DEL', key)
            return 'USER:' .. userId
        end
        return 'NOT_FOUND'
    """;

    @ConfigProperty(name = "qr.session.ttl-seconds", defaultValue = "120")
    int defaultTtlSeconds;

    @ConfigProperty(name = "qr.rate-limit.max-per-minute", defaultValue = "10")
    int maxSessionsPerMinute;

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    UserRepository userRepo;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    UserMapper userMapper;

    @Inject
    QrWebSocketRegistry qrWebSocketRegistry;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Tạo phiên đăng nhập QR mới
     */
    public CreateQrSessionResponse createSession(String clientIp) {
        checkRateLimit(clientIp);

        String sessionId = UUID.randomUUID().toString();
        String pollSecret = generateSecureSecret();
        Instant expiresAt = Instant.now().plusSeconds(defaultTtlSeconds);

        String redisKey = "qr:session:" + sessionId;
        redisDataSource.execute("EVAL", CREATE_SESSION_LUA, "1", redisKey,
                String.valueOf(defaultTtlSeconds), sessionId, pollSecret, expiresAt.toString());

        LOG.infof("Đã tạo phiên QR mới (id: %s) cho IP: %s (TTL: %ds)", sessionId, clientIp, defaultTtlSeconds);

        // qrContent chỉ chứa sessionId, không chứa pollSecret
        return new CreateQrSessionResponse(sessionId, pollSecret, sessionId, "PENDING", expiresAt);
    }

    /**
     * Tra cứu trạng thái phiên QR (HTTP Polling)
     */
    public QrSessionStatusResponse getSessionStatus(String sessionId, String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw ApiException.forbidden("Mã bí mật tra cứu phiên QR không chính xác hoặc bị thiếu");
        }

        String redisKey = "qr:session:" + sessionId;
        if (!redisDataSource.key().exists(redisKey)) {
            throw ApiException.notFound("QR_SESSION_NOT_FOUND", "Phiên đăng nhập QR không tồn tại hoặc đã hết hạn");
        }

        String storedSecret = redisDataSource.hash(String.class).hget(redisKey, "pollSecret");
        if (storedSecret == null || !constantTimeEquals(storedSecret, clientSecret)) {
            throw ApiException.forbidden("Mã bí mật tra cứu phiên QR không chính xác hoặc bị thiếu");
        }

        Response resp = redisDataSource.execute("EVAL", CONSUME_SESSION_LUA, "1", redisKey);
        String result = resp != null ? resp.toString() : "NOT_FOUND";

        if ("NOT_FOUND".equals(result)) {
            throw ApiException.notFound("QR_SESSION_NOT_FOUND", "Phiên đăng nhập QR không tồn tại hoặc đã hết hạn");
        }

        if ("PENDING".equals(result)) {
            return QrSessionStatusResponse.pending(sessionId);
        }

        if (result.startsWith("USER:")) {
            String userIdStr = result.substring("USER:".length());
            User user = loadActiveUser(userIdStr);
            String token = jwtTokenService.generateToken(user);
            UserResponse userResp = userMapper.toUserResponse(user);

            LOG.infof("Đã trả token thành công qua HTTP Polling và xóa phiên QR: %s cho user: %s", sessionId, user.getEmail());
            return QrSessionStatusResponse.confirmed(sessionId, token, "Bearer", jwtTokenService.getExpiresInSeconds(), userResp);
        }

        throw ApiException.notFound("QR_SESSION_NOT_FOUND", "Phiên đăng nhập QR không tồn tại hoặc đã hết hạn");
    }

    /**
     * Mobile xác nhận phiên đăng nhập QR kèm JWT Bearer
     */
    public ConfirmQrResponse confirmSession(String sessionId, UUID userId) {
        User user = loadActiveUser(userId.toString());

        String redisKey = "qr:session:" + sessionId;
        Response resp = redisDataSource.execute("EVAL", CONFIRM_LUA, "1", redisKey, userId.toString());
        String result = resp != null ? resp.toString() : "NOT_FOUND";

        if ("NOT_FOUND".equals(result)) {
            throw ApiException.notFound("QR_SESSION_NOT_FOUND", "Phiên đăng nhập QR không tồn tại hoặc đã hết hạn");
        }
        if ("ALREADY_CONFIRMED".equals(result)) {
            throw ApiException.conflict("QR_SESSION_ALREADY_CONFIRMED", "Phiên đăng nhập QR đã được xác nhận trước đó");
        }

        LOG.infof("Thiết bị di động đã xác nhận thành công phiên QR: %s cho user: %s", sessionId, user.getEmail());

        // Kiểm tra xem Web client có đang lắng nghe qua WebSocket không
        WebSocketConnection wsConn = qrWebSocketRegistry.get(sessionId);
        if (wsConn != null && wsConn.isOpen()) {
            notifyWebSocketConsumer(sessionId, user, wsConn);
        }

        return ConfirmQrResponse.CONFIRMED;
    }

    /**
     * Xác thực pollSecret phục vụ WebSocket handshake
     */
    public boolean validateSecret(String sessionId, String clientSecret) {
        if (sessionId == null || sessionId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return false;
        }
        String redisKey = "qr:session:" + sessionId;
        String storedSecret = redisDataSource.hash(String.class).hget(redisKey, "pollSecret");
        if (storedSecret == null) {
            return false;
        }
        return constantTimeEquals(storedSecret, clientSecret);
    }

    /**
     * Lấy TTL còn lại của phiên để thiết lập hẹn giờ cho WebSocket
     */
    public long getRemainingTtlSeconds(String sessionId) {
        String redisKey = "qr:session:" + sessionId;
        Long ttl = redisDataSource.key().ttl(redisKey);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    private void notifyWebSocketConsumer(String sessionId, User user, WebSocketConnection wsConn) {
        String redisKey = "qr:session:" + sessionId;
        Response resp = redisDataSource.execute("EVAL", CONSUME_SESSION_LUA, "1", redisKey);
        String result = resp != null ? resp.toString() : "NOT_FOUND";

        if (result != null && result.startsWith("USER:")) {
            try {
                String token = jwtTokenService.generateToken(user);
                UserResponse userResp = userMapper.toUserResponse(user);

                Map<String, Object> payload = Map.of(
                        "status", "CONFIRMED",
                        "accessToken", token,
                        "tokenType", "Bearer",
                        "expiresIn", jwtTokenService.getExpiresInSeconds(),
                        "user", userResp
                );

                String jsonPayload = objectMapper.writeValueAsString(payload);
                wsConn.sendTextAndAwait(jsonPayload);
                wsConn.closeAndAwait(CloseReason.NORMAL);
                LOG.infof("Đã đẩy token thành công qua WebSocket và xóa phiên QR: %s cho user: %s", sessionId, user.getEmail());
            } catch (Exception e) {
                LOG.errorf("Lỗi khi gửi sự kiện WebSocket cho phiên QR %s: %s", sessionId, e.getMessage());
            } finally {
                qrWebSocketRegistry.unregister(sessionId, wsConn);
            }
        }
    }

    private void checkRateLimit(String clientIp) {
        String safeIp = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "unknown";
        String rateKey = "qr:rate_limit:" + safeIp;

        Response resp = redisDataSource.execute("EVAL", RATE_LIMIT_LUA, "1", rateKey, String.valueOf(maxSessionsPerMinute));
        if (resp != null && resp.toInteger() == 0) {
            LOG.warnf("Vượt quá giới hạn tạo phiên QR cho IP: %s (tối đa %d phiên/phút)", safeIp, maxSessionsPerMinute);
            throw ApiException.tooManyRequests("TOO_MANY_REQUESTS", "Quá nhiều yêu cầu tạo phiên QR, vui lòng thử lại sau 1 phút");
        }
    }

    private User loadActiveUser(String userIdStr) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "ID người dùng không đúng định dạng UUID");
        }

        User user = userRepo.findByIdOptional(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin tài khoản"));

        if (!user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản của bạn đã bị vô hiệu hóa");
        }
        return user;
    }

    private String generateSecureSecret() {
        byte[] bytes = new byte[16]; // 128 bit entropy
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
