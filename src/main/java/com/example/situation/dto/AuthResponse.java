package com.example.situation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private long expiresInSeconds;
    private String username;
    private String role;
}
