
package com.minicloud.entity;

import com.minicloud.storage.StorageQuotaPolicy;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(
            name = "storage_quota_bytes",
            nullable = false,
            columnDefinition = "BIGINT NOT NULL DEFAULT 104857600")
    private Long storageQuotaBytes = StorageQuotaPolicy.DEFAULT_QUOTA_BYTES;

    private LocalDateTime createdAt;

    public AppUser() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null || role.isBlank()) {
            role = "USER";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (storageQuotaBytes == null) {
            storageQuotaBytes = StorageQuotaPolicy.DEFAULT_QUOTA_BYTES;
        }
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void assignAdminRole() {
        this.role = "ADMIN";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new IllegalArgumentException(
                    "Status must be ACTIVE or SUSPENDED.");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public void setStorageQuotaBytes(Long storageQuotaBytes) {
        this.storageQuotaBytes = storageQuotaBytes;
    }
}
