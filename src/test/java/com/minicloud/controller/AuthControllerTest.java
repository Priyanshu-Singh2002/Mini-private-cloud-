
package com.minicloud.controller;

import com.minicloud.dto.LoginResponse;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerShouldReturn201() throws Exception {
        when(authService.register(any()))
                .thenReturn(new RegisterResponse(
                        1L, "sanu", "sanu@example.com", "USER"
                ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "sanu",
                                  "email": "sanu@example.com",
                                  "password": "StrongPass123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("sanu"))
                .andExpect(jsonPath("$.email")
                        .value("sanu@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerShouldReturn400ForInvalidInput() throws Exception {
        when(authService.register(any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid input"
                ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "email": "sanu@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerShouldReturn409ForDuplicateAccount()
            throws Exception {
        when(authService.register(any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Account already exists"
                ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "sanu",
                                  "email": "sanu@example.com",
                                  "password": "StrongPass123"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginShouldReturn200() throws Exception {
        when(authService.login(any()))
                .thenReturn(new LoginResponse(
                        1L, "sanu", "sanu@example.com", "USER","test.jwt.token"
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "sanu",
                                  "password": "StrongPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("sanu"))
                .andExpect(jsonPath("$.email")
                        .value("sanu@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginShouldReturn401ForInvalidCredentials()
            throws Exception {
        when(authService.login(any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "sanu",
                                  "password": "WrongPass123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
