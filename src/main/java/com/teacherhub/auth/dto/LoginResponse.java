package com.teacherhub.auth.dto;

import com.teacherhub.user.enums.UserRole;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {

    public record UserInfo(
            Long userId,
            String email,
            String name,
            UserRole role
    ) {
    }
}