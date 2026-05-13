package com.example.situation.model;

import com.example.situation.compliance.DataClassification;
import com.example.situation.compliance.DataClassificationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "refresh_token",
    indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_refresh_token_expires", columnList = "expiresAt")
    }
)
@DataClassification(DataClassificationLevel.CONFIDENTIEL)
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(length = 64)
    private String replacedByTokenHash;

    @Column(length = 128)
    private String createdByIp;

    @Column(length = 255)
    private String userAgent;

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
