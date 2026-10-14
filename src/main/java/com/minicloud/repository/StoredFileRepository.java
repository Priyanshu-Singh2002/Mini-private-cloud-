package com.minicloud.repository;

import com.minicloud.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository
        extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<StoredFile> findByIdAndUserId(Long id, Long userId);

    boolean existsByStoredFilename(String storedFilename);
}