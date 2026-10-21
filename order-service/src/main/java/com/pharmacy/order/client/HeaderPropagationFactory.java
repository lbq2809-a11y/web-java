package com.pharmacy.order.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import java.util.UUID;

/**
 * Tự động chuyển tiếp (propagate) header Authorization (JWT) và X-Correlation-Id
 * khi gọi MicroProfile REST Client sang các service khác theo quy ước tại docs/API-CONTRACT.md
 */
@ApplicationScoped
public class HeaderPropagationFactory implements ClientHeadersFactory {

    @Inject
    JsonWebToken jwt;

    @Inject
    HttpHeaders httpHeaders;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders
    ) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

        // 1. Chuyển tiếp JWT Token người dùng
        if (jwt != null && jwt.getRawToken() != null && !jwt.getRawToken().isBlank()) {
            result.putSingle("Authorization", "Bearer " + jwt.getRawToken());
        } else if (incomingHeaders != null && incomingHeaders.containsKey("Authorization")) {
            result.putSingle("Authorization", incomingHeaders.getFirst("Authorization"));
        }

        // 2. Chuyển tiếp hoặc sinh mới X-Correlation-Id
        if (incomingHeaders != null && incomingHeaders.containsKey("X-Correlation-Id")) {
            result.putSingle("X-Correlation-Id", incomingHeaders.getFirst("X-Correlation-Id"));
        } else {
            result.putSingle("X-Correlation-Id", UUID.randomUUID().toString());
        }

        return result;
    }
}

