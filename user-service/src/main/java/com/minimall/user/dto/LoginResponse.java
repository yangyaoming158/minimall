package com.minimall.user.dto;

import com.minimall.user.domain.UserRole;

public record LoginResponse(
        String token,
        String tokenType,
        Long userId,
        String username,
        UserRole role) {
}
