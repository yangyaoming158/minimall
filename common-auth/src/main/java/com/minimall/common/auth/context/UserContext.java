package com.minimall.common.auth.context;

import java.util.Objects;

public final class UserContext {

    private final Long userId;
    private final String username;

    private UserContext(Long userId, String username) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
    }

    public static UserContext of(Long userId, String username) {
        return new UserContext(userId, username);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}
