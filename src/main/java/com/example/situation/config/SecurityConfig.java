package com.example.situation.config;

import com.example.situation.security.ApiRateLimitFilter;
import com.example.situation.security.JwtAuthenticationFilter;
import com.example.situation.service.AppUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ApiRateLimitFilter apiRateLimitFilter,
        JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        RequestMatcher apiMatcher = new AntPathRequestMatcher("/api/**");

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                apiMatcher,
                new AntPathRequestMatcher("/v3/api-docs/**"),
                new AntPathRequestMatcher("/swagger-ui/**")
            ))
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED),
                apiMatcher
            ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/css/**", "/login", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                .requestMatchers("/api/users/**", "/users/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/situations", true)
                .permitAll()
            )
            .logout(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
        AppUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
        throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://172.16.2.14:*,http://172.16.2.35:*,http://172.16.2.40:*}") List<String> allowedOriginPatterns
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "Content-Length", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
