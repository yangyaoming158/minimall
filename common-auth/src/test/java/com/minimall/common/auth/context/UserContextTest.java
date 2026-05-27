package com.minimall.common.auth.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserContextTest {

    @Test
    void createsUserContext() {
        UserContext userContext = UserContext.of(1001L, "alice");

        assertThat(userContext.getUserId()).isEqualTo(1001L);
        assertThat(userContext.getUsername()).isEqualTo("alice");
        assertThat(userContext.getRole()).isEqualTo(AuthRole.USER);
    }

    @Test
    void createsUserContextWithExplicitRole() {
        UserContext userContext = UserContext.of(1001L, "alice", AuthRole.ADMIN);

        assertThat(userContext.getUserId()).isEqualTo(1001L);
        assertThat(userContext.getUsername()).isEqualTo("alice");
        assertThat(userContext.getRole()).isEqualTo(AuthRole.ADMIN);
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> UserContext.of(null, "alice"))
                .withMessage("userId must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> UserContext.of(1001L, null))
                .withMessage("username must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> UserContext.of(1001L, "alice", null))
                .withMessage("role must not be null");
    }
}
