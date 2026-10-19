package com.minicloud.controller;

import com.minicloud.dto.StorageUsageResponse;
import com.minicloud.service.StorageQuotaService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageQuotaService storageQuotaService;

    public StorageController(StorageQuotaService storageQuotaService) {
        this.storageQuotaService = storageQuotaService;
    }

    @GetMapping("/me")
    public StorageUsageResponse getMyStorageUsage(
            @AuthenticationPrincipal Jwt jwt) {
        return storageQuotaService.getUsage(getUserId(jwt));
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException(
                    "Authenticated JWT is required.");
        }

        Object userIdClaim = jwt.getClaim("userId");

        if (!(userIdClaim instanceof Number number)) {
            throw new IllegalStateException(
                    "JWT does not contain a valid userId.");
        }

        return number.longValue();
    }
}
