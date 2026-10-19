package com.minicloud.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicloud.entity.ComputeJob;
import com.minicloud.entity.StoredFile;
import com.minicloud.job.JobStatus;
import com.minicloud.job.JobType;
import com.minicloud.repository.ComputeJobRepository;
import com.minicloud.repository.StoredFileRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExecutor {

    private final ComputeJobRepository jobRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final long maxInputBytes;

    public JobExecutor(
            ComputeJobRepository jobRepository,
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            ObjectMapper objectMapper,
            @Value("${app.jobs.max-input-bytes:1048576}") long maxInputBytes) {
        this.jobRepository = jobRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.maxInputBytes = maxInputBytes;
    }

    @Transactional
    public void execute(Long jobId) {
        ComputeJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) {
            return;
        }

        try {
            String result = runJob(job);
            job.setStatus(JobStatus.COMPLETED);
            job.setResult(result);
            job.setErrorMessage(null);
            job.setCompletedAt(LocalDateTime.now());
        } catch (Exception ex) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(safeMessage(ex));
            job.setCompletedAt(LocalDateTime.now());
        }
    }

    private String runJob(ComputeJob job) throws Exception {
        JsonNode parameters = objectMapper.readTree(
                job.getParameters() == null ? "{}" : job.getParameters());

        if (job.getJobType() == JobType.TEXT_STATS) {
            String text = readOwnedFileText(job, parameters);
            return objectMapper.writeValueAsString(textStats(text));
        }

        if (job.getJobType() == JobType.CSV_STATS) {
            String text = readOwnedFileText(job, parameters);
            return objectMapper.writeValueAsString(csvStats(text));
        }

        if (job.getJobType() == JobType.BATCH_SUM) {
            return objectMapper.writeValueAsString(batchSum(parameters));
        }

        throw new IllegalArgumentException("Unsupported job type.");
    }

    private String readOwnedFileText(ComputeJob job, JsonNode parameters) {
        if (parameters.path("fileId").isMissingNode()) {
            throw new IllegalArgumentException("fileId is required.");
        }

        long fileId = parameters.path("fileId").asLong();
        StoredFile storedFile = storedFileRepository
                .findByIdAndUserId(fileId, job.getOwnerUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The job input file was not found."));

        return fileStorageService.readBoundedText(
                storedFile.getStoredFilename(),
                maxInputBytes);
    }

    private static Map<String, Object> textStats(String text) {
        String value = text == null ? "" : text;
        int lineCount = value.isEmpty() ? 0 : value.split("\\R", -1).length;
        String trimmed = value.trim();
        int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lineCount", lineCount);
        result.put("wordCount", wordCount);
        result.put("characterCount", value.length());
        return result;
    }

    private static Map<String, Object> csvStats(String text) {
        String value = text == null ? "" : text;
        String[] lines = value.isEmpty() ? new String[0] : value.split("\\R", -1);

        int rowCount = 0;
        int columnCount = 0;
        int emptyCells = 0;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            String[] cells = line.split(",", -1);
            if (columnCount == 0) {
                columnCount = cells.length;
            }

            rowCount++;
            for (String cell : cells) {
                if (cell == null || cell.isBlank()) {
                    emptyCells++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowCount", rowCount);
        result.put("columnCount", columnCount);
        result.put("emptyCellCount", emptyCells);
        return result;
    }

    private static Map<String, Object> batchSum(JsonNode parameters) {
        JsonNode values = parameters.path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException("BATCH_SUM requires a values array.");
        }

        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int count = 0;

        for (JsonNode node : values) {
            if (!node.isNumber() || !Double.isFinite(node.asDouble())) {
                throw new IllegalArgumentException(
                        "BATCH_SUM values must be finite numbers.");
            }

            double number = node.asDouble();
            sum += number;
            min = Math.min(min, number);
            max = Math.max(max, number);
            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("sum", sum);
        result.put("min", min);
        result.put("max", max);
        return result;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Job execution failed.";
        }

        return message.substring(0, Math.min(message.length(), 500));
    }
}
