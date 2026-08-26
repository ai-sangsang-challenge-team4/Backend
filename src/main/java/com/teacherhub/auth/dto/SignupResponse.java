package com.teacherhub.auth.dto;

import com.teacherhub.user.enums.UserRole;

import java.time.LocalDateTime;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        UserRole role,
        LocalDateTime createdAt
) {
}