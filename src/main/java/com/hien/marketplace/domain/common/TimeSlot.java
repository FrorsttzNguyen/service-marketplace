package com.hien.marketplace.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Value Object cho khung giờ (time slot).
 *
 * Dùng cho Booking (start_time, end_time) và ServiceAvailability.
 * Đảm bảo start_time luôn trước end_time — business rule được enforce ở constructor.
 */
@Embeddable
public class TimeSlot {

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    protected TimeSlot() {
    }

    public TimeSlot(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time cannot be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                "Start time must be before end time. Given: " + startTime + " - " + endTime
            );
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    /**
     * Tính số phút của time slot.
     * Ví dụ: 09:00 - 10:30 = 90 phút.
     */
    public long toMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot timeSlot)) return false;
        return Objects.equals(startTime, timeSlot.startTime)
                && Objects.equals(endTime, timeSlot.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }

    @Override
    public String toString() {
        return "TimeSlot{" + startTime + " - " + endTime + "}";
    }
}
