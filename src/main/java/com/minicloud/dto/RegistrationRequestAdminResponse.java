package com.minicloud.dto;

import java.time.LocalDateTime;

public record RegistrationRequestAdminResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String organization,
        String accessReason,
        String status,
        LocalDateTime createdAt
) {
}
