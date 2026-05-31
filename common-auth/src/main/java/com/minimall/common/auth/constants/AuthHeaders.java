package com.minimall.common.auth.constants;

public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";

    /**
     * Shared secret the gateway injects on every request it forwards, and that
     * service-to-service internal callers must present. Downstream services only
     * trust the {@code X-User-*} propagation headers and expose {@code /internal/**}
     * when this header matches the configured secret, so a caller that bypasses the
     * gateway (e.g. by hitting a service port directly) cannot forge an admin
     * identity or reach internal APIs.
     */
    public static final String GATEWAY_TOKEN = "X-Internal-Token";

    private AuthHeaders() {
    }
}
