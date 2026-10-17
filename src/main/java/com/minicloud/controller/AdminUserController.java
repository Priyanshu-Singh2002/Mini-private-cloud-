
package com.minicloud.controller;

import com.minicloud.dto.AdminUserResponse;
import com.minicloud.service.AdminUserService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers();
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
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                adminUserService.reactivateUser(userId));
    }

    private Long getUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");

        if (!(claim instanceof Number userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid user identity in token.");
        }

        return userId.longValue();
    }
}
