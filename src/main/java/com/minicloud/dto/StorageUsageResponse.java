package com.minicloud.dto;

public record StorageUsageResponse(
        Long userId,
        long fileCount,
        long usedBytes,
        long storageQuotaBytes,
        long remainingBytes,
        double usagePercent
) {
}
