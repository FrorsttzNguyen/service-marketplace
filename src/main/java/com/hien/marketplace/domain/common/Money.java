package com.hien.marketplace.domain.common;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object đại diện cho tiền tệ.
 *
 * Tại sao dùng Value Object thay vì long/int?
 * - Encapsulation: không tạo được Money âm (constructor validate)
 * - Type safety: không nhầm lẫn "long priceCents" với "long userId"
 * - Immutable: thread-safe, không sợ ai sửa giá trị sau khi tạo
 * - Domain-rich: logic nghiệp vụ (add, subtract) nằm ngay trong object
 *
 * Tiền lưu dưới dạng cents (long) để tránh lỗi floating-point precision.
 * Ví dụ: $19.99 = 1999 cents. Không bao giờ dùng double/float cho tiền.
 */
@Embeddable
public class Money {

    // Số tiền tính bằng cents. Ví dụ: 1999 = $19.99
    private long amountCents;

    // Constructor không tham số cần cho JPA (@Embeddable)
    // JPA dùng reflection để tạo object, cần no-arg constructor
    protected Money() {
    }

    // Factory method — cách tạo Money duy nhất từ bên ngoài class
    // Dùng static factory thay vì public constructor để dễ đọc: Money.of(1999)
    public static Money of(long amountCents) {
        if (amountCents < 0) {
            throw new IllegalArgumentException(
                "Money amount cannot be negative. Given: " + amountCents
            );
        }
        return new Money(amountCents);
    }

    // Constructor private — bắt buộc đi qua validation
    private Money(long amountCents) {
        this.amountCents = amountCents;
    }

    // === Arithmetic operations — luôn trả về object MỚI (immutable) ===

    public Money add(Money other) {
        return Money.of(this.amountCents + other.amountCents);
    }

    public Money subtract(Money other) {
        return Money.of(this.amountCents - other.amountCents);
    }

    public Money multiply(int multiplier) {
        return Money.of(this.amountCents * multiplier);
    }

    public Money multiply(BigDecimal multiplier) {
        //setScale(0, RoundingMode.HALF_UP) — làm tròn đến số nguyên gần nhất
        long result = BigDecimal.valueOf(this.amountCents)
                .multiply(multiplier)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();
        return Money.of(result);
    }

    // === Getters ===

    public long getAmountCents() {
        return amountCents;
    }

    // Chuyển cents thành dollars để hiển thị (ví dụ: 1999 → 19.99)
    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(amountCents, 2);
    }

    // === equals & hashCode — bắt buộc cho Value Object ===
    // Hai Money bằng nhau nếu amountCents bằng nhau (không phân biệt identity)

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amountCents == money.amountCents;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountCents);
    }

    @Override
    public String toString() {
        return "Money{cents=" + amountCents + "}";
    }
}
