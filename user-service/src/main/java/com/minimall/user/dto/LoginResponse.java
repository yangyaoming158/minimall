package com.minimall.user.dto;

public record LoginResponse(
        String token,
        String tokenType,
        Long userId,
        String username) {
}
