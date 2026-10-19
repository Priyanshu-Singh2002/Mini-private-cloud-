package com.minicloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.dto.JobResponse;
import com.minicloud.dto.SubmitJobRequest;
import com.minicloud.entity.AppUser;
import com.minicloud.entity.ComputeJob;
import com.minicloud.entity.StoredFile;
import com.minicloud.job.JobPriority;
import com.minicloud.job.JobQueuePolicy;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.repository.ComputeJobRepository;
import com.minicloud.repository.StoredFileRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    static final String RESTART_FAILURE_MESSAGE =
            "Job was interrupted by an application restart and marked failed.";

    private final ComputeJobRepository jobRepository;
    private final AppUserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectMapper objectMapper;
    private final int maxQueuedGlobal;
    private final int maxActivePerUser;
    private final long maxInputBytes;
    private final int maxBatchValues;

    public JobService(
            ComputeJobRepository jobRepository,
            AppUserRepository userRepository,
            StoredFileRepository storedFileRepository,
            ObjectMapper objectMapper,
            @Value("${app.jobs.max-queued-global:50}") int maxQueuedGlobal,
            @Value("${app.jobs.max-active-per-user:10}") int maxActivePerUser,
            @Value("${app.jobs.max-input-bytes:1048576}") long maxInputBytes,
            @Value("${app.jobs.max-batch-values:2000}") int maxBatchValues) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.storedFileRepository = storedFileRepository;
        this.objectMapper = objectMapper;
        this.maxQueuedGlobal = maxQueuedGlobal;
        this.maxActivePerUser = maxActivePerUser;
        this.maxInputBytes = maxInputBytes;
        this.maxBatchValues = maxBatchValues;
    }

    @Transactional
    public JobResponse submit(Long userId, SubmitJobRequest request) {
        if (request == null || request.jobType() == null
                || request.jobType().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "jobType is required.");
        }

        JobType jobType = parseJobType(request.jobType());
        JobPriority priority = parsePriority(request.priority());
        String parameters = validateAndSerializeParameters(userId, jobType, request);

        long queuedGlobal = jobRepository.countByStatus(JobStatus.QUEUED);
        if (queuedGlobal >= maxQueuedGlobal) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The job queue is full. Try again later.");
        }

        long userActive = jobRepository.countByOwnerUserIdAndStatusIn(
                userId,
                List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        if (userActive >= maxActivePerUser) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You already have the maximum number of active jobs.");
        }

        ComputeJob job = new ComputeJob();
        job.setOwnerUserId(userId);
        job.setJobType(jobType);
        job.setPriority(priority);
        job.setStatus(JobStatus.QUEUED);
        job.setParameters(parameters);
        job.setSubmittedAt(LocalDateTime.now());

        return toResponse(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listForUser(Long userId) {
        return jobRepository
                .findAllByOwnerUserIdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse getForUser(Long userId, Long jobId) {
        ComputeJob job = jobRepository.findByIdAndOwnerUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Job not found."));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listAllForAdmin() {
        return jobRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse cancelForUser(Long userId, Long jobId) {
        ComputeJob job = jobRepository.findByIdAndOwnerUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Job not found."));

        if (job.getStatus() == JobStatus.QUEUED) {
            job.setStatus(JobStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage("Cancelled by owner before execution started.");
            return toResponse(job);
        }

        if (job.getStatus() == JobStatus.RUNNING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A running job cannot be cancelled in this prototype.");
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only queued jobs can be cancelled.");
    }

    @Transactional
    public Optional<ComputeJob> claimNextQueuedJob() {
        List<ComputeJob> queued = jobRepository.findAllByStatus(JobStatus.QUEUED);
        Optional<ComputeJob> selected = JobQueuePolicy.selectNext(queued);
        if (selected.isEmpty() || selected.get().getId() == null) {
            return Optional.empty();
        }

        ComputeJob locked = jobRepository
                .findByIdForUpdate(selected.get().getId())
                .orElse(null);

        if (locked == null || locked.getStatus() != JobStatus.QUEUED) {
            return Optional.empty();
        }

        locked.setStatus(JobStatus.RUNNING);
        locked.setStartedAt(LocalDateTime.now());
        locked.setErrorMessage(null);
        return Optional.of(locked);
    }

    @Transactional
    public void requeueIfRunning(Long jobId) {
        ComputeJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) {
            return;
        }

        job.setStatus(JobStatus.QUEUED);
        job.setStartedAt(null);
    }

    @Transactional
    public int failOrphanedRunningJobs() {
        return jobRepository.failOrphanedRunningJobs(RESTART_FAILURE_MESSAGE);
    }

    JobResponse toResponse(ComputeJob job) {
        String username = userRepository.findById(job.getOwnerUserId())
                .map(AppUser::getUsername)
                .orElse("unknown");

        return new JobResponse(
                job.getId(),
                job.getOwnerUserId(),
                username,
                job.getJobType(),
                job.getPriority(),
                job.getStatus(),
                job.getParameters(),
                job.getSubmittedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getResult(),
                job.getErrorMessage());
    }

    private String validateAndSerializeParameters(
            Long userId,
            JobType jobType,
            SubmitJobRequest request) {

        Map<String, Object> payload = new LinkedHashMap<>();

        if (jobType == JobType.TEXT_STATS || jobType == JobType.CSV_STATS) {
            if (request.fileId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "fileId is required for this job type.");
            }

            StoredFile storedFile = storedFileRepository
                    .findByIdAndUserId(request.fileId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "The selected file was not found in your storage."));

            if (storedFile.getFileSize() != null
                    && storedFile.getFileSize() > maxInputBytes) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The selected file exceeds the job input size limit.");
            }

            payload.put("fileId", storedFile.getId());
            payload.put("originalFilename", storedFile.getOriginalFilename());
        } else if (jobType == JobType.BATCH_SUM) {
            if (request.values() == null || request.values().isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "values are required for BATCH_SUM jobs.");
            }

            if (request.values().size() > maxBatchValues) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "BATCH_SUM accepts at most "
                                + maxBatchValues
                                + " numbers.");
            }

            for (Double value : request.values()) {
                if (value == null || !Double.isFinite(value)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "BATCH_SUM values must be finite numbers.");
                }
            }

            payload.put("values", request.values());
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Job parameters could not be stored.");
        }
    }

    private static JobType parseJobType(String value) {
        try {
            return JobType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported job type. Use TEXT_STATS, CSV_STATS or BATCH_SUM.");
        }
    }

    private static JobPriority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return JobPriority.MEDIUM;
        }

        try {
            return JobPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Priority must be HIGH, MEDIUM or LOW.");
        }
    }
}
