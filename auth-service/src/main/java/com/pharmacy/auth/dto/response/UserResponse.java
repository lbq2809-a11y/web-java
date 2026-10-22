package com.pharmacy.auth.dto.response;

import com.pharmacy.auth.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean active,
        Instant createdAt
) {
}

