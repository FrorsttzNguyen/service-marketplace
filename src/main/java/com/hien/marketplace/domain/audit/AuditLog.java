package com.hien.marketplace.domain.audit;

import com.hien.marketplace.domain.user.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity cho audit log. Ghi lại mọi thay đổi quan trọng trên data.
 *
 * WHY jsonb type:
 * - PostgreSQL's jsonb allows querying JSON content directly in SQL
 * - Hypersistence Utils provides cross-database compatibility
 * - Works with both PostgreSQL (jsonb) and H2 (text/varchar) for tests
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
     * Old values before change, stored as JSON.
     *
     * WHY Map<String, Object>:
     * - More flexible than String - can query individual fields
     * - JsonType handles serialization/deserialization automatically
     * - Works with PostgreSQL jsonb and H2 text
     */
    @Type(JsonType.class)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    /**
     * New values after change, stored as JSON.
     *
     * WHY Map<String, Object>:
     * - More flexible than String - can query individual fields
     * - JsonType handles serialization/deserialization automatically
     * - Works with PostgreSQL jsonb and H2 text
     */
    @Type(JsonType.class)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    protected AuditLog() {
    }

    public AuditLog(String entityType, Long entityId, String action,
                     Map<String, Object> oldValues, Map<String, Object> newValues, User performedBy) {
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
    public Map<String, Object> getOldValues() { return oldValues; }
    public Map<String, Object> getNewValues() { return newValues; }
    public User getPerformedBy() { return performedBy; }
    public LocalDateTime getPerformedAt() { return performedAt; }
}
