package com.minimall.common.auth.context;

public enum AuthRole {
    USER,
    ADMIN;

    public static AuthRole fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("role claim must not be blank");
        }
        return AuthRole.valueOf(claim);
    }
}
