package com.example.situation.config;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedUsers(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.seed.admin-password:}") String adminPassword,
        @Value("${app.seed.user-password:}") String userPassword,
        @Value("${app.seed.dg-password:}") String dgPassword,
        @Value("${app.seed.daf-password:}") String dafPassword,
        @Value("${app.seed.ddza-password:}") String ddzaPassword,
        @Value("${app.seed.ddzo-password:}") String ddzoPassword
    ) {
        return args -> {
            seedUser(appUserRepository, passwordEncoder, "admin", "ADMIN", adminPassword);
            seedUser(appUserRepository, passwordEncoder, "user", "USER", userPassword);
            seedUser(appUserRepository, passwordEncoder, "DG", "DG", dgPassword);
            seedUser(appUserRepository, passwordEncoder, "DAF", "DAF", dafPassword);
            seedUser(appUserRepository, passwordEncoder, "DDZA", "DDZA", ddzaPassword);
            seedUser(appUserRepository, passwordEncoder, "DDZO", "DDZO", ddzoPassword);
        };
    }

    private static void seedUser(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        String username,
        String role,
        String configuredPassword
    ) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(resolvePassword(configuredPassword, username)));
        user.setRole(role);
        appUserRepository.save(user);
    }

    private static String resolvePassword(String configured, String username) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("No seeded password configured for '{}'. Generated temporary password: {}", username, generated);
        return generated;
    }
}
