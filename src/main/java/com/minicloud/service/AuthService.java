
package com.minicloud.service;

import com.minicloud.dto.LoginRequest;
import com.minicloud.dto.LoginResponse;
import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.security.JwtService;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (request == null
                || request.username() == null
                || request.username().isBlank()
                || request.email() == null
                || request.email().isBlank()
                || request.password() == null
                || request.password().length() < 8
                || request.password().length() > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a username, email and a password of 8-72 characters.");
        }

        if (request.fullName() == null || request.fullName().isBlank()
                || request.phoneNumber() == null || request.phoneNumber().isBlank()
                || request.organization() == null || request.organization().isBlank()
                || request.accessReason() == null || request.accessReason().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Full name, phone number, organization and reason for access are required.");
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim();
        String phoneNumber = request.phoneNumber().trim();
        String organization = request.organization().trim();
        String accessReason = request.accessReason().trim();

        if (username.length() > 50
                || email.length() > 150
                || fullName.length() > 100
                || phoneNumber.length() > 30
                || organization.length() > 100
                || accessReason.length() > 1000) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more registration fields exceed the allowed length.");
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

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setOrganization(organization);
        user.setAccessReason(accessReason);

        // New registrations must be approved by an administrator.
        user.setStatus("PENDING");

        AppUser savedUser = userRepository.save(user);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole());
    }

    public LoginResponse login(LoginRequest request) {

        if (request == null
                || request.uniqueUserId() == null
                || request.uniqueUserId().isBlank()
                || request.password() == null
                || request.password().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unique User ID and password are required.");
        }

        String uniqueUserId = request.uniqueUserId().trim().toUpperCase(Locale.ROOT);

        AppUser user = userRepository.findByUniqueUserId(uniqueUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid Unique User ID or password."));

        String status = user.getStatus();

        if (!"ACTIVE".equals(status)) {
            if ("PENDING".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your registration is awaiting administrator approval.");
            }

            if ("REJECTED".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your registration request was not approved.");
            }

            if ("SUSPENDED".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is suspended.");
            }

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is not active.");
        }

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Unique User ID or password.");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole());

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                token);
    }
}
