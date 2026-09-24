package com.pharmacy.auth.service;

import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import com.pharmacy.auth.dto.response.AuthResponse;
import com.pharmacy.auth.entity.User;
import com.pharmacy.auth.exception.AuthException;
import com.pharmacy.auth.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email)) {
            throw new AuthException("Email đã được sử dụng");
        }

        User user = new User();
        user.email = request.email;
        user.password = BcryptUtil.bcryptHash(request.password); // hash mật khẩu
        user.fullName = request.fullName;
        user.phone = request.phone;

        userRepository.persist(user);

        String token = generateToken(user);
        return new AuthResponse(token, user.email, user.fullName, user.role.name());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email)
                .orElseThrow(() -> new AuthException("Email hoặc mật khẩu không đúng"));

        if (!BcryptUtil.matches(request.password, user.password)) {
            throw new AuthException("Email hoặc mật khẩu không đúng");
        }

        if (!user.active) {
            throw new AuthException("Tài khoản đã bị khóa");
        }

        String token = generateToken(user);
        return new AuthResponse(token, user.email, user.fullName, user.role.name());
    }

    private String generateToken(User user) {
        return Jwt.issuer("https://pharmacy.com/issuer")
                .upn(user.email)
                .groups(Set.of(user.role.name()))
                .claim("fullName", user.fullName)
                .claim("userId", user.id)
                .expiresIn(Duration.ofHours(24))
                .sign();
    }
}