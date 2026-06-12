package com.minimall.common.auth.context;

import java.util.Objects;

public final class UserContext {

    private final Long userId;
    private final String username;
    private final AuthRole role;

    private UserContext(Long userId, String username, AuthRole role) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public static UserContext of(Long userId, String username) {
        return of(userId, username, AuthRole.USER);
    }

    public static UserContext of(Long userId, String username, AuthRole role) {
        return new UserContext(userId, username, role);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public AuthRole getRole() {
        return role;
    }
}
