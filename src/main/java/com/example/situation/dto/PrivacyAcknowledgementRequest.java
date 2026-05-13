package com.example.situation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PrivacyAcknowledgementRequest {

    @NotBlank
    @Size(max = 40)
    private String version;
}
