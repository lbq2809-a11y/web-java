package com.pharmacy.gateway.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Quản lý cấu hình URL các dịch vụ backend và bảo mật của API Gateway.
 */
@ApplicationScoped
public class GatewayConfig {

    @ConfigProperty(name = "gateway.routes.auth.url", defaultValue = "http://localhost:8082")
    String authServiceUrl;

    @ConfigProperty(name = "gateway.routes.medicine.url", defaultValue = "http://localhost:8081")
    String medicineServiceUrl;

    @ConfigProperty(name = "gateway.routes.prescription.url", defaultValue = "http://localhost:8083")
    String prescriptionServiceUrl;

    @ConfigProperty(name = "gateway.routes.order.url", defaultValue = "http://localhost:8084")
    String orderServiceUrl;

    @ConfigProperty(name = "gateway.routes.payment.url", defaultValue = "http://localhost:8085")
    String paymentServiceUrl;

    @ConfigProperty(name = "gateway.routes.notification.url", defaultValue = "http://localhost:8086")
    String notificationServiceUrl;

    @ConfigProperty(name = "gateway.security.public-routes", defaultValue = "")
    List<String> publicRoutes;

    @ConfigProperty(name = "gateway.http.client.timeout-ms", defaultValue = "10000")
    long timeoutMs;

    public String authServiceUrl() {
        return authServiceUrl;
    }

    public String medicineServiceUrl() {
        return medicineServiceUrl;
    }

    public String prescriptionServiceUrl() {
        return prescriptionServiceUrl;
    }

    public String orderServiceUrl() {
        return orderServiceUrl;
    }

    public String paymentServiceUrl() {
        return paymentServiceUrl;
    }

    public String notificationServiceUrl() {
        return notificationServiceUrl;
    }

    public List<String> publicRoutes() {
        return publicRoutes;
    }

    public long timeoutMs() {
        return timeoutMs;
    }
}
