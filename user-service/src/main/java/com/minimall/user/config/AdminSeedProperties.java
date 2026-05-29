package com.minimall.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "minimall.admin.seed")
public class AdminSeedProperties {

    private boolean enabled;
    private String username;
    private String password;
    private String email;
    private String phone;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean hasCredentials() {
        return StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    public boolean hasEmail() {
        return StringUtils.hasText(email);
    }

    public boolean hasPhone() {
        return StringUtils.hasText(phone);
    }

    public String normalizedUsername() {
        return username.trim();
    }
}
