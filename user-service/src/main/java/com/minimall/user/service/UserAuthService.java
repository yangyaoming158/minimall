package com.minimall.user.service;

import com.minimall.common.auth.context.AuthRole;
import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.user.domain.User;
import com.minimall.user.dto.LoginRequest;
import com.minimall.user.dto.LoginResponse;
import com.minimall.user.dto.RegisterRequest;
import com.minimall.user.dto.UserResponse;
import com.minimall.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {

    private static final String USERNAME_EXISTS_MESSAGE = "Username already exists";
    private static final String BAD_CREDENTIALS_MESSAGE = "Username or password is incorrect";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public UserAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.CONFLICT, USERNAME_EXISTS_MESSAGE);
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.phone());

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, USERNAME_EXISTS_MESSAGE, exception);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(this::badCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw badCredentials();
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), AuthRole.valueOf(user.getRole().name()));
        return new LoginResponse(token, "Bearer", user.getId(), user.getUsername(), user.getRole());
    }

    private BusinessException badCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS_MESSAGE);
    }
}
