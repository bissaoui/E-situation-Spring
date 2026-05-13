package com.example.situation.repository;

import com.example.situation.model.AppUser;
import com.example.situation.model.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUser(AppUser user);
    void deleteByExpiresAtBefore(Instant cutoff);
}
