package com.minimall.user.dto;

import com.minimall.common.auth.context.UserContext;
import com.minimall.user.domain.UserRole;

public record CurrentUserResponse(
        Long userId,
        String username,
        UserRole role) {

    public static CurrentUserResponse from(UserContext userContext) {
        return new CurrentUserResponse(
                userContext.getUserId(),
                userContext.getUsername(),
                UserRole.valueOf(userContext.getRole().name()));
    }
}
