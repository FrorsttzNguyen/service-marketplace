package com.hien.marketplace.domain.common;

import java.util.Objects;

/**
 * Value Object cho số điện thoại.
 *
 * Đơn giản wrapper cho String — nhưng tách riêng để:
 * - Type safety: không truyền nhầm phone number vào field khác
 * - Validation tập trung: nếu cần validate format sau này, sửa ở 1 chỗ
 */
public class PhoneNumber {

    private final String value;

    public PhoneNumber(String value) {
        if (value != null && !value.isBlank()) {
            // Chỉ giữ lại chữ số và dấu + (quốc tế)
            String normalized = value.replaceAll("[^0-9+]", "");
            if (normalized.length() < 7) {
                throw new IllegalArgumentException("Phone number too short: " + value);
            }
            this.value = normalized;
        } else {
            this.value = null;
        }
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhoneNumber that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value != null ? value : "";
    }
}
