package com.example.situation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyService {

    private final int minLength;

    public PasswordPolicyService(@Value("${security.password.min-length:12}") int minLength) {
        this.minLength = minLength;
    }

    public void validate(String password, String username) {
        if (password == null || password.length() < minLength) {
            throw new IllegalArgumentException("Password does not meet the security requirements.");
        }
        if (!password.chars().anyMatch(Character::isUpperCase)
            || !password.chars().anyMatch(Character::isLowerCase)
            || !password.chars().anyMatch(Character::isDigit)
            || password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw new IllegalArgumentException("Password does not meet the security requirements.");
        }
        if (username != null && !username.isBlank()
            && password.toLowerCase().contains(username.trim().toLowerCase())) {
            throw new IllegalArgumentException("Password does not meet the security requirements.");
        }
    }

    public int getMinLength() {
        return minLength;
    }
}
