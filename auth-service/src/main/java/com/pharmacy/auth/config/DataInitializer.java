package com.pharmacy.auth.config;

import com.pharmacy.auth.entity.Role;
import com.pharmacy.auth.entity.User;
import com.pharmacy.auth.repository.UserRepository;
import com.pharmacy.auth.service.PasswordEncoder;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Khởi tạo dữ liệu seed cho môi trường dev/test khi ứng dụng khởi động
 */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Inject
    UserRepository userRepo;

    @Inject
    PasswordEncoder passwordEncoder;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        seedUser(UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "khach_hang@pharmacy.local", "Nguyễn Văn A", "0901234567", Role.KHACH_HANG);
        seedUser(UUID.fromString("22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "duoc_si@pharmacy.local", "DS. Trần Thị Dược", "0902345678", Role.DUOC_SI);
        seedUser(UUID.fromString("00000000-0000-0000-0000-000000000000"), "admin@pharmacy.local", "Quản trị viên Admin", "0909999999", Role.ADMIN);
    }

    public void seedUser(UUID id, String email, String fullName, String phone, Role role) {
        Optional<User> existing = userRepo.findByEmailOptional(email);
        if (existing.isEmpty()) {
            User user = new User();
            user.setId(id);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("123456"));
            user.setFullName(fullName);
            user.setPhone(phone);
            user.setRole(role);
            user.setActive(true);
            userRepo.persist(user);
            LOG.infof("Đã khởi tạo tài khoản seed: %s (role: %s)", email, role);
        } else {
            // Đảm bảo mật khẩu luôn khớp với 123456
            User user = existing.get();
            if (!passwordEncoder.matches("123456", user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode("123456"));
                userRepo.persist(user);
                LOG.infof("Đã cập nhật lại mật khẩu chuẩn 123456 cho tài khoản seed: %s", email);
            }
        }
    }
}
