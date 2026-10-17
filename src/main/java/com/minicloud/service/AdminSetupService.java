
package com.minicloud.service;

import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSetupService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean bootstrapEnabled;
    private final String setupSecret;

    public AdminSetupService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap-enabled:false}") boolean bootstrapEnabled,
            @Value("${app.admin.setup-secret:}") String setupSecret) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEnabled = bootstrapEnabled;
        this.setupSecret = setupSecret;
    }

    @Transactional
    public RegisterResponse createFirstAdmin(
            RegisterRequest request, String suppliedSecret) {

        if (!bootstrapEnabled || setupSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Admin bootstrap is disabled.");
        }

        if (suppliedSecret == null || !MessageDigest.isEqual(
                setupSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid admin setup secret.");
        }

        if (userRepository.existsByRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An admin account already exists.");
        }

        if (request == null
                || request.username() == null
                || request.username().isBlank()
                || request.username().length() > 50
                || request.email() == null
                || request.email().isBlank()
                || request.email().length() > 150
                || request.password() == null
                || request.password().length() < 8
                || request.password().length() > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a username, email and a password of 8–72 characters.");
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (username.isBlank() || username.length() > 50
                || email.isBlank() || email.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid username or email.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered.");
        }

        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.assignAdminRole();

        AppUser saved = userRepository.saveAndFlush(admin);

        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRole());
    }
}
