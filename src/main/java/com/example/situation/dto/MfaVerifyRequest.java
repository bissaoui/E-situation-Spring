package com.example.situation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MfaVerifyRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Code must contain exactly 6 digits")
    private String code;
}
