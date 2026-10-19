package com.minicloud.service;

import com.minicloud.entity.ComputeJob;
import com.minicloud.job.JobPriority;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;
import com.minicloud.repository.ComputeJobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobDispatchSchedulerTest {

    @Mock
    private JobService jobService;

    @Mock
    private JobExecutor jobExecutor;

    @Mock
    private ExecutorService jobWorkerExecutor;

    @Mock
    private ComputeJobRepository jobRepository;

    private JobDispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JobDispatchScheduler(
                jobService,
                jobExecutor,
                jobWorkerExecutor,
                jobRepository,
                1);
    }

    @Test
    void rejectedExecutionRequeuesTheClaimedJob() {
        ComputeJob job = new ComputeJob();
        job.setId(5L);
        job.setOwnerUserId(1L);
        job.setJobType(JobType.BATCH_SUM);
        job.setPriority(JobPriority.HIGH);
        job.setStatus(JobStatus.RUNNING);
        job.setSubmittedAt(LocalDateTime.now());

        when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(0L);
        when(jobService.claimNextQueuedJob()).thenReturn(Optional.of(job));
        doThrow(new RejectedExecutionException("queue full"))
                .when(jobWorkerExecutor)
                .execute(any());

        scheduler.dispatch();

        verify(jobService).requeueIfRunning(5L);
        verify(jobExecutor, never()).execute(any());
    }

    @Test
    void doesNotClaimWorkWhenWorkersAreBusy() {
        when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(1L);

        scheduler.dispatch();

        verify(jobService, never()).claimNextQueuedJob();
    }
}
