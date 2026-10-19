package com.minicloud.dto;

import java.util.List;

public record SubmitJobRequest(
        String jobType,
        String priority,
        Long fileId,
        List<Double> values
) {
}
