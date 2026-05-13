package com.example.situation.service;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginSecurityService {

    private final AppUserRepository appUserRepository;
    private final int maxFailedAttempts;
    private final long baseLockMinutes;

    public LoginSecurityService(
        AppUserRepository appUserRepository,
        @Value("${security.auth.max-failed-attempts:5}") int maxFailedAttempts,
        @Value("${security.auth.lock-minutes:15}") long baseLockMinutes
    ) {
        this.appUserRepository = appUserRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.baseLockMinutes = baseLockMinutes;
    }

    public Optional<AppUser> findUser(String username) {
        String normalized = username == null ? "" : username.trim();
        return appUserRepository.findByUsernameIgnoreCase(normalized);
    }

    public Duration remainingLockDuration(AppUser user) {
        if (user == null || user.getLockedUntil() == null || !user.getLockedUntil().isAfter(Instant.now())) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), user.getLockedUntil());
    }

    public boolean isSensitiveRole(AppUser user) {
        return user != null && user.getRole() != null && !"USER".equalsIgnoreCase(user.getRole());
    }

    @Transactional
    public void recordFailedAttempt(String username) {
        findUser(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                long multiplier = 1L << Math.min(attempts - maxFailedAttempts, 4);
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(baseLockMinutes * multiplier)));
            }
            appUserRepository.save(user);
        });
    }

    @Transactional
    public void recordSuccessfulLogin(AppUser user) {
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);
    }
}
