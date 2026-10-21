package com.pharmacy.order.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý giỏ hàng của khách hàng lưu trữ tạm thời trên Redis
 * Key pattern: cart:{userId} (dạng Redis Hash)
 * Tự động gia hạn thời gian sống (TTL) 7 ngày sau mỗi lần thao tác
 */
@ApplicationScoped
public class CartRedisService {

    private static final Logger LOG = Logger.getLogger(CartRedisService.class);
    public static final long CART_TTL_SECONDS = 7 * 24 * 3600L; // 7 ngày

    @Inject
    RedisDataSource redisDataSource;

    private HashCommands<String, String, String> hashCommands;
    private KeyCommands<String> keyCommands;

    @PostConstruct
    void init() {
        this.hashCommands = redisDataSource.hash(String.class);
        this.keyCommands = redisDataSource.key();
    }

    public String getCartKey(UUID userId) {
        return "cart:" + userId;
    }

    /**
     * Lấy toàn bộ giỏ hàng của khách (trả về Map gồm medicineId -> số lượng)
     */
    public Map<String, Integer> getCart(UUID userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        String key = getCartKey(userId);
        Map<String, String> raw = hashCommands.hgetall(key);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> result = new HashMap<>();
        raw.forEach((medId, qtyStr) -> {
            try {
                int qty = Integer.parseInt(qtyStr);
                if (qty > 0) {
                    result.put(medId, qty);
                }
            } catch (NumberFormatException e) {
                LOG.warnf("Bỏ qua thuốc %s với số lượng không hợp lệ '%s' trong giỏ %s", medId, qtyStr, key);
            }
        });
        return result;
    }

    /**
     * Thêm thuốc vào giỏ (nếu đã có thì cộng dồn số lượng)
     */
    public void addItem(UUID userId, String medicineId, int quantity) {
        if (userId == null || medicineId == null || quantity <= 0) {
            return;
        }
        String key = getCartKey(userId);
        String currentQtyStr = hashCommands.hget(key, medicineId);
        int newQuantity = quantity;
        if (currentQtyStr != null) {
            try {
                newQuantity += Integer.parseInt(currentQtyStr);
            } catch (NumberFormatException ignored) {}
        }

        hashCommands.hset(key, medicineId, String.valueOf(newQuantity));
        keyCommands.expire(key, CART_TTL_SECONDS);
        LOG.debugf("Đã thêm thuốc %s (số lượng: %d, tổng: %d) vào giỏ %s", medicineId, quantity, newQuantity, key);
    }

    /**
     * Cập nhật số lượng của thuốc trong giỏ (nếu quantity <= 0 thì tự động xóa)
     */
    public void updateQuantity(UUID userId, String medicineId, int quantity) {
        if (userId == null || medicineId == null) {
            return;
        }
        String key = getCartKey(userId);
        if (quantity <= 0) {
            hashCommands.hdel(key, medicineId);
        } else {
            hashCommands.hset(key, medicineId, String.valueOf(quantity));
            keyCommands.expire(key, CART_TTL_SECONDS);
        }
        LOG.debugf("Đã cập nhật số lượng thuốc %s thành %d trong giỏ %s", medicineId, quantity, key);
    }

    /**
     * Xóa 1 loại thuốc khỏi giỏ
     */
    public void removeItem(UUID userId, String medicineId) {
        if (userId == null || medicineId == null) {
            return;
        }
        String key = getCartKey(userId);
        hashCommands.hdel(key, medicineId);
        keyCommands.expire(key, CART_TTL_SECONDS);
        LOG.debugf("Đã xóa thuốc %s khỏi giỏ %s", medicineId, key);
    }

    /**
     * Xóa sạch toàn bộ giỏ hàng (sau khi checkout thành công)
     */
    public void clearCart(UUID userId) {
        if (userId == null) {
            return;
        }
        String key = getCartKey(userId);
        keyCommands.del(key);
        LOG.debugf("Đã xóa toàn bộ giỏ hàng %s", key);
    }
}

