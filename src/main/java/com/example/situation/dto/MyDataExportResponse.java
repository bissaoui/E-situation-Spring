package com.example.situation.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyDataExportResponse {
    private Instant generatedAt;
    private AccountProfileResponse account;
    private String projetScope;
    private long accessibleSituationCount;
}
