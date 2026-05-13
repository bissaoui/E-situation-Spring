package com.example.situation.controller;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import com.example.situation.security.ModelSanitizer;
import com.example.situation.service.AuditService;
import com.example.situation.service.PasswordPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/users", "/api/v1/users"})
@Tag(name = "Users API (Admin)")
@Validated
public class UserApiController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelSanitizer modelSanitizer;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditService auditService;

    public UserApiController(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        ModelSanitizer modelSanitizer,
        PasswordPolicyService passwordPolicyService,
        AuditService auditService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.modelSanitizer = modelSanitizer;
        this.passwordPolicyService = passwordPolicyService;
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List all users (admin only)")
    public List<AppUser> findAll() {
        return appUserRepository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user by id (admin only)")
    public AppUser findById(@PathVariable @Positive Long id) {
        return appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid user id: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create user (admin only)")
    public AppUser create(@Valid @RequestBody AppUser input, Authentication authentication) {
        modelSanitizer.sanitizeUser(input);
        if (input.getPassword() == null || input.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (appUserRepository.existsByUsernameIgnoreCase(input.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + input.getUsername());
        }
        passwordPolicyService.validate(input.getPassword(), input.getUsername());
        input.setPassword(passwordEncoder.encode(input.getPassword()));
        AppUser saved = appUserRepository.save(input);
        auditService.logSensitiveAction("USER_CREATED", actor(authentication), saved.getUsername(), saved.getRole());
        return saved;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user (admin only)")
    public AppUser update(@PathVariable @Positive Long id, @Valid @RequestBody AppUser input, Authentication authentication) {
        modelSanitizer.sanitizeUser(input);
        AppUser existing = appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid user id: " + id));

        if (!existing.getUsername().equals(input.getUsername())
            && appUserRepository.existsByUsernameIgnoreCase(input.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + input.getUsername());
        }

        existing.setUsername(input.getUsername());
        existing.setRole(input.getRole());
        if (input.getPassword() != null && !input.getPassword().isBlank()) {
            passwordPolicyService.validate(input.getPassword(), input.getUsername());
            existing.setPassword(passwordEncoder.encode(input.getPassword()));
        }
        AppUser saved = appUserRepository.save(existing);
        auditService.logSensitiveAction("USER_UPDATED", actor(authentication), saved.getUsername(), saved.getRole());
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user (admin only)")
    public void delete(@PathVariable @Positive Long id, Authentication authentication) {
        AppUser existing = appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid user id: " + id));
        appUserRepository.deleteById(id);
        auditService.logSensitiveAction("USER_DELETED", actor(authentication), existing.getUsername(), existing.getRole());
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "-" : authentication.getName();
    }
}
