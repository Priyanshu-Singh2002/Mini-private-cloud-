package com.minicloud.repository;

import com.minicloud.entity.ComputeJob;
import com.minicloud.job.JobStatus;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComputeJobRepository
        extends JpaRepository<ComputeJob, Long> {

    List<ComputeJob> findAllByOwnerUserIdOrderBySubmittedAtDesc(Long ownerUserId);

    Optional<ComputeJob> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<ComputeJob> findAllByStatus(JobStatus status);

    List<ComputeJob> findAllByOrderBySubmittedAtDesc();

    long countByStatus(JobStatus status);

    long countByOwnerUserIdAndStatusIn(
            Long ownerUserId,
            Collection<JobStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ComputeJob j WHERE j.id = :id")
    Optional<ComputeJob> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE ComputeJob j
            SET j.status = com.minicloud.job.JobStatus.FAILED,
                j.errorMessage = :message,
                j.completedAt = CURRENT_TIMESTAMP
            WHERE j.status = com.minicloud.job.JobStatus.RUNNING
            """)
    int failOrphanedRunningJobs(@Param("message") String message);
}
