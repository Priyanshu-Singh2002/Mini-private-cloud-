package com.minicloud.repository;

import com.minicloud.entity.AppUser;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(String role);

    Optional<AppUser> findByUsernameOrEmail(
            String username,
            String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE AppUser u
            SET u.storageQuotaBytes = :defaultQuota
            WHERE u.storageQuotaBytes IS NULL
            """)
    int backfillNullStorageQuotas(@Param("defaultQuota") long defaultQuota);
}