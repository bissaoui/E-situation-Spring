package com.example.situation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank
    @Size(max = 120)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 120)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 120)
    private String confirmNewPassword;
}
