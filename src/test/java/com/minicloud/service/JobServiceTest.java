package com.minicloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.dto.JobResponse;
import com.minicloud.dto.SubmitJobRequest;
import com.minicloud.entity.AppUser;
import com.minicloud.entity.ComputeJob;
import com.minicloud.job.JobPriority;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.ComputeJobRepository;
import com.minicloud.repository.StoredFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private ComputeJobRepository jobRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    private JobService jobService;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
                jobRepository,
                userRepository,
                storedFileRepository,
                new ObjectMapper(),
                2,
                2,
                1024,
                5);

        owner = new AppUser();
        ReflectionTestUtils.setField(owner, "id", 4L);
        owner.setUsername("alice");
    }

    @Test
    void submitCreatesQueuedJobWithoutExecutingIt() {
        when(jobRepository.countByStatus(JobStatus.QUEUED)).thenReturn(0L);
        when(jobRepository.countByOwnerUserIdAndStatusIn(eq(4L), any()))
                .thenReturn(0L);
        when(jobRepository.save(any(ComputeJob.class)))
                .thenAnswer(invocation -> {
                    ComputeJob job = invocation.getArgument(0);
                    job.setId(11L);
                    return job;
                });
        when(userRepository.findById(4L)).thenReturn(Optional.of(owner));

        JobResponse response = jobService.submit(
                4L,
                new SubmitJobRequest(
                        "BATCH_SUM",
                        "HIGH",
                        null,
                        List.of(1.0, 2.5)));

        assertEquals(11L, response.id());
        assertEquals(JobStatus.QUEUED, response.status());
        assertEquals(JobPriority.HIGH, response.priority());
        verify(jobRepository).save(any(ComputeJob.class));
    }

    @Test
    void usersCannotReadAnotherUsersJob() {
        when(jobRepository.findByIdAndOwnerUserId(9L, 4L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.getForUser(4L, 9L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void queueLimitIsEnforced() {
        when(jobRepository.countByStatus(JobStatus.QUEUED)).thenReturn(2L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.submit(
                        4L,
                        new SubmitJobRequest(
                                "BATCH_SUM",
                                "LOW",
                                null,
                                List.of(1.0))));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void claimNextSelectsHighestPriorityQueuedJob() {
        ComputeJob low = queued(1L, JobPriority.LOW, LocalDateTime.now().minusMinutes(5));
        ComputeJob high = queued(2L, JobPriority.HIGH, LocalDateTime.now());

        when(jobRepository.findAllByStatus(JobStatus.QUEUED))
                .thenReturn(List.of(low, high));
        when(jobRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(high));

        ComputeJob claimed = jobService.claimNextQueuedJob().orElseThrow();

        assertEquals(2L, claimed.getId());
        assertEquals(JobStatus.RUNNING, claimed.getStatus());
        assertNotNull(claimed.getStartedAt());
    }

    @Test
    void cancelOnlyAppliesToQueuedJobs() {
        ComputeJob queued = queued(3L, JobPriority.MEDIUM, LocalDateTime.now());
        when(jobRepository.findByIdAndOwnerUserId(3L, 4L))
                .thenReturn(Optional.of(queued));
        when(userRepository.findById(4L)).thenReturn(Optional.of(owner));

        JobResponse cancelled = jobService.cancelForUser(4L, 3L);
        assertEquals(JobStatus.CANCELLED, cancelled.status());

        ComputeJob running = queued(4L, JobPriority.MEDIUM, LocalDateTime.now());
        running.setStatus(JobStatus.RUNNING);
        when(jobRepository.findByIdAndOwnerUserId(4L, 4L))
                .thenReturn(Optional.of(running));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.cancelForUser(4L, 4L));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(JobStatus.RUNNING, running.getStatus());
    }

    @Test
    void rejectUnsupportedJobType() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.submit(
                        4L,
                        new SubmitJobRequest("SHELL", "HIGH", null, List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static ComputeJob queued(
            Long id,
            JobPriority priority,
            LocalDateTime submittedAt) {
        ComputeJob job = new ComputeJob();
        job.setId(id);
        job.setOwnerUserId(4L);
        job.setJobType(JobType.BATCH_SUM);
        job.setPriority(priority);
        job.setStatus(JobStatus.QUEUED);
        job.setSubmittedAt(submittedAt);
        job.setParameters("{\"values\":[1]}");
        return job;
    }
}
