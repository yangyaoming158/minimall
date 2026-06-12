package com.minimall.user.seed;

import com.minimall.user.config.AdminSeedProperties;
import com.minimall.user.domain.User;
import com.minimall.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final AdminSeedProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeedRunner(
            AdminSeedProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Admin seed is disabled");
            return;
        }
        if (!properties.hasCredentials()) {
            log.warn("Admin seed is enabled but username or password is missing; skipping");
            return;
        }

        seedAdmin();
    }

    private void seedAdmin() {
        String username = properties.normalizedUsername();
        String passwordHash = passwordEncoder.encode(properties.getPassword());

        userRepository.findByUsername(username)
                .ifPresentOrElse(
                        user -> updateAdmin(user, passwordHash),
                        () -> createAdmin(username, passwordHash));
    }

    private void createAdmin(String username, String passwordHash) {
        User admin = new User(username, passwordHash, trimToNull(properties.getEmail()), trimToNull(properties.getPhone()));
        admin.promoteToAdmin();
        userRepository.save(admin);
        log.info("Seeded initial admin user '{}'", username);
    }

    private void updateAdmin(User user, String passwordHash) {
        user.promoteToAdmin();
        user.updatePasswordHash(passwordHash);
        if (properties.hasEmail() || properties.hasPhone()) {
            user.updateContact(
                    properties.hasEmail() ? trimToNull(properties.getEmail()) : user.getEmail(),
                    properties.hasPhone() ? trimToNull(properties.getPhone()) : user.getPhone());
        }
        userRepository.save(user);
        log.info("Updated configured admin user '{}'", user.getUsername());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
