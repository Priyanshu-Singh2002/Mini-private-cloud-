package com.minicloud.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path storageDirectory;

    public FileStorageService(
            @Value("${app.storage.location:storage}") String storageLocation) {

        this.storageDirectory = Paths.get(storageLocation)
                .toAbsolutePath()
                .normalize();
    }

    public void initializeStorage() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not initialize file storage.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty file.");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null
                        ? ""
                        : file.getOriginalFilename());

        if (originalFilename.isBlank()
                || originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new IllegalArgumentException(
                    "Invalid filename.");
        }

        String storedFilename = UUID.randomUUID().toString();

        try {
            initializeStorage();

            Path destination = storageDirectory
                    .resolve(storedFilename)
                    .normalize();

            if (!destination.startsWith(storageDirectory)) {
                throw new IllegalArgumentException(
                        "Invalid storage path.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return storedFilename;

        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not store the uploaded file.", ex);
        }
    }

    public Path loadFile(String storedFilename) {
        if (storedFilename == null
                || !storedFilename.matches(
                        "[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException(
                    "Invalid stored filename.");
        }

        Path filePath = storageDirectory
                .resolve(storedFilename)
                .normalize();

        if (!filePath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid storage path.");
        }

        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException(
                    "File not found on disk.");
        }

        return filePath;
    }

    public void deleteFile(String storedFilename) {
        try {
            Files.deleteIfExists(loadFile(storedFilename));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not delete the stored file.", ex);
        }
    }
}