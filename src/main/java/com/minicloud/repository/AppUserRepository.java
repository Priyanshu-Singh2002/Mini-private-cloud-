package com.minicloud.repository;

import com.minicloud.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(String role);

    Optional<AppUser> findByUsernameOrEmail(
            String username,
            String email
    );
}