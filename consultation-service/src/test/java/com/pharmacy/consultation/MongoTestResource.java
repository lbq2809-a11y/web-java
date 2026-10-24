package com.pharmacy.consultation;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.mongodb.MongoDBContainer;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Quản lý kết nối MongoDB cho Integration Test:
 * Nếu pharmacy-mongodb đang chạy tại localhost:27017 thì tái sử dụng DB consultation_test_db,
 * ngược lại khởi động Testcontainers MongoDB 7 tự động.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(MongoTestResource.class);
    private MongoDBContainer mongoDBContainer;

    @Override
    public Map<String, String> start() {
        // Kiểm tra xem cổng 27017 trên localhost có mở không
        if (isPortOpen("localhost", 27017, 1000)) {
            LOG.info("Phát hiện MongoDB đang chạy tại localhost:27017. Tái sử dụng cho consultation_test_db.");
            return Map.of(
                    "quarkus.mongodb.connection-string", "mongodb://localhost:27017",
                    "quarkus.mongodb.database", "consultation_test_db"
            );
        }

        LOG.info("Khởi động Testcontainers MongoDB 7 cho kiểm thử độc lập...");
        mongoDBContainer = new MongoDBContainer("mongo:7");
        mongoDBContainer.start();
        return Map.of(
                "quarkus.mongodb.connection-string", mongoDBContainer.getReplicaSetUrl(),
                "quarkus.mongodb.database", "consultation_test_db"
        );
    }

    @Override
    public void stop() {
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
        }
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

