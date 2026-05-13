package com.example.situation.controller;

import com.example.situation.dto.AuthResponse;
import com.example.situation.dto.AccountProfileResponse;
import com.example.situation.dto.ChangePasswordRequest;
import com.example.situation.dto.DeletionRequestInput;
import com.example.situation.dto.MfaSetupResponse;
import com.example.situation.dto.MfaStatusResponse;
import com.example.situation.dto.MfaVerifyRequest;
import com.example.situation.dto.MyDataExportResponse;
import com.example.situation.dto.PrivacyAcknowledgementRequest;
import com.example.situation.model.AppUser;
import com.example.situation.repository.SituationRepository;
import com.example.situation.repository.AppUserRepository;
import com.example.situation.security.JwtService;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import com.example.situation.security.TotpService;
import com.example.situation.service.AppUserDetailsService;
import com.example.situation.service.AuditService;
import com.example.situation.service.LoginSecurityService;
import com.example.situation.service.PasswordManagementService;
import com.example.situation.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/account", "/api/v1/account"})
@Tag(name = "Account API")
@Validated
public class AccountApiController {

    private final PasswordManagementService passwordManagementService;
    private final AppUserRepository appUserRepository;
    private final SituationRepository situationRepository;
    private final ProjetAccessService projetAccessService;
    private final TotpService totpService;
    private final LoginSecurityService loginSecurityService;
    private final AppUserDetailsService appUserDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public AccountApiController(
        PasswordManagementService passwordManagementService,
        AppUserRepository appUserRepository,
        SituationRepository situationRepository,
        ProjetAccessService projetAccessService,
        TotpService totpService,
        LoginSecurityService loginSecurityService,
        AppUserDetailsService appUserDetailsService,
        JwtService jwtService,
        RefreshTokenService refreshTokenService,
        AuditService auditService
    ) {
        this.passwordManagementService = passwordManagementService;
        this.appUserRepository = appUserRepository;
        this.situationRepository = situationRepository;
        this.projetAccessService = projetAccessService;
        this.totpService = totpService;
        this.loginSecurityService = loginSecurityService;
        this.appUserDetailsService = appUserDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current account profile")
    public AccountProfileResponse me(Authentication authentication) {
        return toProfile(currentUser(authentication));
    }

    @GetMapping("/my-data/export")
    @Operation(summary = "Export current account profile and scope metadata")
    public MyDataExportResponse exportMyData(Authentication authentication) {
        AppUser user = currentUser(authentication);
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        long accessibleSituationCount = switch (scope) {
            case ALL -> situationRepository.count();
            case DDZA_ONLY, DDZO_ONLY -> situationRepository.findByProjetContainingIgnoreCase(
                projetAccessService.requiredProjetToken(scope)
            ).size();
            case NONE -> 0;
        };
        auditService.logSensitiveAction("ACCOUNT_DATA_EXPORT", user.getUsername(), user.getUsername(), "Current user exported their profile data");
        return new MyDataExportResponse(
            Instant.now(),
            toProfile(user),
            scope.name(),
            accessibleSituationCount
        );
    }

    @PostMapping("/password")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<Map<String, String>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        String username = resolveUsername(authentication);
        passwordManagementService.changePassword(username, request);
        auditService.logPasswordChange(username, clientIp(servletRequest), userAgent(servletRequest));
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @PostMapping("/privacy/acknowledge")
    @Operation(summary = "Store privacy notice acknowledgement")
    public ResponseEntity<Map<String, String>> acknowledgePrivacy(
        @Valid @RequestBody PrivacyAcknowledgementRequest request,
        Authentication authentication
    ) {
        AppUser user = currentUser(authentication);
        user.setPrivacyNoticeVersion(request.getVersion());
        user.setPrivacyNoticeAcceptedAt(Instant.now());
        appUserRepository.save(user);
        auditService.logSensitiveAction("PRIVACY_NOTICE_ACKNOWLEDGED", user.getUsername(), user.getUsername(), request.getVersion());
        return ResponseEntity.ok(Map.of("message", "Privacy notice acknowledgement stored."));
    }

    @PostMapping("/deletion-request")
    @Operation(summary = "Create an account deletion request")
    public ResponseEntity<Map<String, String>> requestDeletion(
        @Valid @RequestBody DeletionRequestInput request,
        Authentication authentication
    ) {
        AppUser user = currentUser(authentication);
        user.setDeletionRequestedAt(Instant.now());
        user.setDeletionRequestReason(request.getReason());
        appUserRepository.save(user);
        auditService.logSensitiveAction("ACCOUNT_DELETION_REQUESTED", user.getUsername(), user.getUsername(), "Deletion request submitted");
        return ResponseEntity.ok(Map.of("message", "Deletion request recorded."));
    }

    @GetMapping("/mfa")
    @Operation(summary = "Get current MFA status")
    public MfaStatusResponse getMfaStatus(Authentication authentication) {
        AppUser user = currentUser(authentication);
        return new MfaStatusResponse(user.isMfaEnabled(), loginSecurityService.isSensitiveRole(user));
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Create or retrieve MFA enrollment secret")
    public MfaSetupResponse setupMfa(Authentication authentication) {
        AppUser user = currentUser(authentication);
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            user.setMfaSecret(totpService.generateSecret());
            appUserRepository.save(user);
        }
        return new MfaSetupResponse(
            user.isMfaEnabled(),
            loginSecurityService.isSensitiveRole(user),
            user.isMfaEnabled() ? null : user.getMfaSecret(),
            totpService.buildOtpAuthUri(user, user.getMfaSecret())
        );
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Enable MFA after verifying an authenticator code")
    public ResponseEntity<AuthResponse> enableMfa(
        @Valid @RequestBody MfaVerifyRequest request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        AppUser user = currentUser(authentication);
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            user.setMfaSecret(totpService.generateSecret());
        }
        if (!totpService.verifyCode(user.getMfaSecret(), request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid multi-factor authentication code");
        }

        user.setMfaEnabled(true);
        appUserRepository.save(user);
        UserDetails userDetails = appUserDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtService.generateAccessToken(userDetails);
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user, servletRequest);
        auditService.logSensitiveAction("MFA_ENABLED", user.getUsername(), user.getUsername(), "Account MFA enrollment completed");

        return ResponseEntity.ok(AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken.plainToken())
            .tokenType("Bearer")
            .expiresInSeconds(jwtService.getAccessExpirationSeconds())
            .refreshExpiresInSeconds(refreshToken.expiresInSeconds())
            .username(user.getUsername())
            .role("ROLE_" + user.getRole())
            .message("Multi-factor authentication enabled.")
            .build());
    }

    private AppUser currentUser(Authentication authentication) {
        String username = resolveUsername(authentication);
        return appUserRepository.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private static String resolveUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
        return authentication.getName();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private static AccountProfileResponse toProfile(AppUser user) {
        return new AccountProfileResponse(
            user.getUsername(),
            "ROLE_" + user.getRole(),
            user.isMfaEnabled(),
            user.getPrivacyNoticeVersion(),
            user.getPrivacyNoticeAcceptedAt(),
            user.getDeletionRequestedAt()
        );
    }
}
