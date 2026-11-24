package com.minicloud.service;

import com.minicloud.dto.AuditLogPageResponse;
import com.minicloud.entity.AuditLog;
import com.minicloud.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(AdminAuditLogService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:audit;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class AdminAuditLogQueryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AdminAuditLogService adminAuditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();

        save("alice", "MC-ACTOR001", "USER_APPROVED", "USER", 11L, "MC-ENTITY1",
                "Approved alice", LocalDateTime.of(2026, 11, 20, 9, 0));
        save("bob", "MC-ACTOR002", "USER_REJECTED", "USER", 12L, "MC-ENTITY2",
                "Rejected bob", LocalDateTime.of(2026, 11, 21, 9, 0));
        save("carol", "MC-ACTOR003", "USER_SUSPENDED", "USER", 13L, "MC-TARGET3",
                "Suspended carol", LocalDateTime.of(2026, 11, 22, 9, 0));
        save("=admin", "MC-ADMIN001", "USER_QUOTA_UPDATED", "USER", 14L, "MC-ENTITY4",
                "quota, \"raised\"", LocalDateTime.of(2026, 11, 23, 9, 0));
        save("system", null, "FILE_DELETED", "FILE", 42L, null,
                "100% complete", LocalDateTime.of(2026, 11, 24, 9, 0));
    }

    @Test
    void paginationReturnsNewestRecordsFirst() {
        AuditLogPageResponse firstPage = adminAuditLogService.search(
                0, 2, null, null, null, null, null);
        AuditLogPageResponse secondPage = adminAuditLogService.search(
                1, 2, null, null, null, null, null);

        assertEquals(5L, firstPage.totalRecords());
        assertEquals(5L, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        assertEquals(2, firstPage.content().size());
        assertEquals("FILE_DELETED", firstPage.content().get(0).action());
        assertEquals("USER_QUOTA_UPDATED", firstPage.content().get(1).action());
        assertEquals("USER_SUSPENDED", secondPage.content().get(0).action());
        assertEquals("FILE_DELETED", firstPage.latestActivity().action());
    }

    @Test
    void actionAndEntityTypeFiltersAreApplied() {
        AuditLogPageResponse byAction = adminAuditLogService.search(
                0, 20, "USER_APPROVED", null, null, null, null);
        AuditLogPageResponse byEntity = adminAuditLogService.search(
                0, 20, null, "FILE", null, null, null);

        assertEquals(1, byAction.content().size());
        assertEquals("alice", byAction.content().getFirst().actorUsername());
        assertEquals(5L, byAction.totalRecords());

        assertEquals(1, byEntity.content().size());
        assertEquals(42L, byEntity.content().getFirst().entityId());
        assertEquals("FILE", byEntity.content().getFirst().entityType());
    }

    @Test
    void searchMatchesUsernameUniqueIdsAndDescription() {
        assertEquals("alice", onlyUsername(adminAuditLogService.search(
                0, 20, null, null, "ali", null, null)));
        assertEquals("bob", onlyUsername(adminAuditLogService.search(
                0, 20, null, null, "mc-actor002", null, null)));
        assertEquals("carol", onlyUsername(adminAuditLogService.search(
                0, 20, null, null, "MC-TARGET3", null, null)));
        assertEquals("bob", onlyUsername(adminAuditLogService.search(
                0, 20, null, null, "Rejected", null, null)));
    }

    @Test
    void searchDoesNotTreatPercentAsAWildcard() {
        AuditLogPageResponse matches = adminAuditLogService.search(
                0, 20, null, null, "%", null, null);

        assertEquals(1, matches.content().size());
        assertEquals("FILE_DELETED", matches.content().getFirst().action());
    }

    @Test
    void dateFilterIsInclusiveOfTheWholeEndDay() {
        AuditLogPageResponse matches = adminAuditLogService.search(
                0, 20, null, null, null,
                LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 23));

        assertEquals(2, matches.content().size());
        assertEquals("USER_QUOTA_UPDATED", matches.content().get(0).action());
        assertEquals("USER_SUSPENDED", matches.content().get(1).action());
    }

    @Test
    void csvExportUsesTheSameFiltersAndEscapesValues() throws Exception {
        StringWriter writer = new StringWriter();
        adminAuditLogService.writeCsv(
                "USER_QUOTA_UPDATED", null, null, null, null, writer);
        String csv = writer.toString();

        assertTrue(csv.startsWith(AuditCsv.HEADER + "\r\n"));
        assertTrue(csv.contains("'=admin"));
        assertTrue(csv.contains("\"quota, \"\"raised\"\"\""));
        assertTrue(csv.contains("MC-ADMIN001"));
        assertTrue(csv.contains("MC-ENTITY4"));
        assertFalse(csv.contains("alice"));
        assertFalse(csv.contains("FILE_DELETED"));
        String[] lines = csv.split("\r\n", -1);
        assertEquals(3, lines.length);
        assertEquals("", lines[2]);
        assertTrue(lines[1].contains("USER_QUOTA_UPDATED"));
    }

    private String onlyUsername(AuditLogPageResponse response) {
        assertEquals(1, response.content().size());
        return response.content().getFirst().actorUsername();
    }

    private void save(
            String username,
            String actorUniqueId,
            String action,
            String entityType,
            Long entityId,
            String entityUniqueId,
            String description,
            LocalDateTime createdAt
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(1L);
        auditLog.setActorUsername(username);
        auditLog.setActorUniqueId(actorUniqueId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setEntityUniqueId(entityUniqueId);
        auditLog.setDescription(description);
        auditLog.setCreatedAt(createdAt);
        auditLogRepository.saveAndFlush(auditLog);
    }
}
