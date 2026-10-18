
package com.minicloud.repository;

import com.minicloud.entity.StoredFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository
        extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<StoredFile> findByIdAndUserId(Long id, Long userId);

    boolean existsByStoredFilename(String storedFilename);

    // Total number of files stored in the cloud.
    @Query("SELECT COUNT(f) FROM StoredFile f")
    long countAllStoredFiles();

    // Total storage consumed by all stored files, in bytes.
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM StoredFile f")
    Long calculateTotalStorageBytes();

    // One row per user: [userId, fileCount, totalBytes].
    @Query("""
            SELECT f.userId, COUNT(f), COALESCE(SUM(f.fileSize), 0)
            FROM StoredFile f
            GROUP BY f.userId
            ORDER BY f.userId
            """)
    List<Object[]> calculateStorageByUser();
}
