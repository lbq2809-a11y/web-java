package com.pharmacy.auth.websocket;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý danh sách kết nối WebSocket đang lắng nghe phiên QR theo sessionId
 */
@ApplicationScoped
public class QrWebSocketRegistry {

    private static final Logger LOG = Logger.getLogger(QrWebSocketRegistry.class);

    private final ConcurrentHashMap<String, WebSocketConnection> activeConnections = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketConnection connection) {
        activeConnections.put(sessionId, connection);
        LOG.debugf("Đã đăng ký WebSocket connection cho QR session: %s", sessionId);
    }

    public void unregister(String sessionId, WebSocketConnection connection) {
        activeConnections.remove(sessionId, connection);
        LOG.debugf("Đã hủy đăng ký WebSocket connection cho QR session: %s", sessionId);
    }

    public WebSocketConnection get(String sessionId) {
        return activeConnections.get(sessionId);
    }

    public boolean hasConnection(String sessionId) {
        WebSocketConnection conn = activeConnections.get(sessionId);
        return conn != null && conn.isOpen();
    }
}

