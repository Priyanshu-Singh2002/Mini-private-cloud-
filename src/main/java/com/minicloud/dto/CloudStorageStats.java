package com.minicloud.dto;

public record CloudStorageStats(
        long totalUsers,
        long totalFiles,
        long totalStorageBytes,
        long usersWithFiles
) {
}
