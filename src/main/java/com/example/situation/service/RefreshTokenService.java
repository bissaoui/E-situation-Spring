package com.example.situation.service;

import com.example.situation.model.AppUser;
import com.example.situation.model.RefreshToken;
import com.example.situation.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    public record IssuedToken(String plainToken, long expiresInSeconds) { }

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationSeconds;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        @Value("${security.refresh-token.expiration-seconds:604800}") long refreshExpirationSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    @Transactional
    public IssuedToken issue(AppUser user, HttpServletRequest request) {
        String plainToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(plainToken));
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshExpirationSeconds));
        refreshToken.setCreatedByIp(resolveClientIp(request));
        refreshToken.setUserAgent(resolveUserAgent(request));
        refreshTokenRepository.save(refreshToken);
        return new IssuedToken(plainToken, refreshExpirationSeconds);
    }

    @Transactional
    public IssuedToken rotate(String presentedToken, HttpServletRequest request) {
        RefreshToken existing = findActiveToken(presentedToken);
        existing.setRevokedAt(Instant.now());
        IssuedToken replacement = issue(existing.getUser(), request);
        existing.setReplacedByTokenHash(hashToken(replacement.plainToken()));
        refreshTokenRepository.save(existing);
        return replacement;
    }

    @Transactional
    public AppUser resolveActiveUser(String presentedToken) {
        return findActiveToken(presentedToken).getUser();
    }

    @Transactional
    public void revoke(String presentedToken) {
        RefreshToken token = findActiveToken(presentedToken);
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    @Scheduled(cron = "${security.refresh-token.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now().minusSeconds(86_400));
    }

    private RefreshToken findActiveToken(String presentedToken) {
        String hashed = hashToken(presentedToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashed)
            .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid."));
        if (!token.isActiveAt(Instant.now())) {
            throw new IllegalArgumentException("Refresh token is invalid.");
        }
        return token;
    }

    private String generateOpaqueToken() {
        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(value.length * 2);
            for (byte b : value) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
