package com.pharmacy.medicine;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Map;

/**
 * Khởi chạy MongoDB Testcontainer (mongo:7) riêng biệt khi chạy Integration Test,
 * đảm bảo không ảnh hưởng tới dữ liệu dev trong container pharmacy-mongodb.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    private MongoDBContainer mongoDBContainer;

    @Override
    public Map<String, String> start() {
        mongoDBContainer = new MongoDBContainer("mongo:7");
        mongoDBContainer.start();
        return Map.of(
                "quarkus.mongodb.connection-string", mongoDBContainer.getReplicaSetUrl(),
                "quarkus.mongodb.database", "medicine_test_db"
        );
    }

    @Override
    public void stop() {
        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
        }
    }
}

