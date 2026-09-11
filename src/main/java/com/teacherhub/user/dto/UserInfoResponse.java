package com.teacherhub.user.dto;

import com.teacherhub.user.enums.UserRole;

public record UserInfoResponse(
        Long userId,
        String email,
        String name,
        UserRole role
) {
}