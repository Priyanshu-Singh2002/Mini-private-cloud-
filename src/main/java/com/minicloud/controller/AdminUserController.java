
package com.minicloud.controller;

import com.minicloud.dto.AdminUserResponse;
import com.minicloud.dto.RegistrationRequestAdminResponse;
import com.minicloud.dto.UpdateStorageQuotaRequest;
import com.minicloud.dto.UserStorageStats;

import com.minicloud.service.AdminUserService;
import com.minicloud.service.StorageQuotaService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final StorageQuotaService storageQuotaService;

    public AdminUserController(
            AdminUserService adminUserService,
            StorageQuotaService storageQuotaService) {
        this.adminUserService = adminUserService;
        this.storageQuotaService = storageQuotaService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers();
    }

    @GetMapping("/pending")
    public List<RegistrationRequestAdminResponse> listPendingRequests() {
        return adminUserService.listPendingRequests();
    }

    @PatchMapping("/{userId}/approve")
    public ResponseEntity<RegistrationRequestAdminResponse> approveRegistration(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = getUserId(jwt);

        return ResponseEntity.ok(
                adminUserService.approveRegistration(userId, adminId));
    }

    @PatchMapping("/{userId}/reject")
    public ResponseEntity<RegistrationRequestAdminResponse> rejectRegistration(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = getUserId(jwt);

        return ResponseEntity.ok(
                adminUserService.rejectRegistration(userId, adminId));
    }

    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<AdminUserResponse> suspendUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = getUserId(jwt);

        return ResponseEntity.ok(
                adminUserService.suspendUser(userId, adminId));
    }

    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<AdminUserResponse> reactivateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = getUserId(jwt);

        return ResponseEntity.ok(
                adminUserService.reactivateUser(userId, adminId));
    }

    @PatchMapping("/{userId}/quota")
    public ResponseEntity<UserStorageStats> updateQuota(
            @PathVariable Long userId,
            @RequestBody UpdateStorageQuotaRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = getUserId(jwt);

        return ResponseEntity.ok(
                storageQuotaService.updateQuota(userId, request, adminId));
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        Object claim = jwt.getClaim("userId");

        if (!(claim instanceof Number userId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity in token.");
        }

        return userId.longValue();
    }
}
