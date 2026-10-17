
package com.minicloud.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        LocalDateTime createdAt
) {
}
