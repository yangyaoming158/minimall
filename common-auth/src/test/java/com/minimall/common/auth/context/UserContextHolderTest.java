package com.minimall.common.auth.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void storesUserContextForCurrentThread() {
        UserContext userContext = UserContext.of(1001L, "alice");

        UserContextHolder.set(userContext);

        assertThat(UserContextHolder.hasContext()).isTrue();
        assertThat(UserContextHolder.get()).containsSame(userContext);
        assertThat(UserContextHolder.getOrNull()).isSameAs(userContext);
        assertThat(UserContextHolder.require()).isSameAs(userContext);
    }

    @Test
    void clearsUserContext() {
        UserContextHolder.set(UserContext.of(1001L, "alice"));

        UserContextHolder.clear();

        assertThat(UserContextHolder.hasContext()).isFalse();
        assertThat(UserContextHolder.get()).isEmpty();
        assertThat(UserContextHolder.getOrNull()).isNull();
    }

    @Test
    void rejectsNullUserContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> UserContextHolder.set(null))
                .withMessage("userContext must not be null");
    }

    @Test
    void requiresAvailableUserContext() {
        assertThatIllegalStateException()
                .isThrownBy(UserContextHolder::require)
                .withMessage("User context is not available");
    }
}
