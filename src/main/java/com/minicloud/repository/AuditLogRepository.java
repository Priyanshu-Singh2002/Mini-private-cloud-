
package com.minicloud.repository;

import com.minicloud.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByActorUserIdOrderByCreatedAtDesc(
            Long actorUserId
    );

    List<AuditLog> findByActionOrderByCreatedAtDesc(
            String action
    );

    Optional<AuditLog> findFirstByOrderByCreatedAtDesc();

    @Query("""
            select distinct a.action
            from AuditLog a
            where a.action is not null
            order by a.action
            """)
    List<String> findDistinctActions();

    @Query("""
            select distinct a.entityType
            from AuditLog a
            where a.entityType is not null
            order by a.entityType
            """)
    List<String> findDistinctEntityTypes();
}
