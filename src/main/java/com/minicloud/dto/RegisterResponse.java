package com.minicloud.dto;

public record RegisterResponse(
        Long id,
        String username,
        String email,
        String role
) {
}
