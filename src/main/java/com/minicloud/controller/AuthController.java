package com.minicloud.controller;

import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.minicloud.dto.LoginRequest;
import com.minicloud.dto.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
