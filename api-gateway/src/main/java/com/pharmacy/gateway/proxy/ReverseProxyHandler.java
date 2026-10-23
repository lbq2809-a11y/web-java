package com.pharmacy.gateway.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.gateway.config.GatewayConfig;
import com.pharmacy.gateway.dto.ErrorResponse;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Xử lý Reverse Proxy dạng Reactive Streaming Non-blocking bằng Vert.x HttpClient.
 * Chịu trách nhiệm:
 * 1. Chuẩn hóa đường dẫn (bỏ trailing slash, giải mã %2e/%2f, xử lý path traversal ..).
 * 2. Quản lý X-Correlation-Id (sinh mới nếu thiếu, chuyển tiếp nguyên vẹn nếu có).
 * 3. Chống giả mạo Header: lọc sạch các header nội bộ X-User-*, X-Role, X-Customer-*, X-Forwarded-User.
 * 4. Xác thực JWT theo cấu hình route công khai linh hoạt (Method:Pattern).
 * 5. Stream trực tiếp Request và Response body bằng PipeTo; kết thúc ngay với GET/HEAD.
 * 6. Bắt lỗi phân biệt 503 SERVICE_UNAVAILABLE (kết nối thất bại) và 504 GATEWAY_TIMEOUT (timeout).
 */
@ApplicationScoped
public class ReverseProxyHandler {

    private static final Logger log = Logger.getLogger(ReverseProxyHandler.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade"
    );

    private static final Set<String> EXACT_SPOOFABLE_HEADERS = Set.of(
        "x-role", "x-roles", "x-forwarded-user"
    );

    @Inject
    Vertx vertx;

    @Inject
    GatewayConfig config;

    @Inject
    JWTParser jwtParser;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        HttpClientOptions options = new HttpClientOptions()
            .setConnectTimeout(5000)
            .setIdleTimeout(30)
            .setKeepAlive(true)
            .setMaxPoolSize(100);
        this.httpClient = vertx.createHttpClient(options);
    }

    @PreDestroy
    void destroy() {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
    }

    public void handle(RoutingContext rc) {
        try {
            HttpServerRequest serverReq = rc.request();
            HttpMethod method = serverReq.method();
            String origin = serverReq.getHeader("Origin");

            // 1. Xử lý CORS preflight (OPTIONS)
            if (method == HttpMethod.OPTIONS) {
                HttpServerResponse response = rc.response();
                if (origin != null && !origin.isBlank()) {
                    response.putHeader("Access-Control-Allow-Origin", origin)
                        .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                        .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key, X-Correlation-Id, Accept")
                        .putHeader("Access-Control-Expose-Headers", "X-Correlation-Id")
                        .putHeader("Access-Control-Allow-Credentials", "true")
                        .putHeader("Access-Control-Max-Age", "86400");
                }
                response.setStatusCode(200).end();
                return;
            }

            // 2. Xử lý X-Correlation-Id: Sinh UUID mới nếu client không gửi
            String correlationId = serverReq.getHeader("X-Correlation-Id");
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            final String finalCorrelationId = correlationId;

            // 3. Chuẩn hóa đường dẫn & Kiểm tra Path Traversal
            String normalizedPath = normalizePath(serverReq.path());
            if (normalizedPath.contains("..") || !normalizedPath.startsWith("/")) {
                sendError(rc, 400, "BAD_REQUEST", "Đường dẫn không hợp lệ hoặc chứa ký tự bất thường",
                    List.of("Đường dẫn chứa ký tự điều hướng bất hợp pháp: " + serverReq.path()), finalCorrelationId);
                return;
            }

            // 4. Kiểm tra JWT Token đối với các Protected Routes
            if (!isPublicRoute(method, normalizedPath)) {
                String authHeader = serverReq.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    sendError(rc, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập tài nguyên này",
                        List.of("Thiếu hoặc sai định dạng header Authorization (Bearer <token>)"), finalCorrelationId);
                    return;
                }

                String token = authHeader.substring(7).trim();
                try {
                    jwtParser.parse(token);
                } catch (ParseException e) {
                    sendError(rc, 401, "UNAUTHORIZED", "Token xác thực không hợp lệ hoặc đã hết hạn",
                        List.of(e.getMessage()), finalCorrelationId);
                    return;
                }
            }

            // 5. Phân giải URL dịch vụ đích theo tiền tố route
            String targetBaseUrl = resolveTargetUrl(normalizedPath);
            if (targetBaseUrl == null) {
                sendError(rc, 404, "NOT_FOUND", "Không tìm thấy tuyến đường tương ứng tại API Gateway",
                    List.of("Đường dẫn không hợp lệ: " + normalizedPath), finalCorrelationId);
                return;
            }

            URI targetUri = URI.create(targetBaseUrl);
            final String host = targetUri.getHost();
            final int port = targetUri.getPort() != -1 
                ? targetUri.getPort() 
                : ("https".equalsIgnoreCase(targetUri.getScheme()) ? 443 : 80);
            final boolean ssl = "https".equalsIgnoreCase(targetUri.getScheme());

            // 6. Xử lý Stream: GET/HEAD không có body, các method khác tạm dừng (pause) stream
            final boolean hasNoBody = (method == HttpMethod.GET || method == HttpMethod.HEAD);
            Pipe<Buffer> requestPipe = null;
            if (!hasNoBody && !serverReq.isEnded()) {
                requestPipe = serverReq.pipe();
            }
            final Pipe<Buffer> finalPipe = requestPipe;

            // 7. Cấu hình request gửi tới backend
            RequestOptions requestOptions = new RequestOptions()
                .setHost(host)
                .setPort(port)
                .setSsl(ssl)
                .setMethod(method)
                .setURI(serverReq.uri())
                .setTimeout(config.timeoutMs());

            httpClient.request(requestOptions)
                .onSuccess(clientReq -> {
                    // Chuyển tiếp các header, LỌC BỎ hop-by-hop và các header nội bộ có nguy cơ giả mạo
                    String clientContentLength = serverReq.getHeader("Content-Length");
                    for (Map.Entry<String, String> header : serverReq.headers()) {
                        String key = header.getKey();
                        if (isHopByHopHeader(key) || key.equalsIgnoreCase("Host") || key.equalsIgnoreCase("Content-Length")) {
                            continue;
                        }
                        if (isSpoofableInternalHeader(key)) {
                            log.debug("Đã loại bỏ header nhạy cảm do client tự gửi: " + key);
                            continue;
                        }
                        clientReq.headers().add(key, header.getValue());
                    }
                    clientReq.headers().set("Host", host + ":" + port);
                    clientReq.headers().set("X-Correlation-Id", finalCorrelationId);

                    if (clientContentLength != null) {
                        clientReq.putHeader("Content-Length", clientContentLength);
                    } else if (!hasNoBody && method != HttpMethod.DELETE) {
                        clientReq.setChunked(true);
                    }

                    if (serverReq.remoteAddress() != null) {
                        clientReq.headers().set("X-Forwarded-For", serverReq.remoteAddress().host());
                    }
                    clientReq.headers().set("X-Forwarded-Proto", serverReq.scheme());
                    clientReq.headers().set("X-Forwarded-Host", serverReq.host());

                    // Xử lý phản hồi từ backend
                    clientReq.response()
                        .onSuccess(backendResp -> {
                            HttpServerResponse clientResp = rc.response();
                            int statusCode = backendResp.statusCode();
                            clientResp.setStatusCode(statusCode);
                            if (backendResp.statusMessage() != null) {
                                clientResp.setStatusMessage(backendResp.statusMessage());
                            }

                            // Chuyển tiếp header phản hồi từ backend về client
                            for (Map.Entry<String, String> entry : backendResp.headers()) {
                                String name = entry.getKey();
                                if (!isHopByHopHeader(name) && !name.equalsIgnoreCase("Content-Length")) {
                                    clientResp.headers().add(name, entry.getValue());
                                }
                            }
                            // Đảm bảo luôn trả về X-Correlation-Id trong response header
                            clientResp.headers().set("X-Correlation-Id", finalCorrelationId);

                            // CORS headers
                            if (origin != null && !origin.isBlank()) {
                                clientResp.headers().set("Access-Control-Allow-Origin", origin);
                                clientResp.headers().set("Access-Control-Expose-Headers", "X-Correlation-Id");
                                clientResp.headers().set("Access-Control-Allow-Credentials", "true");
                            }

                            String backendContentLength = backendResp.getHeader("Content-Length");
                            if (statusCode == 204 || statusCode == 304) {
                                // 204 No Content hoặc 304 Not Modified không có body
                            } else if (backendContentLength != null) {
                                clientResp.putHeader("Content-Length", backendContentLength);
                            } else {
                                clientResp.setChunked(true);
                            }

                            // Stream trực tiếp body phản hồi về cho client
                            backendResp.pipeTo(clientResp, arResp -> {
                                if (arResp.failed()) {
                                    log.warn("Lỗi khi stream dữ liệu phản hồi về client: " + arResp.cause().getMessage());
                                }
                            });
                        })
                        .onFailure(err -> handleBackendFailure(rc, err, host, port, finalPipe, finalCorrelationId));

                    // Nếu GET/HEAD không có body -> kết thúc request ngay, không pipe body
                    if (hasNoBody) {
                        clientReq.end();
                    } else if (finalPipe != null) {
                        finalPipe.to(clientReq, arReq -> {
                            if (arReq.failed()) {
                                log.warn("Lỗi khi stream request body sang backend: " + arReq.cause().getMessage());
                            }
                        });
                    } else {
                        clientReq.end();
                    }
                })
                .onFailure(err -> handleBackendFailure(rc, err, host, port, finalPipe, finalCorrelationId));
        } catch (Exception e) {
            log.error("Lỗi không mong muốn tại API Gateway: " + e.getMessage(), e);
            sendError(rc, 500, "INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi nội bộ tại API Gateway",
                List.of(e.getMessage() != null ? e.getMessage() : "Unknown error"), UUID.randomUUID().toString());
        }
    }

    /**
     * Chuẩn hóa đường dẫn: bỏ query string, giải mã URL, chuẩn hóa dấu chấm đoạn (. và ..), bỏ trailing slash
     */
    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        int qIndex = rawPath.indexOf('?');
        String path = (qIndex != -1) ? rawPath.substring(0, qIndex) : rawPath;

        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        path = path.replace('\\', '/');

        try {
            URI uri = new URI(path).normalize();
            path = uri.getPath();
        } catch (Exception ignored) {
        }

        if (path == null) {
            return "/";
        }

        path = path.replaceAll("/+", "/");

        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Xác định route công khai (không cần token JWT) dựa trên phương thức HTTP và cấu hình
     */
    private boolean isPublicRoute(HttpMethod method, String normalizedPath) {
        if (normalizedPath.startsWith("/q")) {
            return true;
        }

        String methodStr = method.name();
        List<String> rules = config.publicRoutes();
        if (rules != null) {
            for (String rule : rules) {
                String trimmed = rule.trim();
                if (trimmed.isEmpty()) continue;
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx == -1) continue;

                String ruleMethod = trimmed.substring(0, colonIdx).trim();
                String rulePattern = trimmed.substring(colonIdx + 1).trim();

                if (matchesRoute(ruleMethod, rulePattern, methodStr, normalizedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesRoute(String ruleMethod, String rulePattern, String requestMethod, String requestPath) {
        if (!ruleMethod.equalsIgnoreCase("ANY") && !ruleMethod.equalsIgnoreCase(requestMethod)) {
            return false;
        }
        return matchesPattern(rulePattern, requestPath);
    }

    private boolean matchesPattern(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix + "/")) {
                return false;
            }
            String rest = path.substring(prefix.length() + 1);
            return !rest.contains("/") && !rest.isEmpty();
        }
        return false;
    }

    /**
     * Phân giải tiền tố URL đích tương ứng với từng service
     */
    private String resolveTargetUrl(String path) {
        if (path.startsWith("/api/v1/auth")) {
            return config.authServiceUrl();
        }
        if (path.startsWith("/api/v1/medicines") || path.startsWith("/api/v1/categories")) {
            return config.medicineServiceUrl();
        }
        if (path.startsWith("/api/v1/prescriptions")) {
            return config.prescriptionServiceUrl();
        }
        if (path.startsWith("/api/v1/cart") || path.startsWith("/api/v1/orders")) {
            return config.orderServiceUrl();
        }
        if (path.startsWith("/api/v1/payments")) {
            return config.paymentServiceUrl();
        }
        if (path.startsWith("/api/v1/notifications")) {
            return config.notificationServiceUrl();
        }
        return null;
    }

    private boolean isHopByHopHeader(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
    }

    /**
     * Phát hiện các header nội bộ do client tự gửi lên có nguy cơ giả mạo định danh/vai trò
     */
    private boolean isSpoofableInternalHeader(String headerName) {
        String lower = headerName.toLowerCase();
        if (EXACT_SPOOFABLE_HEADERS.contains(lower)) {
            return true;
        }
        return lower.startsWith("x-user-") || lower.startsWith("x-customer-");
    }

    private boolean isTimeoutError(Throwable err) {
        if (err == null) return false;
        if (err instanceof TimeoutException) {
            return true;
        }
        String msg = err.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return true;
        }
        String className = err.getClass().getName();
        return className.toLowerCase().contains("timeout");
    }

    private void handleBackendFailure(RoutingContext rc, Throwable err, String host, int port, Pipe<Buffer> pipe, String correlationId) {
        if (pipe != null) {
            pipe.close();
        }
        if (isTimeoutError(err)) {
            log.error("Timeout khi gọi tới backend " + host + ":" + port + ": " + err.getMessage());
            sendError(rc, 504, "GATEWAY_TIMEOUT", "Dịch vụ đích phản hồi quá thời gian quy định (Timeout)",
                List.of("Request timeout tới backend (" + host + ":" + port + "): " + err.getMessage()), correlationId);
        } else {
            log.error("Không thể kết nối tới backend " + host + ":" + port + ": " + err.getMessage());
            sendError(rc, 503, "SERVICE_UNAVAILABLE", "Dịch vụ đích tạm thời không khả dụng. Vui lòng thử lại sau.",
                List.of("Không thể kết nối tới dịch vụ đích (" + host + ":" + port + "): " + err.getMessage()), correlationId);
        }
    }

    private void sendError(RoutingContext rc, int statusCode, String code, String message, List<String> details, String correlationId) {
        HttpServerResponse response = rc.response();
        if (response.ended() || response.headWritten()) {
            return;
        }
        ErrorResponse errorResponse = new ErrorResponse(code, message, details);
        String json;
        try {
            json = objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            json = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":[]}";
        }

        String origin = rc.request().getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            response.putHeader("Access-Control-Allow-Origin", origin)
                .putHeader("Access-Control-Expose-Headers", "X-Correlation-Id")
                .putHeader("Access-Control-Allow-Credentials", "true");
        }

        response.setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json; charset=UTF-8")
            .putHeader("X-Correlation-Id", correlationId)
            .end(json);
    }
}
