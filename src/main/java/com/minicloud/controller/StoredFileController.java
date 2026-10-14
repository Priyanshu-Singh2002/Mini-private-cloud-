package com.minicloud.controller;

import com.minicloud.entity.StoredFile;
import com.minicloud.service.StoredFileService;

import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class StoredFileController {

    private final StoredFileService storedFileService;

    public StoredFileController(StoredFileService storedFileService) {
        this.storedFileService = storedFileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<StoredFile> uploadFile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {

        Long userId = getUserId(jwt);

        StoredFile storedFile =
                storedFileService.uploadFile(userId, file);

        return ResponseEntity.ok(storedFile);
    }

    @GetMapping
    public ResponseEntity<List<StoredFile>> getUserFiles(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = getUserId(jwt);

        return ResponseEntity.ok(
                storedFileService.getUserFiles(userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        Long userId = getUserId(jwt);

        StoredFile storedFile =
                storedFileService.getUserFile(userId, id);

        Path filePath =
                storedFileService.getDownloadPath(userId, id);

        Resource resource = new FileSystemResource(filePath);

        String contentType = storedFile.getContentType();

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(
                        storedFile.getOriginalFilename(),
                        StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        Long userId = getUserId(jwt);

        storedFileService.deleteUserFile(userId, id);

        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException(
                    "Authenticated JWT is required.");
        }

        Object userIdClaim = jwt.getClaim("userId");

        if (!(userIdClaim instanceof Number number)) {
            throw new IllegalStateException(
                    "JWT does not contain a valid userId.");
        }

        return number.longValue();
    }
}