
package com.minicloud.controller;

import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.service.AdminSetupService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AdminSetupController {

    private final AdminSetupService adminSetupService;

    public AdminSetupController(AdminSetupService adminSetupService) {
        this.adminSetupService = adminSetupService;
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<RegisterResponse> createFirstAdmin(
            @RequestBody RegisterRequest request,
            @RequestHeader(
                    name = "X-Admin-Setup-Secret",
                    required = false) String setupSecret) {

        RegisterResponse response =
                adminSetupService.createFirstAdmin(request, setupSecret);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
