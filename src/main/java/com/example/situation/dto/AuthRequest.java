package com.example.situation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuthRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username contains invalid characters")
    private String username;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @Pattern(regexp = "^$|^\\d{6}$", message = "OTP code must contain exactly 6 digits")
    private String otpCode = "";
}
