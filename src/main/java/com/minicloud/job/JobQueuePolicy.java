package com.minicloud.job;

import com.minicloud.entity.ComputeJob;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class JobQueuePolicy {

    public static final Comparator<ComputeJob> QUEUE_ORDER =
            Comparator.comparingInt((ComputeJob job) -> job.getPriority().rank())
                    .thenComparing(ComputeJob::getSubmittedAt)
                    .thenComparing(ComputeJob::getId);

    private JobQueuePolicy() {
    }

    public static Optional<ComputeJob> selectNext(List<ComputeJob> queuedJobs) {
        if (queuedJobs == null || queuedJobs.isEmpty()) {
            return Optional.empty();
        }

        return queuedJobs.stream()
                .filter(job -> job.getStatus() == JobStatus.QUEUED)
                .min(QUEUE_ORDER);
    }
}
