package com.minicloud.service;

import com.minicloud.dto.AuditLogPageResponse;
import com.minicloud.dto.AuditLogResponse;
import com.minicloud.entity.AuditLog;
import com.minicloud.repository.AuditLogRepository;

import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuditLogService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int EXPORT_BATCH_SIZE = 500;
    public static final int MAX_SEARCH_LENGTH = 200;

    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final AuditLogRepository auditLogRepository;

    public AdminAuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse search(
            int page,
            int size,
            String action,
            String entityType,
            String search,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validatePage(page, size);
        validateFilters(search, fromDate, toDate);

        Page<AuditLog> result = auditLogRepository.findAll(
                specification(action, entityType, search, fromDate, toDate),
                PageRequest.of(page, size, NEWEST_FIRST)
        );

        List<AuditLogResponse> content = result.getContent().stream()
                .map(AuditLogResponse::from)
                .toList();

        AuditLogResponse latestActivity = auditLogRepository
                .findFirstByOrderByCreatedAtDesc()
                .map(AuditLogResponse::from)
                .orElse(null);

        return new AuditLogPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                auditLogRepository.count(),
                latestActivity,
                auditLogRepository.findDistinctActions(),
                auditLogRepository.findDistinctEntityTypes()
        );
    }

    /**
     * Writes every row that matches the filters. Rows are read in fixed-size
     * pages so a large audit table is not loaded in one query.
     */
    @Transactional(readOnly = true)
    public void writeCsv(
            String action,
            String entityType,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            Writer writer
    ) throws IOException {
        validateFilters(search, fromDate, toDate);

        writer.write(AuditCsv.HEADER);
        writer.write("\r\n");

        Specification<AuditLog> filter = specification(
                action, entityType, search, fromDate, toDate);

        int pageNumber = 0;
        Page<AuditLog> batch;
        do {
            Pageable pageable = PageRequest.of(
                    pageNumber, EXPORT_BATCH_SIZE, NEWEST_FIRST);
            batch = auditLogRepository.findAll(filter, pageable);
            for (AuditLog auditLog : batch.getContent()) {
                writer.write(AuditCsv.row(AuditLogResponse.from(auditLog)));
            }
            pageNumber++;
        } while (batch.hasNext());
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be zero or greater.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    private void validateFilters(
            String search,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String normalizedSearch = blankToNull(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "search must be " + MAX_SEARCH_LENGTH + " characters or fewer.");
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "fromDate must be on or before toDate.");
        }
    }

    private Specification<AuditLog> specification(
            String action,
            String entityType,
            String search,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String normalizedAction = blankToNull(action);
        String normalizedEntityType = blankToNull(entityType);
        String normalizedSearch = blankToNull(search);

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (normalizedAction != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("action"), normalizedAction));
            }

            if (normalizedEntityType != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("entityType"), normalizedEntityType));
            }

            if (normalizedSearch != null) {
                String pattern = "%" + escapeLike(normalizedSearch.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("actorUsername")),
                                pattern,
                                '\\'),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("actorUniqueId")),
                                pattern,
                                '\\'),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("entityUniqueId")),
                                pattern,
                                '\\'),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("description")),
                                pattern,
                                '\\'),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("outcome")),
                                pattern,
                                '\\')
                ));
            }

            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"), fromDate.atStartOfDay()));
            }

            if (toDate != null) {
                LocalDateTime endExclusive = toDate.plusDays(1).atStartOfDay();
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"), endExclusive));
            }

            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
