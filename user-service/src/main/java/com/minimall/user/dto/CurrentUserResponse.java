package com.minimall.user.dto;

import com.minimall.common.auth.context.UserContext;

public record CurrentUserResponse(
        Long userId,
        String username) {

    public static CurrentUserResponse from(UserContext userContext) {
        return new CurrentUserResponse(userContext.getUserId(), userContext.getUsername());
    }
}
