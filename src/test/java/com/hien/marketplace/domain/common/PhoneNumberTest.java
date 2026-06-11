package com.hien.marketplace.domain.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho PhoneNumber value object.
 * PhoneNumber gom normalization + validation để User không lưu raw String thiếu kiểm soát.
 */
class PhoneNumberTest {

    @Test
    void shouldNormalizePhoneNumber() {
        PhoneNumber phoneNumber = new PhoneNumber("(+84) 912-345-678");

        assertThat(phoneNumber.getValue()).isEqualTo("+84912345678");
    }

    @Test
    void shouldAllowNullPhoneNumber() {
        PhoneNumber phoneNumber = new PhoneNumber(null);

        assertThat(phoneNumber.getValue()).isNull();
        assertThat(phoneNumber.toString()).isEmpty();
    }

    @Test
    void shouldAllowBlankPhoneNumberAsNull() {
        PhoneNumber phoneNumber = new PhoneNumber("   ");

        assertThat(phoneNumber.getValue()).isNull();
    }

    @Test
    void shouldRejectTooShortPhoneNumber() {
        assertThatThrownBy(() -> new PhoneNumber("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void shouldBeEqualWhenNormalizedValueIsSame() {
        PhoneNumber first = new PhoneNumber("(+84) 912-345-678");
        PhoneNumber second = new PhoneNumber("+84912345678");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
