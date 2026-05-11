package com.example.situation.controller;

import com.example.situation.dto.ChangePasswordRequest;
import com.example.situation.service.PasswordManagementService;
import java.beans.PropertyEditorSupport;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AccountController {

    private final PasswordManagementService passwordManagementService;

    public AccountController(PasswordManagementService passwordManagementService) {
        this.passwordManagementService = passwordManagementService;
    }

    @InitBinder("passwordForm")
    public void preservePasswordFields(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "currentPassword", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text);
            }
        });
        binder.registerCustomEditor(String.class, "newPassword", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text);
            }
        });
        binder.registerCustomEditor(String.class, "confirmNewPassword", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text);
            }
        });
    }

    @GetMapping("/account/password")
    public String passwordForm(Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new ChangePasswordRequest());
        }
        return "account-password";
    }

    @PostMapping("/account/password")
    public String changePassword(
        @Valid @ModelAttribute("passwordForm") ChangePasswordRequest passwordForm,
        BindingResult bindingResult,
        Authentication authentication,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "account-password";
        }
        try {
            passwordManagementService.changePassword(resolveUsername(authentication), passwordForm);
            model.addAttribute("passwordForm", new ChangePasswordRequest());
            model.addAttribute("successMessage", "Password updated successfully.");
            return "account-password";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("passwordChangeError", ex.getMessage());
            return "account-password";
        }
    }

    private static String resolveUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
        return authentication.getName();
    }
}
