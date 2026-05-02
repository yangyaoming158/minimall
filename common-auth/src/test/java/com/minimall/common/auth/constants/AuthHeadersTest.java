package com.minimall.common.auth.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHeadersTest {

    @Test
    void definesUserPropagationHeaderNames() {
        assertThat(AuthHeaders.USER_ID).isEqualTo("X-User-Id");
        assertThat(AuthHeaders.USERNAME).isEqualTo("X-Username");
    }
}
