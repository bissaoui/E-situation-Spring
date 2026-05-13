package com.example.situation.controller;

import com.example.situation.dto.AuthRequest;
import com.example.situation.dto.AuthResponse;
import com.example.situation.dto.LogoutRequest;
import com.example.situation.dto.RefreshTokenRequest;
import com.example.situation.model.AppUser;
import com.example.situation.security.JwtService;
import com.example.situation.security.TotpService;
import com.example.situation.service.AppUserDetailsService;
import com.example.situation.service.AuditService;
import com.example.situation.service.LoginSecurityService;
import com.example.situation.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginSecurityService loginSecurityService;
    private final AppUserDetailsService appUserDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final AuditService auditService;

    public AuthApiController(
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        LoginSecurityService loginSecurityService,
        AppUserDetailsService appUserDetailsService,
        RefreshTokenService refreshTokenService,
        TotpService totpService,
        AuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginSecurityService = loginSecurityService;
        this.appUserDetailsService = appUserDetailsService;
        this.refreshTokenService = refreshTokenService;
        this.totpService = totpService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest servletRequest) {
        AppUser appUser = loginSecurityService.findUser(request.getUsername()).orElse(null);
        Duration remainingLock = loginSecurityService.remainingLockDuration(appUser);
        if (!remainingLock.isZero()) {
            auditService.logAuthFailure(request.getUsername(), clientIp(servletRequest), userAgent(servletRequest), "ACCOUNT_LOCKED");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(Math.max(1, remainingLock.toSeconds())))
                .body(AuthResponse.builder()
                    .message("Too many requests. Please wait before trying again.")
                    .build());
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            UserDetails user = (UserDetails) auth.getPrincipal();
            AppUser currentUser = appUser != null ? appUser
                : loginSecurityService.findUser(user.getUsername())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
            loginSecurityService.recordSuccessfulLogin(currentUser);

            if (loginSecurityService.isSensitiveRole(currentUser)) {
                if (!currentUser.isMfaEnabled()) {
                    auditService.logAuthFailure(currentUser.getUsername(), clientIp(servletRequest), userAgent(servletRequest), "MFA_SETUP_REQUIRED");
                    return ResponseEntity.ok(AuthResponse.builder()
                        .accessToken(jwtService.generateMfaPendingToken(user))
                        .tokenType("Bearer")
                        .expiresInSeconds(jwtService.getMfaPendingExpirationSeconds())
                        .username(currentUser.getUsername())
                        .role("ROLE_" + currentUser.getRole())
                        .mfaSetupRequired(true)
                        .message("Multi-factor authentication setup is required.")
                        .build());
                }
                if (!totpService.verifyCode(currentUser.getMfaSecret(), request.getOtpCode())) {
                    auditService.logAuthFailure(currentUser.getUsername(), clientIp(servletRequest), userAgent(servletRequest), "MFA_REQUIRED");
                    return ResponseEntity.ok(AuthResponse.builder()
                        .username(currentUser.getUsername())
                        .role("ROLE_" + currentUser.getRole())
                        .mfaRequired(true)
                        .message("Multi-factor authentication code required.")
                        .build());
                }
            }

            String accessToken = jwtService.generateAccessToken(user);
            RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(currentUser, servletRequest);
            auditService.logAuthSuccess(currentUser.getUsername(), currentUser.getRole(), clientIp(servletRequest), userAgent(servletRequest));

            return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.plainToken())
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getAccessExpirationSeconds())
                .refreshExpiresInSeconds(refreshToken.expiresInSeconds())
                .username(currentUser.getUsername())
                .role("ROLE_" + currentUser.getRole())
                .message("Authenticated.")
                .build());
        } catch (AuthenticationException ex) {
            loginSecurityService.recordFailedAttempt(request.getUsername());
            auditService.logAuthFailure(request.getUsername(), clientIp(servletRequest), userAgent(servletRequest), "INVALID_CREDENTIALS");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        try {
            AppUser user = refreshTokenService.resolveActiveUser(request.getRefreshToken());
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(user.getUsername());
            RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(request.getRefreshToken(), servletRequest);
            String accessToken = jwtService.generateAccessToken(userDetails);
            auditService.logRefresh(user.getUsername(), clientIp(servletRequest), userAgent(servletRequest));
            return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.plainToken())
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getAccessExpirationSeconds())
                .refreshExpiresInSeconds(rotated.expiresInSeconds())
                .username(user.getUsername())
                .role("ROLE_" + user.getRole())
                .message("Session refreshed.")
                .build());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest servletRequest) {
        try {
            AppUser user = refreshTokenService.resolveActiveUser(request.getRefreshToken());
            refreshTokenService.revoke(request.getRefreshToken());
            auditService.logLogout(user.getUsername(), clientIp(servletRequest), userAgent(servletRequest));
        } catch (IllegalArgumentException ex) {
            // Respond generically to avoid leaking token state.
        }
        return Map.of("message", "Logged out.");
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
}
