package com.minicloud.service;

import com.minicloud.dto.StorageUsageResponse;
import com.minicloud.dto.UpdateStorageQuotaRequest;
import com.minicloud.dto.UserStorageStats;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.StoredFileRepository;
import com.minicloud.storage.StorageQuotaPolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StoredFileRepository storedFileRepository;

    @InjectMocks
    private StorageQuotaService storageQuotaService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setUniqueUserId("MC-A7K92P4X");
        user.setStorageQuotaBytes(StorageQuotaPolicy.DEFAULT_QUOTA_BYTES);
    }

    @Test
    void newUsersReceiveDefaultQuota() {
        AppUser created = new AppUser();
        created.prePersist();

        assertEquals(
                StorageQuotaPolicy.DEFAULT_QUOTA_BYTES,
                created.getStorageQuotaBytes());
    }

    @Test
    void existingCustomQuotaIsNotOverwrittenByDefault() {
        user.setStorageQuotaBytes(50L * 1024 * 1024);
        user.prePersist();

        assertEquals(50L * 1024 * 1024, user.getStorageQuotaBytes());
    }

    @Test
    void usageIsCalculatedFromStoredFileMetadata() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(storedFileRepository.calculateStorageBytesByUserId(7L))
                .thenReturn(4096L);
        when(storedFileRepository.countByUserId(7L)).thenReturn(2L);

        StorageUsageResponse usage = storageQuotaService.getUsage(7L);

        assertEquals(4096L, usage.usedBytes());
        assertEquals(StorageQuotaPolicy.DEFAULT_QUOTA_BYTES, usage.storageQuotaBytes());
        assertEquals(
                StorageQuotaPolicy.DEFAULT_QUOTA_BYTES - 4096L,
                usage.remainingBytes());
        assertEquals(2L, usage.fileCount());
        verify(storedFileRepository).calculateStorageBytesByUserId(7L);
    }

    @Test
    void adminCanUpdateQuota() {
        AppUser admin = new AppUser();
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setUsername("admin");
        admin.setUniqueUserId("MC-F850BEE1");

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        when(storedFileRepository.calculateStorageBytesByUserId(7L))
                .thenReturn(0L);
        when(storedFileRepository.countByUserId(7L)).thenReturn(0L);

        long newQuota = 200L * 1024 * 1024;

        UserStorageStats stats = storageQuotaService.updateQuota(
                7L,
                new UpdateStorageQuotaRequest(newQuota),
                1L);

        assertEquals(newQuota, stats.storageQuotaBytes());
        assertEquals(newQuota, user.getStorageQuotaBytes());

        verify(auditLogService).record(
                1L,
                "admin",
                "MC-F850BEE1",
                "USER_QUOTA_UPDATED",
                "USER",
                7L,
                "MC-A7K92P4X",
                "Storage quota for user alice changed from "
                        + StorageQuotaPolicy.DEFAULT_QUOTA_BYTES
                        + " bytes to " + newQuota + " bytes.");
    }

    @Test
    void rejectZeroQuota() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageQuotaService.updateQuota(
                        7L,
                        new UpdateStorageQuotaRequest(0L),
                        1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectNegativeQuota() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                (() -> storageQuotaService.updateQuota(
                        7L,
                        new UpdateStorageQuotaRequest(-10L),
                        1L)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectExcessivelyLargeQuota() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
               () -> storageQuotaService.updateQuota(
        7L,
        new UpdateStorageQuotaRequest(
                StorageQuotaPolicy.MAX_QUOTA_BYTES + 1),
        1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void uploadWithinQuotaIsAllowed() {
        when(userRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(user));
        when(storedFileRepository.calculateStorageBytesByUserId(7L))
                .thenReturn(100L);

        assertDoesNotThrow(
                () -> storageQuotaService.enforceQuotaForUpload(7L, 50L));
    }

    @Test
    void uploadThatExactlyFillsQuotaIsAllowed() {
        user.setStorageQuotaBytes(1000L);
        when(userRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(user));
        when(storedFileRepository.calculateStorageBytesByUserId(7L))
                .thenReturn(600L);

        assertDoesNotThrow(
                () -> storageQuotaService.enforceQuotaForUpload(7L, 400L));
    }

    @Test
    void uploadExceedingQuotaIsRejected() {
        user.setStorageQuotaBytes(1000L);
        when(userRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(user));
        when(storedFileRepository.calculateStorageBytesByUserId(7L))
                .thenReturn(900L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageQuotaService.enforceQuotaForUpload(7L, 101L));

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, exception.getStatusCode());
    }
}
