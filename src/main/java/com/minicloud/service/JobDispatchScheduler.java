package com.minicloud.service;

import com.minicloud.entity.ComputeJob;
import com.minicloud.job.JobStatus;
import com.minicloud.repository.ComputeJobRepository;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Single-machine prototype dispatcher.
 *
 * QUEUED jobs are selected by HIGH, then MEDIUM, then LOW priority, with
 * earlier submission time and lower job ID as tie-breakers. Already RUNNING
 * jobs are never preempted. After a crash, jobs left in RUNNING are marked
 * FAILED because in-memory worker state cannot be recovered.
 */
@Component
public class JobDispatchScheduler {

    private final JobService jobService;
    private final JobExecutor jobExecutor;
    private final ExecutorService jobWorkerExecutor;
    private final ComputeJobRepository jobRepository;
    private final int workerThreads;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public JobDispatchScheduler(
            JobService jobService,
            JobExecutor jobExecutor,
            ExecutorService jobWorkerExecutor,
            ComputeJobRepository jobRepository,
            @Value("${app.jobs.worker-threads:2}") int workerThreads) {
        this.jobService = jobService;
        this.jobExecutor = jobExecutor;
        this.jobWorkerExecutor = jobWorkerExecutor;
        this.jobRepository = jobRepository;
        this.workerThreads = Math.max(1, workerThreads);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        jobService.failOrphanedRunningJobs();
    }

    @Scheduled(fixedDelayString = "${app.jobs.dispatch-interval-ms:250}")
    public void dispatch() {
        int running = (int) jobRepository.countByStatus(JobStatus.RUNNING);
        int freeSlots = workerThreads - running;

        for (int i = 0; i < freeSlots; i++) {
            Optional<ComputeJob> claimed = jobService.claimNextQueuedJob();
            if (claimed.isEmpty()) {
                return;
            }

            Long jobId = claimed.get().getId();
            if (!inFlight.add(jobId)) {
                continue;
            }

            try {
                jobWorkerExecutor.execute(() -> {
                    try {
                        jobExecutor.execute(jobId);
                    } finally {
                        inFlight.remove(jobId);
                    }
                });
            } catch (RejectedExecutionException ex) {
                inFlight.remove(jobId);
                jobService.requeueIfRunning(jobId);
                return;
            }
        }
    }
}
