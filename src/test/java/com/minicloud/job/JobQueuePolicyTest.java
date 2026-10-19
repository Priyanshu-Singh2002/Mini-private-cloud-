package com.minicloud.job;

import com.minicloud.entity.ComputeJob;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobQueuePolicyTest {

    @Test
    void highPriorityIsSelectedBeforeEarlierLowPriorityJobs() {
        LocalDateTime earlier = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime later = earlier.plusMinutes(5);

        ComputeJob low = job(1L, JobPriority.LOW, earlier);
        ComputeJob high = job(2L, JobPriority.HIGH, later);

        ComputeJob selected = JobQueuePolicy
                .selectNext(List.of(low, high))
                .orElseThrow();

        assertEquals(2L, selected.getId());
        assertEquals(JobPriority.HIGH, selected.getPriority());
    }

    @Test
    void equalPriorityUsesSubmissionTimeThenId() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 10, 0);

        ComputeJob second = job(20L, JobPriority.MEDIUM, time);
        ComputeJob first = job(10L, JobPriority.MEDIUM, time);

        ComputeJob selected = JobQueuePolicy
                .selectNext(List.of(second, first))
                .orElseThrow();

        assertEquals(10L, selected.getId());
    }

    @Test
    void runningJobsAreNotSelected() {
        ComputeJob running = job(1L, JobPriority.HIGH, LocalDateTime.now());
        running.setStatus(JobStatus.RUNNING);

        assertTrue(JobQueuePolicy.selectNext(List.of(running)).isEmpty());
    }

    private static ComputeJob job(
            Long id,
            JobPriority priority,
            LocalDateTime submittedAt) {
        ComputeJob job = new ComputeJob();
        job.setId(id);
        job.setOwnerUserId(1L);
        job.setJobType(JobType.BATCH_SUM);
        job.setPriority(priority);
        job.setStatus(JobStatus.QUEUED);
        job.setSubmittedAt(submittedAt);
        return job;
    }
}
