
package com.minicloud.service;

import com.minicloud.dto.CloudStorageStats;
import com.minicloud.dto.UserStorageStats;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.StoredFileRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageStatsService {

    private final AppUserRepository userRepository;
    private final StoredFileRepository storedFileRepository;

    public StorageStatsService(
            AppUserRepository userRepository,
            StoredFileRepository storedFileRepository) {
        this.userRepository = userRepository;
        this.storedFileRepository = storedFileRepository;
    }

    @Transactional(readOnly = true)
    public CloudStorageStats getCloudStorageStats() {
        long totalUsers = userRepository.count();
        long totalFiles = storedFileRepository.countAllStoredFiles();

        Long totalBytes =
                storedFileRepository.calculateTotalStorageBytes();

        long usersWithFiles =
                storedFileRepository.calculateStorageByUser().size();

        return new CloudStorageStats(
                totalUsers,
                totalFiles,
                totalBytes == null ? 0L : totalBytes,
                usersWithFiles
        );
    }

    @Transactional(readOnly = true)
    public List<UserStorageStats> getUserStorageStats() {
        List<AppUser> users = userRepository.findAll();

        Map<Long, Object[]> storageByUser = new HashMap<>();

        for (Object[] row : storedFileRepository.calculateStorageByUser()) {
            Long userId = ((Number) row[0]).longValue();
            storageByUser.put(userId, row);
        }

        List<UserStorageStats> result = new ArrayList<>();

        for (AppUser user : users) {
            Object[] row = storageByUser.get(user.getId());

            long fileCount = row == null
                    ? 0L
                    : ((Number) row[1]).longValue();

            long usedBytes = row == null
                    ? 0L
                    : ((Number) row[2]).longValue();

            result.add(new UserStorageStats(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    user.getStatus(),
                    fileCount,
                    usedBytes
            ));
        }

        return result;
    }
}
