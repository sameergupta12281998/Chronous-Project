package com.airtribe.chronos.identity;

import com.airtribe.chronos.identity.web.LoginRequest;
import com.airtribe.chronos.identity.web.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        RegisterRequest reg = new RegisterRequest("alice", "alice@example.com", "password123");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        LoginRequest login = new LoginRequest("alice", "password123");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void duplicateRegistrationFails() throws Exception {
        RegisterRequest reg = new RegisterRequest("bob", "bob@example.com", "password123");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidLoginRejected() throws Exception {
        LoginRequest login = new LoginRequest("nobody", "wrongpass");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationErrorReturns400() throws Exception {
        RegisterRequest bad = new RegisterRequest("a", "not-an-email", "short");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
