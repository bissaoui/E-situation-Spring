package com.example.situation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresInSeconds;
    private Long refreshExpiresInSeconds;
    private String username;
    private String role;
    private boolean mfaRequired;
    private boolean mfaSetupRequired;
    private String message;
}
