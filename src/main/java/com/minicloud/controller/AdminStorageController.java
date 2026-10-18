
package com.minicloud.controller;

import com.minicloud.dto.CloudStorageStats;
import com.minicloud.dto.UserStorageStats;
import com.minicloud.service.StorageStatsService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {

    private final StorageStatsService storageStatsService;

    public AdminStorageController(
            StorageStatsService storageStatsService) {
        this.storageStatsService = storageStatsService;
    }

    @GetMapping("/summary")
    public CloudStorageStats getCloudStorageStats() {
        return storageStatsService.getCloudStorageStats();
    }

    @GetMapping("/users")
    public List<UserStorageStats> getUserStorageStats() {
        return storageStatsService.getUserStorageStats();
    }
}
