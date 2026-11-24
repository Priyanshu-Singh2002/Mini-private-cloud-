package com.minicloud.service;

import com.minicloud.entity.AppUser;
import com.minicloud.entity.StoredFile;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.StoredFileRepository;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final StorageQuotaService storageQuotaService;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final StoredFileService self;

    public StoredFileService(
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            StorageQuotaService storageQuotaService,
            AppUserRepository userRepository,
            AuditLogService auditLogService,
            @Lazy StoredFileService self) {
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.storageQuotaService = storageQuotaService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.self = self == null ? this : self;
    }

    public StoredFile uploadFile(Long userId, MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();

        try {
            if (userId == null) {
                throw new IllegalArgumentException(
                        "Authenticated user is required.");
            }

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(
                        "Please select a non-empty file.");
            }

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
        } catch (RuntimeException ex) {
            auditFile(
                    userId,
                    null,
                    fileName(originalFilename),
                    "FILE_UPLOADED",
                    "File upload failed. " + safeReason(ex),
                    AuditLogService.FAILURE);
            throw ex;
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

            StoredFile saved = storedFileRepository.save(storedFile);
            auditFile(
                    userId,
                    saved.getId(),
                    saved.getOriginalFilename(),
                    "FILE_UPLOADED",
                    "Uploaded file " + saved.getOriginalFilename() + ".",
                    AuditLogService.SUCCESS);
            return saved;

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
        try {
            StoredFile storedFile = getUserFile(userId, fileId);

            // Delete the disk file first. If that fails, retain the metadata.
            fileStorageService.deleteFile(storedFile.getStoredFilename());
            storedFileRepository.delete(storedFile);
            auditFile(
                    userId,
                    storedFile.getId(),
                    storedFile.getOriginalFilename(),
                    "FILE_DELETED",
                    "Deleted file " + storedFile.getOriginalFilename() + ".",
                    AuditLogService.SUCCESS);
        } catch (RuntimeException ex) {
            auditFile(
                    userId,
                    fileId,
                    null,
                    "FILE_DELETED",
                    "File delete failed. " + safeReason(ex),
                    AuditLogService.FAILURE);
            throw ex;
        }
    }

    public Path getDownloadPath(Long userId, Long fileId) {
        StoredFile storedFile = getUserFile(userId, fileId);
        return fileStorageService.loadFile(
                storedFile.getStoredFilename());
    }

    public StoredFile openDownload(Long userId, Long fileId) {
        try {
            StoredFile storedFile = getUserFile(userId, fileId);
            fileStorageService.loadFile(storedFile.getStoredFilename());
            auditFile(
                    userId,
                    storedFile.getId(),
                    storedFile.getOriginalFilename(),
                    "FILE_DOWNLOADED",
                    "Downloaded file " + storedFile.getOriginalFilename() + ".",
                    AuditLogService.SUCCESS);
            return storedFile;
        } catch (RuntimeException ex) {
            auditFile(
                    userId,
                    fileId,
                    null,
                    "FILE_DOWNLOADED",
                    "File download failed. " + safeReason(ex),
                    AuditLogService.FAILURE);
            throw ex;
        }
    }

    private void auditFile(
            Long userId,
            Long fileId,
            String filename,
            String action,
            String description,
            String outcome) {
        AppUser actor = findUser(userId);
        String detail = filename == null || filename.isBlank()
                ? description
                : description;
        auditLogService.record(
                actor == null ? userId : actor.getId(),
                actor == null ? null : actor.getUsername(),
                actor == null ? null : actor.getUniqueUserId(),
                action,
                "FILE",
                fileId,
                null,
                detail,
                outcome);
    }

    private AppUser findUser(Long userId) {
        if (userId == null || userRepository == null) {
            return null;
        }

        Optional<AppUser> user = userRepository.findById(userId);
        if (user == null || user.isEmpty()) {
            return null;
        }

        return user.get();
    }

    private static String fileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }

        return Path.of(originalFilename).getFileName().toString();
    }

    private static String safeReason(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "The operation could not be completed.";
        }

        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}