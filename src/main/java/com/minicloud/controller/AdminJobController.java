package com.minicloud.controller;

import com.minicloud.dto.JobResponse;
import com.minicloud.service.JobService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final JobService jobService;

    public AdminJobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public List<JobResponse> listAllJobs() {
        return jobService.listAllForAdmin();
    }
}
