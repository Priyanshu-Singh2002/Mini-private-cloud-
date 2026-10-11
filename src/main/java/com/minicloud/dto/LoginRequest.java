package com.minicloud.dto;

public record LoginRequest(
        String usernameOrEmail,
        String password
) {
}
