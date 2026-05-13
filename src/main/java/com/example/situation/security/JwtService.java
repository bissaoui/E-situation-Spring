package com.example.situation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final long accessExpirationSeconds;
    private final long mfaPendingExpirationSeconds;

    public JwtService(
        @Value("${security.jwt.secret:}") String secret,
        @Value("${security.jwt.expiration-seconds:900}") long accessExpirationSeconds,
        @Value("${security.jwt.mfa-pending-expiration-seconds:300}") long mfaPendingExpirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(resolveSecret(secret)));
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.mfaPendingExpirationSeconds = mfaPendingExpirationSeconds;
    }

    public String generateAccessToken(UserDetails userDetails) {
        return generateToken(userDetails, accessExpirationSeconds, false);
    }

    @Deprecated
    public String generateToken(UserDetails userDetails) {
        return generateAccessToken(userDetails);
    }

    public String generateMfaPendingToken(UserDetails userDetails) {
        return generateToken(userDetails, mfaPendingExpirationSeconds, true);
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();
        String tokenType = claims.get("token_type", String.class);
        return username.equals(userDetails.getUsername())
            && expiration.after(new Date())
            && TOKEN_TYPE_ACCESS.equals(tokenType);
    }

    public boolean isMfaPending(Claims claims) {
        Boolean value = claims.get("mfa_pending", Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    @Deprecated
    public long getExpirationSeconds() {
        return getAccessExpirationSeconds();
    }

    public long getMfaPendingExpirationSeconds() {
        return mfaPendingExpirationSeconds;
    }

    private String generateToken(UserDetails userDetails, long expirationSeconds, boolean mfaPending) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .subject(userDetails.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .id(UUID.randomUUID().toString())
            .claims(Map.of(
                "roles", userDetails.getAuthorities().stream().map(Object::toString).toList(),
                "token_type", TOKEN_TYPE_ACCESS,
                "mfa_pending", mfaPending
            ))
            .signWith(signingKey)
            .compact();
    }

    private static String resolveSecret(String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return configuredSecret.trim();
        }
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        String generated = Base64.getEncoder().encodeToString(random);
        log.warn("JWT_SECRET not set. Generated ephemeral JWT secret for this runtime.");
        return generated;
    }
}
