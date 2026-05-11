package com.example.situation.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.situation.dto.ChangePasswordRequest;
import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordManagementService passwordManagementService;

    @Captor
    private ArgumentCaptor<AppUser> userCaptor;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("user1");
        user.setPassword("encoded-old");
        user.setRole("USER");
    }

    @Test
    void changePasswordUpdatesEncodedPasswordWhenInputIsValid() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-pass");
        request.setNewPassword("new-pass-123");
        request.setConfirmNewPassword("new-pass-123");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("new-pass-123", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("encoded-new");

        passwordManagementService.changePassword("user1", request);

        verify(appUserRepository).save(userCaptor.capture());
        AppUser saved = userCaptor.getValue();
        assertEquals("encoded-new", saved.getPassword());
    }

    @Test
    void changePasswordThrowsWhenCurrentPasswordIsIncorrect() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong-old");
        request.setNewPassword("new-pass-123");
        request.setConfirmNewPassword("new-pass-123");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-old", "encoded-old")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> passwordManagementService.changePassword("user1", request));
    }

    @Test
    void changePasswordThrowsWhenConfirmationDoesNotMatch() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-pass");
        request.setNewPassword("new-pass-123");
        request.setConfirmNewPassword("new-pass-124");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> passwordManagementService.changePassword("user1", request));
    }

    @Test
    void changePasswordThrowsWhenNewPasswordEqualsCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-pass");
        request.setNewPassword("old-pass");
        request.setConfirmNewPassword("old-pass");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> passwordManagementService.changePassword("user1", request));
    }
}
