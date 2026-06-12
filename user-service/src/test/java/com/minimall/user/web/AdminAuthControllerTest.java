package com.minimall.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.auth.constants.AuthHeaders;
import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.context.UserContextHolder;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.user.domain.User;
import com.minimall.user.domain.UserRole;
import com.minimall.user.dto.LoginRequest;
import com.minimall.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_auth_controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "minimall.auth.jwt.secret=test-jwt-secret-for-admin-auth",
        "minimall.auth.jwt.expire-seconds=3600"
})
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        UserContextHolder.clear();
    }

    @Test
    void adminLoginReturnsAdminTokenAndResponse() throws Exception {
        User admin = new User("admin", passwordEncoder.encode("password123"), null, null);
        admin.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(admin);

        String responseBody = mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(responseBody).get("data").get("token").asText();
        assertThat(jwtUtils.parseToken(token).getRole()).isEqualTo(AuthRole.ADMIN);
    }

    @Test
    void adminLoginRejectsUserCredentialsWithForbidden() throws Exception {
        userRepository.saveAndFlush(new User("alice", passwordEncoder.encode("password123"), null, null));

        mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    @Test
    void adminLoginWithBadCredentialsReturnsUnauthorized() throws Exception {
        userRepository.saveAndFlush(new User("admin", passwordEncoder.encode("password123"), null, null));

        mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Username or password is incorrect"));
    }

    @Test
    void adminMeReturnsAdminFromBearerToken() throws Exception {
        String token = jwtUtils.generateToken(42L, "admin", AuthRole.ADMIN);

        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void adminMeWithUserBearerTokenReturnsForbidden() throws Exception {
        String token = jwtUtils.generateToken(43L, "alice", AuthRole.USER);

        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void adminMeWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }

    @Test
    void adminMeWithUserPropagationHeadersReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header(AuthHeaders.USER_ID, "44")
                        .header(AuthHeaders.USERNAME, "alice"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()));

        assertThat(UserContextHolder.hasContext()).isFalse();
    }
}
