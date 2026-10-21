package com.pharmacy.order.service;

import com.pharmacy.order.client.MedicineServiceClient;
import com.pharmacy.order.dto.AddToCartRequest;
import com.pharmacy.order.dto.CartItemResponse;
import com.pharmacy.order.dto.CartResponse;
import com.pharmacy.order.dto.MedicineResponse;
import com.pharmacy.order.dto.UpdateCartItemRequest;
import com.pharmacy.order.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service nghiệp vụ quản lý giỏ hàng:
 * Lưu trữ số lượng thuốc trên Redis, truy vấn giá và trạng thái thuốc mới nhất từ medicine-service.
 */
@ApplicationScoped
public class CartService {

    private static final Logger LOG = Logger.getLogger(CartService.class);

    @Inject
    CartRedisService cartRedisService;

    @Inject
    @RestClient
    MedicineServiceClient medicineClient;

    /**
     * Lấy chi tiết giỏ hàng hiện tại kèm giá thuốc thời gian thực từ medicine-service
     */
    public CartResponse getCartDetail(UUID userId) {
        Map<String, Integer> cartMap = cartRedisService.getCart(userId);
        if (cartMap == null || cartMap.isEmpty()) {
            return new CartResponse(Collections.emptyList(), BigDecimal.ZERO, 0);
        }

        String ids = String.join(",", cartMap.keySet());
        List<MedicineResponse> medicines;
        try {
            medicines = medicineClient.getMedicinesBatch(ids);
        } catch (Exception e) {
            LOG.errorf("Lỗi khi gọi medicine-service lấy thông tin giá giỏ hàng: %s", e.getMessage());
            // Trả giỏ hàng tối thiểu nếu medicine-service tạm thời không liên lạc được
            List<CartItemResponse> fallbackItems = new ArrayList<>();
            cartMap.forEach((medId, qty) -> fallbackItems.add(new CartItemResponse(
                    medId, null, "Thuốc mã " + medId, null, BigDecimal.ZERO, null, "OTC", qty, BigDecimal.ZERO, true
            )));
            return new CartResponse(fallbackItems, BigDecimal.ZERO, cartMap.values().stream().mapToInt(Integer::intValue).sum());
        }

        if (medicines == null || medicines.isEmpty()) {
            return new CartResponse(Collections.emptyList(), BigDecimal.ZERO, 0);
        }

        Map<String, MedicineResponse> medMap = medicines.stream()
                .collect(Collectors.toMap(MedicineResponse::id, m -> m));

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (Map.Entry<String, Integer> entry : cartMap.entrySet()) {
            String medId = entry.getKey();
            int qty = entry.getValue();
            MedicineResponse med = medMap.get(medId);

            if (med != null) {
                BigDecimal price = med.price() != null ? med.price() : BigDecimal.ZERO;
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
                totalAmount = totalAmount.add(subtotal);
                totalItems += qty;

                items.add(new CartItemResponse(
                        med.id(),
                        med.code(),
                        med.name(),
                        med.unit(),
                        price,
                        med.imageUrl(),
                        med.prescriptionType(),
                        qty,
                        subtotal,
                        med.active()
                ));
            } else {
                // Thuốc không còn tồn tại trong DB của medicine-service
                items.add(new CartItemResponse(
                        medId, null, "Sản phẩm không còn kinh doanh", null, BigDecimal.ZERO, null, "OTC", qty, BigDecimal.ZERO, false
                ));
            }
        }

        return new CartResponse(items, totalAmount, totalItems);
    }

    /**
     * Thêm thuốc vào giỏ hàng
     */
    public void addItem(UUID userId, AddToCartRequest req) {
        if (req.quantity() == null || req.quantity() <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Số lượng thuốc phải lớn hơn 0");
        }
        cartRedisService.addItem(userId, req.medicineId(), req.quantity());
    }

    /**
     * Cập nhật số lượng của một thuốc trong giỏ
     */
    public void updateQuantity(UUID userId, String medicineId, UpdateCartItemRequest req) {
        cartRedisService.updateQuantity(userId, medicineId, req.quantity());
    }

    /**
     * Xóa 1 loại thuốc khỏi giỏ
     */
    public void removeItem(UUID userId, String medicineId) {
        cartRedisService.removeItem(userId, medicineId);
    }

    /**
     * Xóa toàn bộ giỏ hàng
     */
    public void clearCart(UUID userId) {
        cartRedisService.clearCart(userId);
    }
}

