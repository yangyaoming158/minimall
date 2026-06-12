package com.minimall.user.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.minimall.user.config.AdminSeedProperties;
import com.minimall.user.domain.User;
import com.minimall.user.domain.UserRole;
import com.minimall.user.domain.UserStatus;
import com.minimall.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AdminSeedRunnerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private UserRepository userRepository;

    @Test
    void disabledSeedDoesNotCreateAdmin() throws Exception {
        AdminSeedProperties properties = properties(false, "admin", "password123");

        runner(properties).run(new DefaultApplicationArguments());

        assertThat(userRepository.findByUsername("admin")).isEmpty();
    }

    @Test
    void enabledSeedWithoutCredentialsDoesNotCreateAdmin() throws Exception {
        AdminSeedProperties properties = properties(true, "admin", null);

        runner(properties).run(new DefaultApplicationArguments());

        assertThat(userRepository.findByUsername("admin")).isEmpty();
    }

    @Test
    void enabledSeedCreatesAdminWithBcryptPassword() throws Exception {
        AdminSeedProperties properties = properties(true, " admin ", "password123");
        properties.setEmail(" admin@example.com ");
        properties.setPhone(" 13800000000 ");

        runner(properties).run(new DefaultApplicationArguments());

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", admin.getPasswordHash())).isTrue();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getPhone()).isEqualTo("13800000000");
    }

    @Test
    void enabledSeedPromotesExistingUserAndUpdatesPasswordWithoutClearingContact() throws Exception {
        User existing = new User("admin", passwordEncoder.encode("old-password"), "old@example.com", "13900000000");
        existing.setRole(UserRole.USER);
        existing.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(existing);

        AdminSeedProperties properties = properties(true, "admin", "new-password");

        runner(properties).run(new DefaultApplicationArguments());

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("new-password", admin.getPasswordHash())).isTrue();
        assertThat(admin.getEmail()).isEqualTo("old@example.com");
        assertThat(admin.getPhone()).isEqualTo("13900000000");
    }

    private AdminSeedRunner runner(AdminSeedProperties properties) {
        return new AdminSeedRunner(properties, userRepository, passwordEncoder);
    }

    private AdminSeedProperties properties(boolean enabled, String username, String password) {
        AdminSeedProperties properties = new AdminSeedProperties();
        properties.setEnabled(enabled);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }
}
