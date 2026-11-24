package com.minicloud.service;

import com.minicloud.entity.AuditLog;
import com.minicloud.repository.AuditLogRepository;

import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditLogService {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private static final Pattern UNIQUE_USER_ID =
            Pattern.compile("^MC-[A-Z0-9]{8}$");

    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService self;

    @Autowired
    public AuditLogService(
            AuditLogRepository auditLogRepository,
            @Lazy AuditLogService self) {
        this.auditLogRepository = auditLogRepository;
        this.self = self == null ? this : self;
    }

    public void record(
            Long actorUserId,
            String actorUsername,
            String actorUniqueId,
            String action,
            String entityType,
            Long entityId,
            String entityUniqueId,
            String description
    ) {
        record(
                actorUserId,
                actorUsername,
                actorUniqueId,
                action,
                entityType,
                entityId,
                entityUniqueId,
                description,
                SUCCESS);
    }

    /**
     * Writes one audit row. Successful outcomes that happen inside a
     * transaction are stored after that transaction commits, so a later
     * rollback does not leave a success record. Failures are stored
     * immediately in a separate transaction so they survive the rollback.
     * A failure to write the audit row is logged and does not replace the
     * business result.
     */
    public void record(
            Long actorUserId,
            String actorUsername,
            String actorUniqueId,
            String action,
            String entityType,
            Long entityId,
            String entityUniqueId,
            String description,
            String outcome
    ) {
        String storedOutcome = normalizeOutcome(outcome);
        String storedDescription = limit(description);
        Runnable write = () -> {
            try {
                self.persist(
                        actorUserId,
                        actorUsername,
                        actorUniqueId,
                        action,
                        entityType,
                        entityId,
                        entityUniqueId,
                        storedDescription,
                        storedOutcome);
            } catch (RuntimeException ex) {
                logger.error(
                        "Audit log write failed for action {}",
                        action,
                        ex);
            }
        };

        if (SUCCESS.equals(storedOutcome)
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            write.run();
                        }
                    });
            return;
        }

        write.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(
            Long actorUserId,
            String actorUsername,
            String actorUniqueId,
            String action,
            String entityType,
            Long entityId,
            String entityUniqueId,
            String description,
            String outcome
    ) {
        AuditLog auditLog = new AuditLog();

        auditLog.setActorUserId(actorUserId);
        auditLog.setActorUsername(limit(actorUsername, 50));
        auditLog.setActorUniqueId(snapshotUniqueId(actorUniqueId));
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setEntityUniqueId(snapshotEntityUniqueId(entityType, entityUniqueId));
        auditLog.setDescription(description);
        auditLog.setOutcome(outcome);

        auditLogRepository.save(auditLog);
    }

    private static String normalizeOutcome(String outcome) {
        if (SUCCESS.equals(outcome) || FAILURE.equals(outcome)) {
            return outcome;
        }
        return null;
    }

    private static String limit(String value) {
        return limit(value, MAX_DESCRIPTION_LENGTH);
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * Keeps a unique-user-ID snapshot only. Blank values and values that are
     * not a user unique ID (numeric file or job ids, for example) are stored
     * as null so existing history and non-user entities are not mislabeled.
     */
    private static String snapshotUniqueId(String uniqueId) {
        if (uniqueId == null || uniqueId.isBlank()) {
            return null;
        }

        String normalized = uniqueId.trim().toUpperCase(Locale.ROOT);
        if (!UNIQUE_USER_ID.matcher(normalized).matches()) {
            return null;
        }

        return normalized;
    }

    private static String snapshotEntityUniqueId(String entityType, String uniqueId) {
        if (!"USER".equals(entityType)) {
            return null;
        }

        return snapshotUniqueId(uniqueId);
    }
}
