package com.hien.marketplace.domain.audit;

import com.hien.marketplace.domain.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity cho audit log. Ghi lại mọi thay đổi quan trọng trên data.
 *
 * WHY String type for jsonb:
 * - Flyway migration creates real jsonb columns in PostgreSQL
 * - columnDefinition = "text" for H2 compatibility in tests
 * - Production: PostgreSQL jsonb (via Flyway V6 migration)
 * - Tests: H2 text (via ddl-auto=create-drop)
 *
 * Alternative approach for native jsonb support:
 * - Use hypersistence-utils with @Type(JsonType.class) and Map<String, Object>
 * - Requires TestContainers for tests (real PostgreSQL)
 * - See BaseDataJpaTest for TestContainers setup
 *
 * Không bao giờ DELETE audit log — đây là yêu cầu compliance cho financial data.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // "Booking", "Order", "Payment"...

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 20)
    private String action; // "INSERT", "UPDATE", "DELETE"

    /**
     * Old values before change, stored as JSON string.
     *
     * WHY String with columnDefinition = "text":
     * - H2-compatible for tests (H2 doesn't support jsonb type)
     * - PostgreSQL migration V6 creates real jsonb column
     * - When using TestContainers, add @Type(JsonType.class) + Map<String, Object>
     */
    @Column(name = "old_values", columnDefinition = "text")
    private String oldValues;

    /**
     * New values after change, stored as JSON string.
     *
     * WHY String with columnDefinition = "text":
     * - H2-compatible for tests (H2 doesn't support jsonb type)
     * - PostgreSQL migration V6 creates real jsonb column
     * - When using TestContainers, add @Type(JsonType.class) + Map<String, Object>
     */
    @Column(name = "new_values", columnDefinition = "text")
    private String newValues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    protected AuditLog() {
    }

    public AuditLog(String entityType, Long entityId, String action,
                     String oldValues, String newValues, User performedBy) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.performedBy = performedBy;
        this.performedAt = LocalDateTime.now();
    }

    // Getters (no setters — audit logs are immutable)
    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getOldValues() { return oldValues; }
    public String getNewValues() { return newValues; }
    public User getPerformedBy() { return performedBy; }
    public LocalDateTime getPerformedAt() { return performedAt; }
}
