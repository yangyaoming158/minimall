package com.minimall.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.user.domain.User;
import com.minimall.user.domain.UserRole;
import com.minimall.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserAndFindsByUsername() {
        User saved = userRepository.saveAndFlush(new User("alice", "$2a$10$hash", "alice@example.com", "13800000000"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(userRepository.findByUsername("alice"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("alice@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    @Test
    void persistsStatusAsEnumValue() {
        User user = new User("disabled-user", "$2a$10$hash", null, null);
        user.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByUsername("disabled-user"))
                .isPresent()
                .get()
                .extracting(User::getStatus)
                .isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void persistsRoleAsEnumValue() {
        User user = new User("admin-user", "$2a$10$hash", null, null);
        user.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByUsername("admin-user"))
                .isPresent()
                .get()
                .extracting(User::getRole)
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void defaultsNullRoleToUserOnPersist() {
        User user = new User("default-role", "$2a$10$hash", null, null);
        user.setRole(null);

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(userRepository.findByUsername("default-role"))
                .isPresent()
                .get()
                .extracting(User::getRole)
                .isEqualTo(UserRole.USER);
    }

    @Test
    void duplicateUsernameViolatesUniqueConstraint() {
        userRepository.saveAndFlush(new User("duplicate", "$2a$10$hash", null, null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("duplicate", "$2a$10$hash2", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
