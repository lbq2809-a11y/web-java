package com.pharmacy.auth.repository;

import com.pharmacy.auth.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    public Optional<User> findByEmailOptional(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return find("LOWER(email) = ?1", email.trim().toLowerCase()).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return count("LOWER(email) = ?1", email.trim().toLowerCase()) > 0;
    }
}

