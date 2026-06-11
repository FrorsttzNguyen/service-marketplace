package com.hien.marketplace.domain.common;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho TimeSlot value object.
 * TimeSlot bảo vệ invariant: một khung giờ luôn có startTime trước endTime.
 */
class TimeSlotTest {

    @Test
    void shouldCreateValidTimeSlot() {
        TimeSlot timeSlot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 30));

        assertThat(timeSlot.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(timeSlot.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void shouldRejectNullStartTime() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectNullEndTime() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(9, 0), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectEqualStartAndEndTime() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before end time");
    }

    @Test
    void shouldRejectEndTimeBeforeStartTime() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(10, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before end time");
    }

    @Test
    void shouldCalculateDurationInMinutes() {
        TimeSlot timeSlot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 30));

        assertThat(timeSlot.toMinutes()).isEqualTo(90);
    }

    @Test
    void shouldBeEqualWhenStartAndEndAreSame() {
        TimeSlot first = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        TimeSlot second = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
