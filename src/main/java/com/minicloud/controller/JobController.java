package com.minicloud.controller;

import com.minicloud.dto.JobResponse;
import com.minicloud.dto.SubmitJobRequest;
import com.minicloud.service.JobService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SubmitJobRequest request) {
        return jobService.submit(getUserId(jwt), request);
    }

    @GetMapping
    public List<JobResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return jobService.listForUser(getUserId(jwt));
    }

    @GetMapping("/{jobId}")
    public JobResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) {
        return jobService.getForUser(getUserId(jwt), jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public JobResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) {
        return jobService.cancelForUser(getUserId(jwt), jobId);
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException(
                    "Authenticated JWT is required.");
        }

        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number number)) {
            throw new IllegalStateException(
                    "JWT does not contain a valid userId.");
        }

        return number.longValue();
    }
}
