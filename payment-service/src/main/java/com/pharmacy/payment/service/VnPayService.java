package com.pharmacy.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class VnPayService {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @ConfigProperty(name = "vnpay.tmn-code")
    String tmnCode;

    @ConfigProperty(name = "vnpay.hash-secret")
    String hashSecret;

    @ConfigProperty(name = "vnpay.payment-url")
    String paymentUrl;

    @ConfigProperty(name = "vnpay.return-url")
    String returnUrl;

    public String createPaymentUrl(
        String transactionReference,
        BigDecimal amount,
        String ipAddress
    ) {
        long vnpAmount = amount
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact();

        LocalDateTime now = LocalDateTime.now(
            ZoneId.of("Asia/Ho_Chi_Minh")
        );

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(vnpAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", transactionReference);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + transactionReference);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr",
            ipAddress == null || ipAddress.isBlank() ? "127.0.0.1" : ipAddress);
        params.put("vnp_CreateDate", now.format(DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(DATE_FORMAT));

        String hashData = encodeParams(params);
        String secureHash = hmacSHA512(hashSecret, hashData);

        return paymentUrl + "?" + hashData
            + "&vnp_SecureHash=" + secureHash;
    }

    public boolean verifySignature(Map<String, String> receivedParams) {
        if (receivedParams == null) {
            return false;
        }

        String providedHash = receivedParams.get("vnp_SecureHash");

        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }

        Map<String, String> signedParams = new TreeMap<>();

        receivedParams.forEach((key, value) -> {
            if (key != null
                    && key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null) {
                signedParams.put(key, value);
            }
        });

        String calculatedHash = hmacSHA512(
            hashSecret,
            encodeParams(signedParams)
        );

        return MessageDigest.isEqual(
            providedHash.toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII),
            calculatedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public boolean isMerchantCode(String receivedCode) {
        return receivedCode != null && tmnCode.equals(receivedCode);
    }

    private String encodeParams(Map<String, String> params) {
        return params.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSHA512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");

            SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"
            );

            mac.init(key);

            byte[] bytes = mac.doFinal(
                data.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder result = new StringBuilder();

            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                "Không thể xử lý chữ ký VNPay", e
            );
        }
    }
}