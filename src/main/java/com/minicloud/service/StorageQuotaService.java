package com.minicloud.service;

import com.minicloud.dto.StorageUsageResponse;
import com.minicloud.dto.UpdateStorageQuotaRequest;
import com.minicloud.dto.UserStorageStats;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.StoredFileRepository;
import com.minicloud.storage.StorageQuotaPolicy;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageQuotaService {

    private final AppUserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final ConcurrentHashMap<Long, Object> uploadLocks =
            new ConcurrentHashMap<>();

    public StorageQuotaService(
            AppUserRepository userRepository,
            StoredFileRepository storedFileRepository) {
        this.userRepository = userRepository;
        this.storedFileRepository = storedFileRepository;
    }

    public Object lockForUser(Long userId) {
        return uploadLocks.computeIfAbsent(userId, id -> new Object());
    }

    @Transactional
    public void enforceQuotaForUpload(Long userId, long incomingFileSizeBytes) {
        AppUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."));

        long quotaBytes = resolveQuota(user);
        long usedBytes = currentUsageBytes(userId);

        if (incomingFileSizeBytes > Long.MAX_VALUE - usedBytes
                || usedBytes + incomingFileSizeBytes > quotaBytes) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Upload exceeds your storage quota. "
                            + "Used "
                            + usedBytes
                            + " of "
                            + quotaBytes
                            + " bytes.");
        }
    }

    @Transactional(readOnly = true)
    public StorageUsageResponse getUsage(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."));

        return toUsageResponse(user);
    }

    @Transactional
    public UserStorageStats updateQuota(
            Long userId,
            UpdateStorageQuotaRequest request) {

        if (request == null || request.storageQuotaBytes() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storageQuotaBytes is required.");
        }

        long quotaBytes = request.storageQuotaBytes();

        if (quotaBytes < StorageQuotaPolicy.MIN_QUOTA_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Storage quota must be a positive number of bytes.");
        }

        if (quotaBytes > StorageQuotaPolicy.MAX_QUOTA_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Storage quota exceeds the maximum of 1 TiB.");
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."));

        user.setStorageQuotaBytes(quotaBytes);
        return toUserStorageStats(user);
    }

    public UserStorageStats toUserStorageStats(AppUser user) {
        long usedBytes = currentUsageBytes(user.getId());
        long fileCount = storedFileRepository.countByUserId(user.getId());
        long quotaBytes = resolveQuota(user);
        long remainingBytes = Math.max(0L, quotaBytes - usedBytes);

        return new UserStorageStats(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                fileCount,
                usedBytes,
                quotaBytes,
                remainingBytes,
                usagePercent(usedBytes, quotaBytes));
    }

    private StorageUsageResponse toUsageResponse(AppUser user) {
        long usedBytes = currentUsageBytes(user.getId());
        long fileCount = storedFileRepository.countByUserId(user.getId());
        long quotaBytes = resolveQuota(user);
        long remainingBytes = Math.max(0L, quotaBytes - usedBytes);

        return new StorageUsageResponse(
                user.getId(),
                fileCount,
                usedBytes,
                quotaBytes,
                remainingBytes,
                usagePercent(usedBytes, quotaBytes));
    }

    private long currentUsageBytes(Long userId) {
        Long usedBytes =
                storedFileRepository.calculateStorageBytesByUserId(userId);
        return usedBytes == null ? 0L : usedBytes;
    }

    private static long resolveQuota(AppUser user) {
        Long quota = user.getStorageQuotaBytes();
        return quota == null
                ? StorageQuotaPolicy.DEFAULT_QUOTA_BYTES
                : quota;
    }

    static double usagePercent(long usedBytes, long quotaBytes) {
        if (quotaBytes <= 0L) {
            return 100.0;
        }

        double percent = (usedBytes * 100.0) / quotaBytes;
        return Math.round(Math.min(percent, 100.0) * 10.0) / 10.0;
    }
}
