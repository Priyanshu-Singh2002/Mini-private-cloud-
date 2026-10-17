
package com.minicloud.service;

import com.minicloud.dto.AdminUserResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final AppUserRepository userRepository;

    public AdminUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        AppUser::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse suspendUser(Long userId, Long adminId) {
        AppUser user = getUser(userId);

        if (userId.equals(adminId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot suspend your own admin account.");
        }

        if ("ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Admin accounts cannot be suspended through user management.");
        }

        user.setStatus("SUSPENDED");
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse reactivateUser(Long userId) {
        AppUser user = getUser(userId);
        user.setStatus("ACTIVE");
        return toResponse(user);
    }

    private AppUser getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."));
    }

    private AdminUserResponse toResponse(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
