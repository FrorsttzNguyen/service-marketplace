package com.hien.marketplace.domain.payment;

import com.hien.marketplace.domain.common.Money;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity cho hoàn tiền. Một Payment có thể có nhiều Refund (partial refunds).
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "amount_cents", nullable = false))
    private Money amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "stripe_refund_id", length = 255)
    private String stripeRefundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Refund() {
    }

    public Refund(Payment payment, Money amount, String reason) {
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void markAsSucceeded() { this.status = RefundStatus.SUCCEEDED; }
    public void markAsFailed() { this.status = RefundStatus.FAILED; }

    public Money getAmount() { return amount; }

    // Getters
    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getStripeRefundId() { return stripeRefundId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStripeRefundId(String stripeRefundId) { this.stripeRefundId = stripeRefundId; }
}
