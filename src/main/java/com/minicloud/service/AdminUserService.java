
package com.minicloud.service;

import com.minicloud.dto.AdminUserResponse;
import com.minicloud.dto.RegistrationRequestAdminResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;

import com.minicloud.service.AuditLogService;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserService.class);

    private static final String USER_ID_PREFIX = "MC-";
    private static final String USER_ID_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int USER_ID_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository userRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public AdminUserService(
            AppUserRepository userRepository,
            EmailService emailService,
            AuditLogService auditLogService) {

        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
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

    // Get all registration requests awaiting admin approval.
    @Transactional(readOnly = true)
    public List<RegistrationRequestAdminResponse> listPendingRequests() {
        return userRepository
                .findByStatusOrderByCreatedAtAsc("PENDING")
                .stream()
                .map(this::toRegistrationRequestResponse)
                .toList();
    }

    // Approve a pending registration request.

    @Transactional
    public RegistrationRequestAdminResponse approveRegistration(
            Long userId, Long adminId) {

        AppUser user = getUserForUpdate(userId);
        AppUser admin = getUser(adminId);

        validatePendingRequest(user);

        String uniqueUserId = generateUniqueUserId();
        user.setUniqueUserId(uniqueUserId);
        user.setStatus("ACTIVE");

        auditLogService.record(
                admin.getId(),
                admin.getUsername(),
                admin.getUniqueUserId(),
                "USER_APPROVED",
                "USER",
                user.getId(),
                uniqueUserId,
                "Administrator approved registration for user "
                        + user.getUsername() + ".");

        String recipientEmail = user.getEmail();
        String recipientName = user.getFullName();
        String approvedUserId = uniqueUserId;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        try {
                            sendApprovalEmail(
                                    recipientEmail,
                                    recipientName,
                                    approvedUserId);
                        } catch (Exception exception) {
                            logger.error(
                                    "Account approved, but approval email "
                                            + "could not be sent to {}",
                                    recipientEmail,
                                    exception);
                        }
                    }
                });

        return toRegistrationRequestResponse(user);
    }

    // Reject a pending registration request.
    @Transactional
    public RegistrationRequestAdminResponse rejectRegistration(
            Long userId, Long adminId) {

        AppUser user = getUserForUpdate(userId);
        AppUser admin = getUser(adminId);

        validatePendingRequest(user);

        user.setStatus("REJECTED");

        auditLogService.record(
                admin.getId(),
                admin.getUsername(),
                admin.getUniqueUserId(),
                "USER_REJECTED",
                "USER",
                user.getId(),
                user.getUniqueUserId(),
                "Administrator rejected registration for user "
                        + user.getUsername() + ".");

        return toRegistrationRequestResponse(user);
    }

    @Transactional
    public AdminUserResponse suspendUser(Long userId, Long adminId) {

        AppUser user = getUserForUpdate(userId);
        AppUser admin = getUser(adminId);

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

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only active users can be suspended.");
        }

        user.setStatus("SUSPENDED");

        auditLogService.record(
                admin.getId(),
                admin.getUsername(),
                admin.getUniqueUserId(),
                "USER_SUSPENDED",
                "USER",
                user.getId(),
                user.getUniqueUserId(),
                "Administrator suspended user " + user.getUsername() + ".");

        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse reactivateUser(Long userId, Long adminId) {

        AppUser user = getUserForUpdate(userId);
        AppUser admin = getUser(adminId);

        if (!"SUSPENDED".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only suspended users can be reactivated.");
        }

        user.setStatus("ACTIVE");

        auditLogService.record(
                admin.getId(),
                admin.getUsername(),
                admin.getUniqueUserId(),
                "USER_REACTIVATED",
                "USER",
                user.getId(),
                user.getUniqueUserId(),
                "Administrator reactivated user " + user.getUsername() + ".");

        return toResponse(user);
    }

    // Generate an ID such as MC-A7K92P4X.
    private String generateUniqueUserId() {

        String uniqueUserId;

        do {
            StringBuilder randomPart = new StringBuilder();

            for (int i = 0; i < USER_ID_LENGTH; i++) {
                int index = RANDOM.nextInt(USER_ID_CHARACTERS.length());

                randomPart.append(USER_ID_CHARACTERS.charAt(index));
            }

            uniqueUserId = USER_ID_PREFIX + randomPart;

        } while (userRepository.existsByUniqueUserId(uniqueUserId));

        return uniqueUserId;
    }

    // Send account approval confirmation.
    private void sendApprovalEmail(
            String recipientEmail,
            String recipientName,
            String uniqueUserId) {
        String displayName = recipientName == null || recipientName.isBlank()
                ? "User"
                : recipientName;

        String subject = "Your Mini Private Cloud account has been approved!";

        String body = """
                Hello %s,

                Good news! Your registration request for Mini Private Cloud
                has been approved by the administrator.

                Your account is now ACTIVE.

                Your permanent Mini Private Cloud User ID is:

                %s

                Please keep this ID for your future reference.

                You can now sign in to Mini Private Cloud using your
                registered username or email and password.

                Welcome to Mini Private Cloud!

                Regards,
                Mini Private Cloud Administration Team
                """.formatted(displayName, uniqueUserId);

        emailService.sendEmail(recipientEmail, subject, body);
    }

    private void validatePendingRequest(AppUser user) {
        if (!"PENDING".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This registration request is no longer pending.");
        }

        if ("ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Admin accounts cannot be processed as registration requests.");
        }
    }

    private AppUser getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."));
    }

    private AppUser getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
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

    private RegistrationRequestAdminResponse toRegistrationRequestResponse(
            AppUser user) {
        return new RegistrationRequestAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getOrganization(),
                user.getAccessReason(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
