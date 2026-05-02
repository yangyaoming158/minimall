package com.minimall.common.auth.context;

import java.util.Objects;
import java.util.Optional;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext userContext) {
        CONTEXT.set(Objects.requireNonNull(userContext, "userContext must not be null"));
    }

    public static Optional<UserContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static UserContext getOrNull() {
        return CONTEXT.get();
    }

    public static UserContext require() {
        UserContext userContext = CONTEXT.get();
        if (userContext == null) {
            throw new IllegalStateException("User context is not available");
        }
        return userContext;
    }

    public static boolean hasContext() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
