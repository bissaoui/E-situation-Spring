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
    private final PasswordPolicyService passwordPolicyService;

    public PasswordManagementService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        PasswordPolicyService passwordPolicyService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Unable to update password."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Unable to update password.");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Unable to update password.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Unable to update password.");
        }

        passwordPolicyService.validate(request.getNewPassword(), user.getUsername());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);
    }
}
