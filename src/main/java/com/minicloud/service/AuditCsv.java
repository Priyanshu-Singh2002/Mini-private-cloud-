package com.minicloud.service;

import com.minicloud.dto.AuditLogResponse;

import java.time.format.DateTimeFormatter;

/**
 * RFC 4180-style CSV cells. Text that could be interpreted as a spreadsheet
 * formula is prefixed so Excel and similar tools treat it as text.
 */
public final class AuditCsv {

    public static final String HEADER =
            "id,actorUserId,actorUniqueId,actorUsername,action,entityType,entityId,entityUniqueId,description,outcome,createdAt";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private AuditCsv() {
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return "";
        }

        char first = text.charAt(0);
        if (first == '='
                || first == '+'
                || first == '-'
                || first == '@'
                || first == '\t'
                || first == '\r') {
            text = "'" + text;
        }

        if (text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }

    public static String row(AuditLogResponse entry) {
        String createdAt = entry.createdAt() == null
                ? ""
                : TIMESTAMP.format(entry.createdAt());

        return String.join(",",
                cell(entry.id()),
                cell(entry.actorUserId()),
                cell(entry.actorUniqueId()),
                cell(entry.actorUsername()),
                cell(entry.action()),
                cell(entry.entityType()),
                cell(entry.entityId()),
                cell(entry.entityUniqueId()),
                cell(entry.description()),
                cell(entry.outcome()),
                cell(createdAt)
        ) + "\r\n";
    }
}
