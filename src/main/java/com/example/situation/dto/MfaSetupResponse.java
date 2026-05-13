package com.example.situation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MfaSetupResponse {
    private boolean enabled;
    private boolean requiredForRole;
    private String secret;
    private String otpauthUri;
}
