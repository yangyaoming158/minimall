package com.minimall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 64, message = "username length must be at most 64")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 6, max = 128, message = "password length must be between 6 and 128")
        String password,

        @Email(message = "email must be valid")
        @Size(max = 128, message = "email length must be at most 128")
        String email,

        @Size(max = 32, message = "phone length must be at most 32")
        String phone) {
}
