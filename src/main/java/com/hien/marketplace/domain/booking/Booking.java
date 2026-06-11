package com.hien.marketplace.domain.booking;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.vendor.Vendor;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity cho đặt lịch — bảng quan trọng nhất trong hệ thống.
 *
 * Double-booking prevention (2 lớp):
 *   Layer 1: @Version (optimistic locking) — application level
 *   Layer 2: UNIQUE(service_id, booking_date, start_time) — database level
 *
 * State Machine: status chỉ chuyển đổi theo BookingStatus rules.
 * Không thể chuyển COMPLETED → PENDING — BookingStatus.throwIfInvalidTransition() sẽ reject.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_price_cents", nullable = false)
    private long totalPriceCents;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Optimistic locking: JPA tự động check version khi UPDATE
    // UPDATE WHERE id=? AND version=?. Nếu version đã đổi → OptimisticLockException
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Status history — audit trail cho mọi lần chuyển trạng thái
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    protected Booking() {
    }

    public Booking(ServiceEntity service, User customer, Vendor vendor,
                    LocalDate bookingDate, LocalTime startTime, LocalTime endTime,
                    Money totalPrice) {
        this.service = service;
        this.customer = customer;
        this.vendor = vendor;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalPriceCents = totalPrice.getAmountCents();
        this.status = BookingStatus.PENDING;
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

    // === State Machine transitions ===
    // Mỗi method check xem chuyển đổi có hợp lệ không trước khi thực hiện.
    // BookingStatus enum chứa rules — entity chỉ gọi validate.

    public void confirm(User changedBy) {
        BookingStatus.PENDING.throwIfInvalidTransition(BookingStatus.CONFIRMED);
        changeStatus(BookingStatus.CONFIRMED, changedBy, "Vendor confirmed booking");
    }

    public void start(User changedBy) {
        BookingStatus.CONFIRMED.throwIfInvalidTransition(BookingStatus.IN_PROGRESS);
        changeStatus(BookingStatus.IN_PROGRESS, changedBy, "Service started");
    }

    public void complete(User changedBy) {
        BookingStatus.IN_PROGRESS.throwIfInvalidTransition(BookingStatus.COMPLETED);
        changeStatus(BookingStatus.COMPLETED, changedBy, "Service completed");
    }

    public void cancel(User changedBy, String reason) {
        // Cancel có thể từ PENDING hoặc CONFIRMED
        status.throwIfInvalidTransition(BookingStatus.CANCELLED);
        changeStatus(BookingStatus.CANCELLED, changedBy, reason);
    }

    private void changeStatus(BookingStatus newStatus, User changedBy, String reason) {
        BookingStatus oldStatus = this.status;
        this.status = newStatus;
        this.statusHistory.add(new BookingStatusHistory(this, oldStatus, newStatus, changedBy, reason));
    }

    // === Getters ===

    public Long getId() { return id; }
    public ServiceEntity getService() { return service; }
    public User getCustomer() { return customer; }
    public Vendor getVendor() { return vendor; }
    public LocalDate getBookingDate() { return bookingDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BookingStatus getStatus() { return status; }
    public Money getTotalPrice() { return Money.of(totalPriceCents); }
    public String getNotes() { return notes; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<BookingStatusHistory> getStatusHistory() { return statusHistory; }

    public void setNotes(String notes) { this.notes = notes; }
}
