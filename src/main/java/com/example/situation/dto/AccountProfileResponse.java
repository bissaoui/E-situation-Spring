package com.example.situation.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountProfileResponse {
    private String username;
    private String role;
    private boolean mfaEnabled;
    private String privacyNoticeVersion;
    private Instant privacyNoticeAcceptedAt;
    private Instant deletionRequestedAt;
}
