package com.minicloud.dto;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long totalRecords,
        AuditLogResponse latestActivity,
        List<String> actions,
        List<String> entityTypes
) {
}
