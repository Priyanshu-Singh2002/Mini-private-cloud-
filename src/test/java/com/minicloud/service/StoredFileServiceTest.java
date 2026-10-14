package com.minicloud.service;

import com.minicloud.entity.StoredFile;
import com.minicloud.repository.StoredFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private StoredFileService storedFileService;

    private StoredFile file;

    @BeforeEach
    void setUp() {
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
}