package com.minicloud.service;

import com.minicloud.entity.StoredFile;
import com.minicloud.repository.StoredFileRepository;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final StorageQuotaService storageQuotaService;
    private final StoredFileService self;

    public StoredFileService(
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            StorageQuotaService storageQuotaService,
            @Lazy StoredFileService self) {
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.storageQuotaService = storageQuotaService;
        this.self = self == null ? this : self;
    }

    public StoredFile uploadFile(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required.");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty file.");
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(
                    "Filename is required.");
        }

        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        synchronized (storageQuotaService.lockForUser(userId)) {
            return self.storeWithinQuota(
                    userId,
                    file,
                    originalFilename,
                    contentType);
        }
    }

    @Transactional
    public StoredFile storeWithinQuota(
            Long userId,
            MultipartFile file,
            String originalFilename,
            String contentType) {

        storageQuotaService.enforceQuotaForUpload(userId, file.getSize());

        String storedFilename = fileStorageService.storeFile(file);

        try {
            StoredFile storedFile = new StoredFile();
            storedFile.setUserId(userId);
            storedFile.setOriginalFilename(
                    Path.of(originalFilename).getFileName().toString());
            storedFile.setStoredFilename(storedFilename);
            storedFile.setContentType(contentType);
            storedFile.setFileSize(file.getSize());

            return storedFileRepository.save(storedFile);

        } catch (RuntimeException ex) {
            // Avoid leaving an orphaned file if metadata saving fails.
            try {
                fileStorageService.deleteFile(storedFilename);
            } catch (RuntimeException cleanupException) {
                ex.addSuppressed(cleanupException);
            }

            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<StoredFile> getUserFiles(Long userId) {
        return storedFileRepository
                .findAllByUserIdOrderByUploadedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public StoredFile getUserFile(Long userId, Long fileId) {
        return storedFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "File not found."));
    }

    @Transactional
    public void deleteUserFile(Long userId, Long fileId) {
        StoredFile storedFile = getUserFile(userId, fileId);

        // Delete the disk file first. If that fails, retain the metadata.
        fileStorageService.deleteFile(storedFile.getStoredFilename());
        storedFileRepository.delete(storedFile);
    }

    public Path getDownloadPath(Long userId, Long fileId) {
        StoredFile storedFile = getUserFile(userId, fileId);
        return fileStorageService.loadFile(
                storedFile.getStoredFilename());
    }
}