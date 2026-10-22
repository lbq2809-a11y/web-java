package com.pharmacy.auth.service;

import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import com.pharmacy.auth.dto.response.AuthResponse;
import com.pharmacy.auth.dto.response.UserResponse;
import com.pharmacy.auth.entity.Role;
import com.pharmacy.auth.entity.User;
import com.pharmacy.auth.exception.ApiException;
import com.pharmacy.auth.mapper.UserMapper;
import com.pharmacy.auth.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Service xử lý nghiệp vụ xác thực: Đăng ký khách hàng, Đăng nhập, và Lấy thông tin hồ sơ
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    UserRepository userRepo;

    @Inject
    PasswordEncoder passwordEncoder;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    UserMapper userMapper;

    @Inject
    JsonWebToken jwt;

    /**
     * Đăng ký tài khoản khách hàng mới
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw ApiException.badRequest("EMAIL_ALREADY_EXISTS", "Email đã được sử dụng bởi một tài khoản khác");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone() != null ? req.phone().trim() : null);
        user.setRole(Role.KHACH_HANG);
        user.setActive(true);

        userRepo.persist(user);
        LOG.infof("Đã tạo mới tài khoản khách hàng: %s (id: %s)", user.getEmail(), user.getId());

        String token = jwtTokenService.generateToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                jwtTokenService.getExpiresInSeconds(),
                userMapper.toUserResponse(user)
        );
    }

    /**
     * Đăng nhập bằng email và mật khẩu
     */
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepo.findByEmailOptional(email)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            LOG.warnf("Đăng nhập thất bại do sai mật khẩu cho tài khoản: %s", email);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        if (!user.isActive()) {
            LOG.warnf("Tài khoản %s đang bị khóa nhưng cố đăng nhập", email);
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản của bạn đã bị vô hiệu hóa, vui lòng liên hệ quản trị viên");
        }

        String token = jwtTokenService.generateToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                jwtTokenService.getExpiresInSeconds(),
                userMapper.toUserResponse(user)
        );
    }

    /**
     * Lấy thông tin tài khoản hiện tại từ JWT token
     */
    public UserResponse getMe() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Không xác định được danh tính từ token");
        }

        UUID userId;
        try {
            userId = UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "ID người dùng trong token không đúng định dạng UUID");
        }

        User user = userRepo.findByIdOptional(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin tài khoản"));

        return userMapper.toUserResponse(user);
    }
}

