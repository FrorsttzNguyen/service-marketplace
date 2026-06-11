package com.hien.marketplace.domain.service;

import com.hien.marketplace.domain.common.TimeSlot;
import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * Entity cho khung giờ hoạt động của dịch vụ.
 * Mỗi record = 1 ngày trong tuần (0=Sunday, 6=Saturday).
 *
 * Ví dụ: Spa mở Thứ 2-6 từ 9:00-18:00 → 5 records (day_of_week 1-5).
 */
@Entity
@Table(name = "service_availability")
public class ServiceAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek; // 0=Sunday, 1=Monday, ..., 6=Saturday

    // TimeSlot gom start/end để rule "start phải trước end" chỉ nằm ở một chỗ.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "startTime", column = @Column(name = "start_time", nullable = false)),
        @AttributeOverride(name = "endTime", column = @Column(name = "end_time", nullable = false))
    })
    private TimeSlot timeSlot;

    protected ServiceAvailability() {
    }

    public ServiceAvailability(ServiceEntity service, short dayOfWeek, LocalTime startTime, LocalTime endTime) {
        if (dayOfWeek < 0 || dayOfWeek > 6) {
            throw new IllegalArgumentException("dayOfWeek must be 0-6, got: " + dayOfWeek);
        }
        this.service = service;
        this.dayOfWeek = dayOfWeek;
        this.timeSlot = new TimeSlot(startTime, endTime);
    }

    // Getters
    public Long getId() { return id; }
    public ServiceEntity getService() { return service; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return timeSlot.getStartTime(); }
    public LocalTime getEndTime() { return timeSlot.getEndTime(); }
    public TimeSlot getTimeSlot() { return timeSlot; }
}
