package com.minimall.user.dto;

import com.minimall.user.domain.User;
import com.minimall.user.domain.UserStatus;

public record UserResponse(
        Long userId,
        String username,
        String email,
        String phone,
        UserStatus status) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus());
    }
}
