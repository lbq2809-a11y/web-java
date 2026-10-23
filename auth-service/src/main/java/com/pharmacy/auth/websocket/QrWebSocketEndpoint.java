package com.pharmacy.auth.websocket;

import com.pharmacy.auth.service.QrSessionService;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Endpoint WebSocket thời gian thực cho phiên đăng nhập QR:
 * ws://localhost:8082/ws/qr/{sessionId}?secret=<pollSecret>
 * (Kết nối trực tiếp tới auth-service, không đi qua API Gateway)
 */
@WebSocket(path = "/ws/qr/{sessionId}")
public class QrWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(QrWebSocketEndpoint.class);

    @Inject
    QrSessionService qrSessionService;

    @Inject
    QrWebSocketRegistry qrWebSocketRegistry;

    @Inject
    Vertx vertx;

    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, WebSocketConnection connection) {
        String query = connection.handshakeRequest() != null ? connection.handshakeRequest().query() : null;
        String secret = extractSecret(query);

        // Kiểm tra mã bí mật tra cứu pollSecret
        if (secret == null || !qrSessionService.validateSecret(sessionId, secret)) {
            LOG.warnf("Từ chối kết nối WebSocket QR do secret không hợp lệ cho session: %s", sessionId);
            // Mã đóng 1008 = Policy Violation (RFC 6455)
            connection.closeAndAwait(new CloseReason(1008, "INVALID_SECRET"));
            return;
        }

        qrWebSocketRegistry.register(sessionId, connection);
        LOG.infof("Đã thiết lập kết nối WebSocket QR thành công cho session: %s", sessionId);

        // Hẹn giờ đóng kết nối khi phiên hết hạn TTL
        long remainingTtlSeconds = qrSessionService.getRemainingTtlSeconds(sessionId);
        if (remainingTtlSeconds > 0) {
            vertx.setTimer(remainingTtlSeconds * 1000L, timerId -> {
                WebSocketConnection conn = qrWebSocketRegistry.get(sessionId);
                if (conn != null && conn.isOpen()) {
                    LOG.infof("Phiên QR %s đã hết hạn TTL, gửi thông báo EXPIRED và đóng WebSocket", sessionId);
                    try {
                        conn.sendTextAndAwait("{\"status\":\"EXPIRED\"}");
                        conn.closeAndAwait(CloseReason.NORMAL);
                    } catch (Exception ignored) {
                    } finally {
                        qrWebSocketRegistry.unregister(sessionId, conn);
                    }
                }
            });
        }
    }

    @OnTextMessage
    public void onMessage(@PathParam("sessionId") String sessionId, String message, WebSocketConnection connection) {
        // Trình duyệt có thể gửi "PING" để duy trì kết nối
        if ("PING".equalsIgnoreCase(message)) {
            connection.sendTextAndAwait("PONG");
        }
    }

    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId, WebSocketConnection connection) {
        qrWebSocketRegistry.unregister(sessionId, connection);
        LOG.debugf("Kết nối WebSocket cho session %s đã đóng", sessionId);
    }

    @OnError
    public void onError(@PathParam("sessionId") String sessionId, Throwable throwable, WebSocketConnection connection) {
        qrWebSocketRegistry.unregister(sessionId, connection);
        LOG.warnf("Lỗi kết nối WebSocket cho session %s: %s", sessionId, throwable != null ? throwable.getMessage() : "Unknown");
    }

    private String extractSecret(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if ("secret".equalsIgnoreCase(pair[0].trim()) && pair.length == 2) {
                return pair[1].trim();
            }
        }
        return null;
    }
}

