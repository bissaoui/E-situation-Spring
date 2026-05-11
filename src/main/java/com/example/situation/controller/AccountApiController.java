package com.example.situation.controller;

import com.example.situation.dto.ChangePasswordRequest;
import com.example.situation.service.PasswordManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account API")
@Validated
public class AccountApiController {

    private final PasswordManagementService passwordManagementService;

    public AccountApiController(PasswordManagementService passwordManagementService) {
        this.passwordManagementService = passwordManagementService;
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Change password for authenticated user")
    public Map<String, String> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication
    ) {
        passwordManagementService.changePassword(resolveUsername(authentication), request);
        return Map.of("message", "Password updated successfully");
    }

    private static String resolveUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
        return authentication.getName();
    }
}
