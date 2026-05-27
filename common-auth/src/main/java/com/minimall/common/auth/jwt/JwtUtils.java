package com.minimall.common.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.minimall.common.auth.config.JwtProperties;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

public class JwtUtils {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_CLAIM = "userId";
    private static final String USERNAME_CLAIM = "username";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Clock clock;

    public JwtUtils(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtUtils(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, AuthRole.USER);
    }

    public String generateToken(Long userId, String username, AuthRole role) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");

        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.getExpireSeconds());

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim(USER_ID_CLAIM, userId)
                .withClaim(USERNAME_CLAIM, username)
                .withClaim(ROLE_CLAIM, role.name())
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    public UserContext parseToken(String token) {
        String normalizedToken = normalizeToken(token);
        DecodedJWT decodedJwt;
        try {
            decodedJwt = verifier.verify(normalizedToken);
        } catch (JWTVerificationException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token", exception);
        }

        Long userId = decodedJwt.getClaim(USER_ID_CLAIM).asLong();
        String username = decodedJwt.getClaim(USERNAME_CLAIM).asString();
        AuthRole role = parseRole(decodedJwt.getClaim(ROLE_CLAIM).asString());
        if (userId == null || username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token claims");
        }

        return UserContext.of(userId, username, role);
    }

    private AuthRole parseRole(String roleClaim) {
        try {
            return AuthRole.fromClaim(roleClaim);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token claims", exception);
        }
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing token");
        }

        String normalizedToken = token.trim();
        if ("Bearer".equalsIgnoreCase(normalizedToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing token");
        }
        if (normalizedToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            normalizedToken = normalizedToken.substring(BEARER_PREFIX.length()).trim();
        }

        if (normalizedToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing token");
        }

        return normalizedToken;
    }
}
