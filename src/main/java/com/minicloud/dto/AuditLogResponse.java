package com.minicloud.dto;

import com.minicloud.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorUniqueId,
        String actorUsername,
        String action,
        String entityType,
        Long entityId,
        String entityUniqueId,
        String description,
        String outcome,
        LocalDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getActorUniqueId(),
                auditLog.getActorUsername(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getEntityUniqueId(),
                auditLog.getDescription(),
                auditLog.getOutcome(),
                auditLog.getCreatedAt()
        );
    }
}
