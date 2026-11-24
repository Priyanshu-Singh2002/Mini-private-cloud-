
package com.minicloud.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_actor_user_id", columnList = "actor_user_id"),
        @Index(name = "idx_audit_action", columnList = "action")
    }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    /**
     * Snapshot of the actor's unique user ID (for example MC-F850BEE1).
     * Null for system-generated events and for actors that have no unique ID.
     * Nullable so Hibernate schema update can add the column without rewriting
     * existing audit_logs rows.
     */
    @Column(name = "actor_unique_id", nullable = true, length = 11)
    private String actorUniqueId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Snapshot of the affected user's unique ID when the entity is a user.
     * Null for file, job, and other non-user entities, and when the user
     * has no unique ID yet. Nullable so existing rows stay unchanged.
     */
    @Column(name = "entity_unique_id", nullable = true, length = 11)
    private String entityUniqueId;

    @Column(length = 1000)
    private String description;

    /**
     * SUCCESS or FAILURE for events that have an outcome.
     * Nullable so audit rows written before this column existed stay valid.
     */
    @Column(length = 16)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorUniqueId() {
        return actorUniqueId;
    }

    public void setActorUniqueId(String actorUniqueId) {
        this.actorUniqueId = actorUniqueId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getEntityUniqueId() {
        return entityUniqueId;
    }

    public void setEntityUniqueId(String entityUniqueId) {
        this.entityUniqueId = entityUniqueId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
