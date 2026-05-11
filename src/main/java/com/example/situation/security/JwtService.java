package com.example.situation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(
        @Value("${security.jwt.secret:}") String secret,
        @Value("${security.jwt.expiration-seconds:7200}") long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(resolveSecret(secret)));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .subject(userDetails.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("roles", userDetails.getAuthorities().stream().map(Object::toString).toList())
            .signWith(signingKey)
            .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        Date expiration = parseClaims(token).getExpiration();
        return username.equals(userDetails.getUsername()) && expiration.after(new Date());
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
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
