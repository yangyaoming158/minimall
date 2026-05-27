package com.minimall.common.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.minimall.common.auth.config.JwtProperties;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "test-secret-with-enough-entropy-for-hmac";

    @Test
    void generatesAndParsesToken() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        String token = jwtUtils.generateToken(1001L, "alice");
        UserContext userContext = jwtUtils.parseToken(token);

        assertThat(token).isNotBlank();
        assertThat(userContext.getUserId()).isEqualTo(1001L);
        assertThat(userContext.getUsername()).isEqualTo("alice");
        assertThat(userContext.getRole()).isEqualTo(AuthRole.USER);
    }

    @Test
    void generatesAndParsesTokenWithExplicitRole() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        String token = jwtUtils.generateToken(1001L, "alice", AuthRole.ADMIN);
        UserContext userContext = jwtUtils.parseToken(token);

        assertThat(userContext.getUserId()).isEqualTo(1001L);
        assertThat(userContext.getUsername()).isEqualTo("alice");
        assertThat(userContext.getRole()).isEqualTo(AuthRole.ADMIN);
    }

    @Test
    void parsesBearerToken() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        String token = jwtUtils.generateToken(1001L, "alice");

        assertThat(jwtUtils.parseToken("Bearer " + token).getUserId()).isEqualTo(1001L);
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        String token = jwtUtils.generateToken(1001L, "alice");
        String tamperedToken = tamperSignature(token);

        assertUnauthorized(() -> jwtUtils.parseToken(tamperedToken), "Invalid or expired token");
    }

    @Test
    void rejectsExpiredToken() {
        JwtUtils jwtUtils = new JwtUtils(properties(-1));

        String token = jwtUtils.generateToken(1001L, "alice");

        assertUnauthorized(() -> jwtUtils.parseToken(token), "Invalid or expired token");
    }

    @Test
    void rejectsTokenWithMissingClaims() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));
        String token = JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(SECRET));

        assertUnauthorized(() -> jwtUtils.parseToken(token), "Invalid token claims");
    }

    @Test
    void rejectsTokenWithInvalidRoleClaim() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));
        String token = JWT.create()
                .withClaim("userId", 1001L)
                .withClaim("username", "alice")
                .withClaim("role", "ROOT")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(SECRET));

        assertUnauthorized(() -> jwtUtils.parseToken(token), "Invalid token claims");
    }

    @Test
    void rejectsMissingToken() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        assertUnauthorized(() -> jwtUtils.parseToken("Bearer  "), "Missing token");
    }

    @Test
    void rejectsBlankSecret() {
        JwtProperties properties = properties(3600);
        properties.setSecret(" ");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtUtils(properties))
                .withMessage("JWT secret must not be blank");
    }

    @Test
    void rejectsMissingRequiredTokenInputs() {
        JwtUtils jwtUtils = new JwtUtils(properties(3600));

        assertThatNullPointerException()
                .isThrownBy(() -> jwtUtils.generateToken(null, "alice"))
                .withMessage("userId must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> jwtUtils.generateToken(1001L, null))
                .withMessage("username must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> jwtUtils.generateToken(1001L, "alice", null))
                .withMessage("role must not be null");
    }

    private static JwtProperties properties(long expireSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpireSeconds(expireSeconds);
        return properties;
    }

    private static String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static void assertUnauthorized(ThrowingCallable callable, String message) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(businessException.getMessage()).isEqualTo(message);
                });
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
