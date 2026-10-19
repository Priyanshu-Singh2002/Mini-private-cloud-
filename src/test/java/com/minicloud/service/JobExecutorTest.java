package com.minicloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.entity.ComputeJob;
import com.minicloud.entity.StoredFile;
import com.minicloud.job.JobPriority;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;
import com.minicloud.repository.ComputeJobRepository;
import com.minicloud.repository.StoredFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private ComputeJobRepository jobRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    private JobExecutor jobExecutor;

    @BeforeEach
    void setUp() {
        jobExecutor = new JobExecutor(
                jobRepository,
                storedFileRepository,
                fileStorageService,
                new ObjectMapper(),
                1024);
    }

    @Test
    void batchSumCompletesWithResult() {
        ComputeJob job = runningJob(JobType.BATCH_SUM, "{\"values\":[2,3,5]}");
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));

        jobExecutor.execute(8L);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertTrue(job.getResult().contains("\"sum\":10"));
        assertNull(job.getErrorMessage());
    }

    @Test
    void textStatsUsesOwnedFileContents() {
        ComputeJob job = runningJob(JobType.TEXT_STATS, "{\"fileId\":15}");
        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(4L);
        storedFile.setStoredFilename("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        storedFile.setOriginalFilename("notes.txt");
        storedFile.setFileSize(12L);

        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(storedFileRepository.findByIdAndUserId(15L, 4L))
                .thenReturn(Optional.of(storedFile));
        when(fileStorageService.readBoundedText(
                storedFile.getStoredFilename(), 1024L))
                .thenReturn("hello world\nsecond");

        jobExecutor.execute(8L);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertTrue(job.getResult().contains("\"wordCount\":3"));
        assertTrue(job.getResult().contains("\"lineCount\":2"));
    }

    @Test
    void missingFileFailsTheJob() {
        ComputeJob job = runningJob(JobType.CSV_STATS, "{\"fileId\":99}");
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(storedFileRepository.findByIdAndUserId(99L, 4L))
                .thenReturn(Optional.empty());

        jobExecutor.execute(8L);

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("The job input file was not found.", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void queuedJobsAreNotExecuted() {
        ComputeJob job = runningJob(JobType.BATCH_SUM, "{\"values\":[1]}");
        job.setStatus(JobStatus.QUEUED);
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));

        jobExecutor.execute(8L);

        assertEquals(JobStatus.QUEUED, job.getStatus());
        assertNull(job.getResult());
    }

    private static ComputeJob runningJob(JobType type, String parameters) {
        ComputeJob job = new ComputeJob();
        job.setId(8L);
        job.setOwnerUserId(4L);
        job.setJobType(type);
        job.setPriority(JobPriority.MEDIUM);
        job.setStatus(JobStatus.RUNNING);
        job.setParameters(parameters);
        job.setSubmittedAt(LocalDateTime.now());
        job.setStartedAt(LocalDateTime.now());
        return job;
    }
}
