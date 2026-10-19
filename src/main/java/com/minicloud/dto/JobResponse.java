package com.minicloud.dto;

import com.minicloud.job.JobPriority;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;

import java.time.LocalDateTime;

public record JobResponse(
        Long id,
        Long ownerUserId,
        String ownerUsername,
        JobType jobType,
        JobPriority priority,
        JobStatus status,
        String parameters,
        LocalDateTime submittedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String result,
        String errorMessage
) {
}
