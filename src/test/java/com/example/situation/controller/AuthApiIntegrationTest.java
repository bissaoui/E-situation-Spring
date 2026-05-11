package com.example.situation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.batch.be-watch.enabled=false")
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*,http://172.16.2.14:*")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturnsUnauthorizedWithoutRedirectingToHtmlLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Origin", "http://172.16.2.14:8082")
                .content("""
                    {"username":"dg","password":"wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://172.16.2.14:8082"))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(content().string(""));
    }

    @Test
    void unauthenticatedApiRequestReturnsUnauthorizedWithoutRedirectingToHtmlLogin() throws Exception {
        mockMvc.perform(get("/api/situations")
                .header("Origin", "http://172.16.2.14:8082"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://172.16.2.14:8082"))
            .andExpect(header().doesNotExist("Location"));
    }
}
