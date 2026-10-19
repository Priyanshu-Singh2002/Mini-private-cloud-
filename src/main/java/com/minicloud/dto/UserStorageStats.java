package com.minicloud.dto;

public record UserStorageStats(
        Long userId,
        String username,
        String email,
        String role,
        String status,
        long fileCount,
        long usedBytes,
        long storageQuotaBytes,
        long remainingBytes,
        double usagePercent
) {
}
