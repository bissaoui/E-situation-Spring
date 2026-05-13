package com.example.situation.model;

import com.example.situation.compliance.DataClassification;
import com.example.situation.compliance.DataClassificationLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@DataClassification(DataClassificationLevel.CONFIDENTIEL)
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username contains invalid characters")
    @Column(unique = true, nullable = false)
    private String username;

    @Size(max = 120)
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotBlank
    @Pattern(
        regexp = "^(ADMIN|USER|DG|DAF|DDZA|DDZO)$",
        message = "Role must be one of: ADMIN, USER, DG, DAF, DDZA, DDZO"
    )
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private Instant lockedUntil;

    @Column(nullable = false)
    private boolean mfaEnabled = false;

    @Column(length = 128)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String mfaSecret;

    @Column(length = 40)
    private String privacyNoticeVersion;

    private Instant privacyNoticeAcceptedAt;

    private Instant deletionRequestedAt;

    @Column(length = 500)
    private String deletionRequestReason;
}
