package com.minicloud.dto;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}
