package com.hien.marketplace.domain.booking;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.common.TimeSlot;
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
 * Double-booking prevention khi TẠO booking:
 *   - UNIQUE(service_id, booking_date, start_time) là lớp chặn cuối cùng ở database.
 *   - @Version dùng cho concurrent UPDATE trên cùng booking row sau khi booking đã tồn tại.
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

    // TimeSlot gom start/end thành một domain concept để constructor luôn validate start < end.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "startTime", column = @Column(name = "start_time", nullable = false)),
        @AttributeOverride(name = "endTime", column = @Column(name = "end_time", nullable = false))
    })
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Embedded
    @AttributeOverride(name = "amountCents", column = @Column(name = "total_price_cents", nullable = false))
    private Money totalPrice;

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
        this.timeSlot = new TimeSlot(startTime, endTime);
        this.totalPrice = totalPrice;
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
    // Tất cả method đều đi qua changeStatus() để không method nào quên validate current status.
    // BookingStatus enum chứa rules — entity truyền trạng thái hiện tại vào rules đó.

    public void confirm(User changedBy) {
        changeStatus(BookingStatus.CONFIRMED, changedBy, "Vendor confirmed booking");
    }

    public void start(User changedBy) {
        changeStatus(BookingStatus.IN_PROGRESS, changedBy, "Service started");
    }

    public void complete(User changedBy) {
        changeStatus(BookingStatus.COMPLETED, changedBy, "Service completed");
    }

    public void cancel(User changedBy, String reason) {
        changeStatus(BookingStatus.CANCELLED, changedBy, reason);
    }

    private void changeStatus(BookingStatus newStatus, User changedBy, String reason) {
        BookingStatus oldStatus = this.status;
        // Validate từ trạng thái THẬT của entity, không hard-code trạng thái mong muốn.
        oldStatus.throwIfInvalidTransition(newStatus);
        this.status = newStatus;
        this.statusHistory.add(new BookingStatusHistory(this, oldStatus, newStatus, changedBy, reason));
    }

    // === Getters ===

    public Long getId() { return id; }
    public ServiceEntity getService() { return service; }
    public User getCustomer() { return customer; }
    public Vendor getVendor() { return vendor; }
    public LocalDate getBookingDate() { return bookingDate; }
    public LocalTime getStartTime() { return timeSlot.getStartTime(); }
    public LocalTime getEndTime() { return timeSlot.getEndTime(); }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public BookingStatus getStatus() { return status; }
    public Money getTotalPrice() { return totalPrice; }
    public String getNotes() { return notes; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<BookingStatusHistory> getStatusHistory() { return statusHistory; }

    public void setNotes(String notes) { this.notes = notes; }
}
