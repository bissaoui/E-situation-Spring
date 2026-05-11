package com.example.situation.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.situation.controller.AuthApiController;
import com.example.situation.security.InputSanitizer;
import com.example.situation.security.JwtService;
import com.example.situation.service.AppUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthApiController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*,http://172.16.2.14:*",
    "security.jwt.secret=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWZvci1KV1Qtc2lnbmluZy0xMjM0NTY3ODkw"
})
class SecurityConfigWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private InputSanitizer inputSanitizer;

    @BeforeEach
    void setUp() {
        when(inputSanitizer.sanitize(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void optionsRequestForApiLoginIncludesCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://172.16.2.14:8082")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://172.16.2.14:8082"))
            .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
            .andExpect(header().string("Access-Control-Expose-Headers", "Authorization, Content-Disposition, Content-Length, Content-Type"));
    }

    @Test
    void postApiLoginReturnsJsonInsteadOfRedirectingToLoginPage() throws Exception {
        UserDetails user = User.withUsername("dg").password("ignored").roles("DG").build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            user,
            null,
            user.getAuthorities()
        );

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateToken(user)).thenReturn("test-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(7200L);

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Origin", "http://172.16.2.14:8082")
                .content("""
                    {"username":"dg","password":"dg123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accessToken").value("test-jwt"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresInSeconds").value(7200))
            .andExpect(jsonPath("$.username").value("dg"))
            .andExpect(jsonPath("$.role").value("ROLE_DG"));
    }
}
