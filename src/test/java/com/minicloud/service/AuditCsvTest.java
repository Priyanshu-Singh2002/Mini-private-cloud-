package com.minicloud.service;

import com.minicloud.dto.AuditLogResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditCsvTest {

    @Test
    void cellLeavesPlainTextUnquoted() {
        assertEquals("alice", AuditCsv.cell("alice"));
        assertEquals("", AuditCsv.cell(null));
        assertEquals("", AuditCsv.cell(""));
    }

    @Test
    void cellQuotesCommasQuotesAndLineBreaks() {
        assertEquals("\"hello, world\"", AuditCsv.cell("hello, world"));
        assertEquals("\"say \"\"hi\"\"\"", AuditCsv.cell("say \"hi\""));
        assertEquals("\"line1\nline2\"", AuditCsv.cell("line1\nline2"));
        assertEquals("\"line1\r\nline2\"", AuditCsv.cell("line1\r\nline2"));
    }

    @Test
    void cellNeutralizesFormulaPrefixes() {
        assertEquals("'=1+1", AuditCsv.cell("=1+1"));
        assertEquals("'+1", AuditCsv.cell("+1"));
        assertEquals("'-1", AuditCsv.cell("-1"));
        assertEquals("'@SUM(A1)", AuditCsv.cell("@SUM(A1)"));
        assertEquals("'\tcmd", AuditCsv.cell("\tcmd"));
        assertEquals("\"'=1,2\"", AuditCsv.cell("=1,2"));
    }

    @Test
    void rowIncludesHeaderFieldsInOrder() {
        AuditLogResponse entry = new AuditLogResponse(
                9L,
                1L,
                "MC-ACTOR001",
                "=admin",
                "USER_APPROVED",
                "USER",
                5L,
                "MC-ENTITY1",
                "Changed quota, \"special\"",
                "SUCCESS",
                LocalDateTime.of(2026, 11, 24, 10, 15, 30)
        );

        String row = AuditCsv.row(entry);

        assertTrue(row.endsWith("\r\n"));
        assertEquals(
                "9,1,MC-ACTOR001,'=admin,USER_APPROVED,USER,5,MC-ENTITY1,\"Changed quota, \"\"special\"\"\",SUCCESS,2026-11-24T10:15:30\r\n",
                row
        );
        assertFalse(row.startsWith("="));
    }
}
