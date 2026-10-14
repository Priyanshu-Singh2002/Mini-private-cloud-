
package com.minicloud.dto;

public record LoginResponse(
        Long id,
        String username,
        String email,
        String role,
        String token
) {
}
