package com.pharmacy.consultation.service;

import com.pharmacy.consultation.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dịch vụ giới hạn tần suất gửi câu hỏi (In-Memory Sliding Window Rate Limiter).
 */
@ApplicationScoped
public class RateLimiterService {

    @ConfigProperty(name = "consultation.rate-limit.max-requests", defaultValue = "20")
    int maxRequests;

    @ConfigProperty(name = "consultation.rate-limit.window-seconds", defaultValue = "60")
    long windowSeconds;

    private final Map<String, Deque<Long>> userRequestTimestamps = new ConcurrentHashMap<>();

    /**
     * Kiểm tra và ghi nhận một lượt gọi từ người dùng.
     * @throws ApiException với mã HTTP 429 nếu vượt quá giới hạn.
     */
    public synchronized void checkRateLimit(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }

        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        Deque<Long> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());

        // Loại bỏ các lượt gọi ngoài cửa sổ thời gian
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            throw new ApiException(
                    "RATE_LIMIT_EXCEEDED",
                    "Bạn đã gửi quá nhiều câu hỏi trong thời gian ngắn. Vui lòng chờ giây lát.",
                    Response.Status.TOO_MANY_REQUESTS
            );
        }

        timestamps.addLast(now);
    }

    /**
     * Xóa dữ liệu rate limit phục vụ kiểm thử.
     */
    public synchronized void reset() {
        userRequestTimestamps.clear();
    }
}

