package com.example.situation.service;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();
        AppUser appUser = appUserRepository.findByUsernameIgnoreCase(normalizedUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.withUsername(appUser.getUsername())
            .password(appUser.getPassword())
            .roles(appUser.getRole())
            .build();
    }
}
