package com.example.situation.controller;

import com.example.situation.dto.AuthRequest;
import com.example.situation.dto.AuthResponse;
import com.example.situation.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthApiController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            UserDetails user = (UserDetails) auth.getPrincipal();
            String token = jwtService.generateToken(user);
            String role = user.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_USER");
            return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), user.getUsername(), role);
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }
}
