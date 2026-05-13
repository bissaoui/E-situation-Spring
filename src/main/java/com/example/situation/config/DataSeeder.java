package com.example.situation.config;

import com.example.situation.model.AppUser;
import com.example.situation.repository.AppUserRepository;
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
        @Value("${app.seed.ddzo-password:}") String ddzoPassword,
        @Value("${app.seed.require-configured-passwords:true}") boolean requireConfiguredPasswords
    ) {
        return args -> {
            seedUser(appUserRepository, passwordEncoder, "admin", "ADMIN", adminPassword, requireConfiguredPasswords);
            seedUser(appUserRepository, passwordEncoder, "user", "USER", userPassword, requireConfiguredPasswords);
            seedUser(appUserRepository, passwordEncoder, "DG", "DG", dgPassword, requireConfiguredPasswords);
            seedUser(appUserRepository, passwordEncoder, "DAF", "DAF", dafPassword, requireConfiguredPasswords);
            seedUser(appUserRepository, passwordEncoder, "DDZA", "DDZA", ddzaPassword, requireConfiguredPasswords);
            seedUser(appUserRepository, passwordEncoder, "DDZO", "DDZO", ddzoPassword, requireConfiguredPasswords);
        };
    }

    private static void seedUser(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        String username,
        String role,
        String configuredPassword,
        boolean requireConfiguredPasswords
    ) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(resolvePassword(configuredPassword, username, requireConfiguredPasswords)));
        user.setRole(role);
        appUserRepository.save(user);
    }

    private static String resolvePassword(String configured, String username, boolean requireConfiguredPasswords) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (requireConfiguredPasswords) {
            throw new IllegalStateException("Seed password is required for user '" + username + "'.");
        }
        log.warn("No seeded password configured for '{}'. Falling back to a development-only placeholder password.", username);
        return "ChangeMeNow!123";
    }
}
