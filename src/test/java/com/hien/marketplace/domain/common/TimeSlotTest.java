package com.hien.marketplace.domain.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    // ================================================================
    // Overlap Detection Tests (Phase 3)
    // ================================================================

    @Nested
    @DisplayName("Overlap Detection")
    class OverlapTests {

        @Test
        @DisplayName("Should detect overlap when slots partially overlap")
        void shouldDetectPartialOverlap() {
            // this:    09:00 - 10:00
            // other:   09:30 - 10:30  → overlaps
            TimeSlot slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
            TimeSlot slot2 = new TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 30));

            assertThat(slot1.overlaps(slot2)).isTrue();
            assertThat(slot2.overlaps(slot1)).isTrue();  // Symmetric
        }

        @Test
        @DisplayName("Should detect overlap when one slot contains another")
        void shouldDetectContainment() {
            // this:    09:00 - 11:00
            // other:   09:30 - 10:30  → overlaps (contained)
            TimeSlot outer = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(11, 0));
            TimeSlot inner = new TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 30));

            assertThat(outer.overlaps(inner)).isTrue();
            assertThat(inner.overlaps(outer)).isTrue();
        }

        @Test
        @DisplayName("Should detect overlap when start times are same")
        void shouldDetectOverlapWhenStartSame() {
            // this:    09:00 - 10:00
            // other:   09:00 - 09:30  → overlaps
            TimeSlot slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
            TimeSlot slot2 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30));

            assertThat(slot1.overlaps(slot2)).isTrue();
        }

        @Test
        @DisplayName("Should NOT detect overlap when slots are adjacent (end == start)")
        void shouldNotDetectAdjacentOverlap() {
            // this:    09:00 - 10:00
            // other:   10:00 - 11:00  → NO overlap (end == start)
            TimeSlot slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
            TimeSlot slot2 = new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0));

            assertThat(slot1.overlaps(slot2)).isFalse();
            assertThat(slot2.overlaps(slot1)).isFalse();
        }

        @Test
        @DisplayName("Should NOT detect overlap when slots are completely separate")
        void shouldNotDetectSeparateSlots() {
            // this:    09:00 - 10:00
            // other:   11:00 - 12:00  → NO overlap
            TimeSlot slot1 = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
            TimeSlot slot2 = new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0));

            assertThat(slot1.overlaps(slot2)).isFalse();
            assertThat(slot2.overlaps(slot1)).isFalse();
        }

        @Test
        @DisplayName("Should return false when other is null")
        void shouldReturnFalseForNull() {
            TimeSlot slot = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));

            assertThat(slot.overlaps(null)).isFalse();
        }
    }
}
