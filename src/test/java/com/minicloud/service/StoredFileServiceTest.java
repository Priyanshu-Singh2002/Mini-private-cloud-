package com.minicloud.service;

import com.minicloud.entity.StoredFile;
import com.minicloud.repository.StoredFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    private StoredFileService storedFileService;

    private StoredFile file;

    @BeforeEach
    void setUp() {
        storedFileService = new StoredFileService(
                storedFileRepository,
                fileStorageService,
                storageQuotaService,
                null);

        file = new StoredFile();
        file.setUserId(2L);
        file.setOriginalFilename("test.txt");
        file.setStoredFilename(
                "579df9d0-7993-44b2-bf69-18a7a31ceed6");
        file.setContentType("text/plain");
        file.setFileSize(37L);
    }

    @Test
    void userCanAccessOwnFile() {
        when(storedFileRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(file));

        StoredFile result = storedFileService.getUserFile(2L, 1L);

        assertEquals("test.txt", result.getOriginalFilename());
        verify(storedFileRepository).findByIdAndUserId(1L, 2L);
    }

    @Test
    void userCannotAccessAnotherUsersFile() {
        when(storedFileRepository.findByIdAndUserId(1L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> storedFileService.getUserFile(3L, 1L)
        );

        verify(fileStorageService, never()).loadFile(anyString());
    }

    @Test
    void userCannotDownloadAnotherUsersFile() {
        when(storedFileRepository.findByIdAndUserId(1L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> storedFileService.getDownloadPath(3L, 1L)
        );

        verify(fileStorageService, never()).loadFile(anyString());
    }

    @Test
    void userCannotDeleteAnotherUsersFile() {
        when(storedFileRepository.findByIdAndUserId(1L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> storedFileService.deleteUserFile(3L, 1L)
        );

        verify(fileStorageService, never()).deleteFile(anyString());
        verify(storedFileRepository, never()).delete(any(StoredFile.class));
    }

    @Test
    void deletingOwnFileRemovesMetadata() {
        when(storedFileRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(file));

        storedFileService.deleteUserFile(2L, 1L);

        verify(fileStorageService).deleteFile(file.getStoredFilename());
        verify(storedFileRepository).delete(file);
    }

    @Test
    void uploadIsRejectedBeforeStoringWhenQuotaExceeded() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "big.txt",
                "text/plain",
                "hello".getBytes());

        when(storageQuotaService.lockForUser(2L)).thenReturn(new Object());
        doThrow(new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE,
                "Upload exceeds your storage quota."))
                .when(storageQuotaService)
                .enforceQuotaForUpload(2L, upload.getSize());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storedFileService.uploadFile(2L, upload));

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, exception.getStatusCode());
        verify(fileStorageService, never()).storeFile(any());
        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadWithinQuotaStoresFileAndMetadata() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "ok.txt",
                "text/plain",
                "hello".getBytes());

        when(storageQuotaService.lockForUser(2L)).thenReturn(new Object());
        when(fileStorageService.storeFile(upload))
                .thenReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StoredFile saved = storedFileService.uploadFile(2L, upload);

        assertEquals("ok.txt", saved.getOriginalFilename());
        assertEquals(upload.getSize(), saved.getFileSize());
        verify(storageQuotaService).enforceQuotaForUpload(2L, upload.getSize());
        verify(fileStorageService).storeFile(upload);
    }

    @Test
    void failedMetadataSaveCleansUpStoredFile() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "ok.txt",
                "text/plain",
                "hello".getBytes());

        when(storageQuotaService.lockForUser(2L)).thenReturn(new Object());
        when(fileStorageService.storeFile(upload))
                .thenReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(storedFileRepository.save(any(StoredFile.class)))
                .thenThrow(new IllegalStateException("database down"));

        assertThrows(
                IllegalStateException.class,
                () -> storedFileService.uploadFile(2L, upload));

        verify(fileStorageService)
                .deleteFile("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void concurrentUploadsAreSerializedPerUser() throws Exception {
        Object sharedLock = new Object();
        AtomicLong usage = new AtomicLong(0L);
        AtomicInteger storedCount = new AtomicInteger();

        when(storageQuotaService.lockForUser(2L)).thenReturn(sharedLock);
        doAnswer(invocation -> {
            long incoming = invocation.getArgument(1);
            if (usage.get() + incoming > 8L) {
                throw new ResponseStatusException(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "Upload exceeds your storage quota.");
            }
            return null;
        }).when(storageQuotaService).enforceQuotaForUpload(eq(2L), anyLong());

        when(fileStorageService.storeFile(any())).thenReturn(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> {
                    StoredFile stored = invocation.getArgument(0);
                    usage.addAndGet(stored.getFileSize());
                    storedCount.incrementAndGet();
                    return stored;
                });

        MockMultipartFile first = new MockMultipartFile(
                "file", "a.txt", "text/plain", "12345".getBytes());
        MockMultipartFile second = new MockMultipartFile(
                "file", "b.txt", "text/plain", "12345".getBytes());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstResult = executor.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                try {
                    storedFileService.uploadFile(2L, first);
                    return true;
                } catch (ResponseStatusException ex) {
                    return false;
                }
            });
            Future<Boolean> secondResult = executor.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                try {
                    storedFileService.uploadFile(2L, second);
                    return true;
                } catch (ResponseStatusException ex) {
                    return false;
                }
            });

            start.countDown();

            boolean firstOk = firstResult.get(3, TimeUnit.SECONDS);
            boolean secondOk = secondResult.get(3, TimeUnit.SECONDS);

            assertTrue(firstOk ^ secondOk);
            assertEquals(1, storedCount.get());
            assertTrue(usage.get() <= 8L);
        } finally {
            executor.shutdownNow();
        }
    }
}
