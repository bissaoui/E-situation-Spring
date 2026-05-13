package com.example.situation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MfaStatusResponse {
    private boolean enabled;
    private boolean requiredForRole;
}
