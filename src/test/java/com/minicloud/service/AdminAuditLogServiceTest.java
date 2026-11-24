package com.minicloud.service;

import com.minicloud.dto.AuditLogPageResponse;
import com.minicloud.entity.AuditLog;
import com.minicloud.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AdminAuditLogService adminAuditLogService;

    @BeforeEach
    void setUp() {
        adminAuditLogService = new AdminAuditLogService(auditLogRepository);
    }

    @Test
    void searchRequestsNewestPageFromTheDatabase() {
        AuditLog newest = sample("USER_APPROVED", "alice", LocalDateTime.of(2026, 11, 24, 12, 0));
        newest.setId(4L);

        when(auditLogRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newest), PageRequest.of(0, 20), 1));
        when(auditLogRepository.count()).thenReturn(8L);
        when(auditLogRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(newest));
        when(auditLogRepository.findDistinctActions()).thenReturn(List.of("USER_APPROVED"));
        when(auditLogRepository.findDistinctEntityTypes()).thenReturn(List.of("USER"));

        AuditLogPageResponse response = adminAuditLogService.search(
                0, 20, "USER_APPROVED", "USER", "alice",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(anySpecification(), pageable.capture());

        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(1, response.content().size());
        assertEquals(4L, response.content().getFirst().id());
        assertEquals("MC-ACTOR001", response.content().getFirst().actorUniqueId());
        assertEquals("MC-ENTITY1", response.content().getFirst().entityUniqueId());
        assertEquals(8L, response.totalRecords());
        assertEquals("USER_APPROVED", response.latestActivity().action());
        assertEquals(List.of("USER_APPROVED"), response.actions());
    }

    @Test
    void searchRejectsAnInvertedDateRangeBeforeQuerying() {
        assertThrows(ResponseStatusException.class, () -> adminAuditLogService.search(
                0, 20, null, null, null,
                LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1)));

        verify(auditLogRepository, never()).findAll(anySpecification(), any(Pageable.class));
    }

    @Test
    void exportWritesPagedRowsAndEscapesUnsafeText() throws Exception {
        AuditLog first = sample("USER_SUSPENDED", "=admin", LocalDateTime.of(2026, 11, 24, 9, 0));
        first.setId(2L);
        first.setDescription("quota, \"raised\"");

        AuditLog second = sample("USER_APPROVED", "alice", LocalDateTime.of(2026, 11, 23, 9, 0));
        second.setId(1L);

        when(auditLogRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    assertEquals(AdminAuditLogService.EXPORT_BATCH_SIZE, pageable.getPageSize());
                    assertEquals(
                            Sort.Direction.DESC,
                            pageable.getSort().getOrderFor("createdAt").getDirection());
                    if (pageable.getPageNumber() == 0) {
                        return new PageImpl<>(
                                List.of(first), pageable, AdminAuditLogService.EXPORT_BATCH_SIZE + 1);
                    }
                    return new PageImpl<>(List.of(second), pageable, AdminAuditLogService.EXPORT_BATCH_SIZE + 1);
                });

        StringWriter writer = new StringWriter();
        adminAuditLogService.writeCsv(null, null, null, null, null, writer);
        String csv = writer.toString();

        assertTrue(csv.startsWith(AuditCsv.HEADER + "\r\n"));
        assertTrue(csv.contains("'=admin"));
        assertTrue(csv.contains("\"quota, \"\"raised\"\"\""));
        assertTrue(csv.contains("alice"));
        verify(auditLogRepository, org.mockito.Mockito.times(2))
                .findAll(anySpecification(), any(Pageable.class));
    }

    private static AuditLog sample(String action, String username, LocalDateTime createdAt) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(1L);
        auditLog.setActorUsername(username);
        auditLog.setActorUniqueId("MC-ACTOR001");
        auditLog.setAction(action);
        auditLog.setEntityType("USER");
        auditLog.setEntityId(5L);
        auditLog.setEntityUniqueId("MC-ENTITY1");
        auditLog.setDescription("Administrator updated a user.");
        auditLog.setCreatedAt(createdAt);
        return auditLog;
    }

    @SuppressWarnings("unchecked")
    private static Specification<AuditLog> anySpecification() {
        return any(Specification.class);
    }
}
