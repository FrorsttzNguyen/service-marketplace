package com.hien.marketplace.domain.order;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity cho đơn hàng — Aggregate Root cho payment domain.
 *
 * Aggregate Root = điểm vào duy nhất cho nhóm entities (Order → Payment → Refund).
 * Code bên ngoài không truy cập trực tiếp Payment hay Refund,
 * phải thông qua Order.
 *
 * Commission = phí platform thu từ vendor. Ví dụ: 10% của total.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "subtotal_cents", nullable = false))
    private Money subtotal;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "commission_cents", nullable = false))
    private Money commission;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "total_cents", nullable = false))
    private Money total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Order() {
    }

    public Order(User customer, Booking booking, Money subtotal, Money commission) {
        this.customer = customer;
        this.booking = booking;
        this.subtotal = subtotal;
        this.commission = commission;
        this.total = subtotal.add(commission);
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Domain methods
    public void markAsPaid() { this.status = OrderStatus.PAID; }
    public void fulfill() { this.status = OrderStatus.FULFILLED; }
    public void cancel() { this.status = OrderStatus.CANCELLED; }
    public void refund() { this.status = OrderStatus.REFUNDED; }

    public Money getSubtotal() { return subtotal; }
    public Money getCommission() { return commission; }
    public Money getTotal() { return total; }

    // Getters
    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public Booking getBooking() { return booking; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
