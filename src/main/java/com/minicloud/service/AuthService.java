
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
    private final AuditLogService auditLogService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
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

            failRegistration(
                    request,
                    "Enter a username, email and a password of 8-72 characters.");
        }

        if (request.fullName() == null || request.fullName().isBlank()
                || request.phoneNumber() == null || request.phoneNumber().isBlank()
                || request.organization() == null || request.organization().isBlank()
                || request.accessReason() == null || request.accessReason().isBlank()) {

            failRegistration(
                    request,
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

            failRegistration(
                    request,
                    "One or more registration fields exceed the allowed length.");
        }

        if (userRepository.existsByUsername(username)) {
            failRegistration(request, "Username already exists.");
        }

        if (userRepository.existsByEmail(email)) {
            failRegistration(request, "Email already registered.");
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

        auditLogService.record(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getUniqueUserId(),
                "USER_REGISTERED",
                "USER",
                savedUser.getId(),
                savedUser.getUniqueUserId(),
                "User " + savedUser.getUsername()
                        + " registered and is awaiting administrator approval.",
                AuditLogService.SUCCESS);

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

            recordLoginFailure(
                    null,
                    safeAccountIdentifier(request),
                    "Login failed because the Unique User ID or password was missing.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unique User ID and password are required.");
        }

        String uniqueUserId = request.uniqueUserId().trim().toUpperCase(Locale.ROOT);

        AppUser user = userRepository.findByUniqueUserId(uniqueUserId)
                .orElse(null);

        if (user == null) {
            recordLoginFailure(
                    null,
                    uniqueUserId,
                    "Login failed because the Unique User ID was not recognized.");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Unique User ID or password.");
        }

        String status = user.getStatus();

        if (!"ACTIVE".equals(status)) {
            recordLoginFailure(
                    user,
                    user.getUniqueUserId(),
                    loginStatusDescription(status));
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

            recordLoginFailure(
                    user,
                    user.getUniqueUserId(),
                    "Login failed because the password was incorrect.");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Unique User ID or password.");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole());

        auditLogService.record(
                user.getId(),
                user.getUsername(),
                user.getUniqueUserId(),
                "LOGIN_SUCCESS",
                "USER",
                user.getId(),
                user.getUniqueUserId(),
                "User " + user.getUsername() + " signed in.",
                AuditLogService.SUCCESS);

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                token);
    }

    @Transactional
    public void logout(Long userId) {
        if (userId == null) {
            auditLogService.record(
                    null,
                    null,
                    null,
                    "LOGOUT",
                    "USER",
                    null,
                    null,
                    "Logout failed because the authenticated user was missing.",
                    AuditLogService.FAILURE);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required.");
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            auditLogService.record(
                    userId,
                    null,
                    null,
                    "LOGOUT",
                    "USER",
                    userId,
                    null,
                    "Logout failed because the account was not found.",
                    AuditLogService.FAILURE);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user was not found.");
        }

        auditLogService.record(
                user.getId(),
                user.getUsername(),
                user.getUniqueUserId(),
                "LOGOUT",
                "USER",
                user.getId(),
                user.getUniqueUserId(),
                "User " + user.getUsername() + " signed out.",
                AuditLogService.SUCCESS);
    }

    private void failRegistration(RegisterRequest request, String reason) {
        String username = null;
        if (request != null && request.username() != null) {
            String trimmed = request.username().trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 50) {
                username = trimmed;
            }
        }

        auditLogService.record(
                null,
                username,
                null,
                "USER_REGISTERED",
                "USER",
                null,
                null,
                "Registration failed. " + reason,
                AuditLogService.FAILURE);
        throw new ResponseStatusException(reason.contains("already")
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST, reason);
    }

    private void recordLoginFailure(
            AppUser user,
            String attemptedIdentifier,
            String description) {
        auditLogService.record(
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                user == null ? attemptedIdentifier : user.getUniqueUserId(),
                "LOGIN_FAILED",
                "USER",
                user == null ? null : user.getId(),
                user == null ? attemptedIdentifier : user.getUniqueUserId(),
                description,
                AuditLogService.FAILURE);
    }

    private static String loginStatusDescription(String status) {
        if ("PENDING".equals(status)) {
            return "Login failed because the account is awaiting administrator approval.";
        }
        if ("REJECTED".equals(status)) {
            return "Login failed because the registration was not approved.";
        }
        if ("SUSPENDED".equals(status)) {
            return "Login failed because the account is suspended.";
        }
        return "Login failed because the account is not active.";
    }

    private static String safeAccountIdentifier(LoginRequest request) {
        if (request == null || request.uniqueUserId() == null) {
            return null;
        }
        String value = request.uniqueUserId().trim();
        return value.isEmpty() ? null : value;
    }
}
