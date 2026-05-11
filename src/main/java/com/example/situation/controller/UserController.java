package com.example.situation.controller;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import com.example.situation.security.ModelSanitizer;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelSanitizer modelSanitizer;

    public UserController(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        ModelSanitizer modelSanitizer
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.modelSanitizer = modelSanitizer;
    }

    @GetMapping("/users")
    public String list(Model model) {
        model.addAttribute("users", appUserRepository.findAll());
        return "users";
    }

    @GetMapping("/users/new")
    public String newForm(Model model) {
        AppUser user = new AppUser();
        user.setRole("USER");
        model.addAttribute("userForm", user);
        model.addAttribute("isEdit", false);
        return "user-form";
    }

    @PostMapping("/users")
    public String create(@Valid @ModelAttribute("userForm") AppUser userForm, BindingResult bindingResult, Model model) {
        modelSanitizer.sanitizeUser(userForm);
        if (userForm.getPassword() == null || userForm.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "required", "Password is required");
        }
        if (appUserRepository.existsByUsername(userForm.getUsername())) {
            bindingResult.rejectValue("username", "duplicate", "Username already exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "user-form";
        }
        userForm.setPassword(passwordEncoder.encode(userForm.getPassword()));
        appUserRepository.save(userForm);
        return "redirect:/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser user = appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid user id: " + id));
        user.setPassword("");
        model.addAttribute("userForm", user);
        model.addAttribute("isEdit", true);
        return "user-form";
    }

    @PostMapping("/users/{id}")
    public String update(
        @PathVariable Long id,
        @Valid @ModelAttribute("userForm") AppUser userForm,
        BindingResult bindingResult,
        Model model
    ) {
        modelSanitizer.sanitizeUser(userForm);
        AppUser existing = appUserRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid user id: " + id));

        if (!existing.getUsername().equals(userForm.getUsername())
            && appUserRepository.existsByUsername(userForm.getUsername())) {
            bindingResult.rejectValue("username", "duplicate", "Username already exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "user-form";
        }

        existing.setUsername(userForm.getUsername());
        existing.setRole(userForm.getRole());
        if (userForm.getPassword() != null && !userForm.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(userForm.getPassword()));
        }
        appUserRepository.save(existing);
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable Long id) {
        appUserRepository.deleteById(id);
        return "redirect:/users";
    }
}
