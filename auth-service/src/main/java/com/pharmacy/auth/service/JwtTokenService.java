package com.pharmacy.auth.service;

import com.pharmacy.auth.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Set;

/**
 * Service ký và phát hành JSON Web Token (JWT) sử dụng SmallRye JWT Build và RSA Private Key
 * Tuân thủ 100% cấu trúc claims trong docs/JWT-CONTRACT.md
 */
@ApplicationScoped
public class JwtTokenService {

    private static final Logger LOG = Logger.getLogger(JwtTokenService.class);
    public static final long TOKEN_EXPIRES_IN_SECONDS = 24 * 3600L; // 24 giờ

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://pharmacy.com/issuer")
    String issuer;

    public String generateToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Thông tin người dùng không hợp lệ để cấp phát JWT");
        }

        String roleName = user.getRole() != null ? user.getRole().name() : "KHACH_HANG";
        String token = Jwt.issuer(issuer)
                .subject(user.getId().toString())
                .upn(user.getEmail())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .groups(Set.of(roleName))
                .expiresIn(Duration.ofSeconds(TOKEN_EXPIRES_IN_SECONDS))
                .sign();

        LOG.infof("Đã cấp phát thành công JWT token cho user: %s (role: %s)", user.getEmail(), roleName);
        return token;
    }

    public long getExpiresInSeconds() {
        return TOKEN_EXPIRES_IN_SECONDS;
    }
}

