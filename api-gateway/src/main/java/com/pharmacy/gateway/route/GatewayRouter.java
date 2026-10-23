package com.pharmacy.gateway.route;

import com.pharmacy.gateway.proxy.ReverseProxyHandler;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Đăng ký routing handler cho API Gateway, đón toàn bộ các request /api/v1/*
 */
@ApplicationScoped
public class GatewayRouter {

    @Inject
    ReverseProxyHandler proxyHandler;

    public void init(@Observes Router router) {
        router.route("/api/v1/*").handler(proxyHandler::handle);
    }
}

