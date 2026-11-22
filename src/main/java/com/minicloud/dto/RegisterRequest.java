package com.minicloud.dto;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String fullName,
        String phoneNumber,
        String organization,
        String accessReason
) {
    // Preserve compatibility with the existing admin bootstrap flow.
    public RegisterRequest(
            String username,
            String email,
            String password
    ) {
        this(username, email, password, null, null, null, null);
    }
}