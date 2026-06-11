package com.hien.marketplace.domain.audit;

import com.hien.marketplace.domain.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity cho audit log. Ghi lại mọi thay đổi quan trọng trên data.
 * JSONB old_values/new_values cho phép lưu flexible data mà không cần thay đổi schema.
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

    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues; // JSON string của giá trị trước khi thay đổi

    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues; // JSON string của giá trị sau khi thay đổi

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
