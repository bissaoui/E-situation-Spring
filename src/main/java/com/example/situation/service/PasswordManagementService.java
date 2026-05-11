package com.example.situation.service;

import com.example.situation.dto.ChangePasswordRequest;
import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordManagementService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordManagementService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = appUserRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("New password confirmation does not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);
    }
}
