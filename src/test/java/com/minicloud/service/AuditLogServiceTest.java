package com.minicloud.service;

import com.minicloud.entity.AuditLog;
import com.minicloud.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void recordShouldSaveAuditLog() {
        auditLogService.record(
                1L,
                "admin",
                "MC-F850BEE1",
                "USER_APPROVED",
                "USER",
                5L,
                "mc-a7k92p4x",
                "Administrator approved a registration."
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();

        assertEquals(1L, savedLog.getActorUserId());
        assertEquals("admin", savedLog.getActorUsername());
        assertEquals("MC-F850BEE1", savedLog.getActorUniqueId());
        assertEquals("USER_APPROVED", savedLog.getAction());
        assertEquals("USER", savedLog.getEntityType());
        assertEquals(5L, savedLog.getEntityId());
        assertEquals("MC-A7K92P4X", savedLog.getEntityUniqueId());
        assertEquals(
                "Administrator approved a registration.",
                savedLog.getDescription()
        );
    }

    @Test
    void recordShouldAllowSystemGeneratedAuditLog() {
        auditLogService.record(
                null,
                "SYSTEM",
                null,
                "JOB_COMPLETED",
                "JOB",
                10L,
                null,
                "Job completed successfully."
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();

        assertNull(savedLog.getActorUserId());
        assertNull(savedLog.getActorUniqueId());
        assertEquals("SYSTEM", savedLog.getActorUsername());
        assertEquals("JOB_COMPLETED", savedLog.getAction());
        assertEquals("JOB", savedLog.getEntityType());
        assertEquals(10L, savedLog.getEntityId());
        assertNull(savedLog.getEntityUniqueId());
    }

    @Test
    void recordShouldNotStoreFileOrJobIdsAsUserUniqueIds() {
        auditLogService.record(
                null,
                "SYSTEM",
                null,
                "FILE_DELETED",
                "FILE",
                42L,
                "42",
                "Stored file was deleted."
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();

        assertEquals(42L, savedLog.getEntityId());
        assertNull(savedLog.getActorUniqueId());
        assertNull(savedLog.getEntityUniqueId());
    }

    @Test
    void recordShouldIgnoreEntityUniqueIdWhenUserHasNone() {
        auditLogService.record(
                1L,
                "admin",
                "MC-F850BEE1",
                "USER_REJECTED",
                "USER",
                8L,
                "   ",
                "Administrator rejected a pending registration."
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();

        assertEquals("MC-F850BEE1", savedLog.getActorUniqueId());
        assertEquals(8L, savedLog.getEntityId());
        assertNull(savedLog.getEntityUniqueId());
    }
}
