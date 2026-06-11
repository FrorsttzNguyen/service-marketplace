package com.hien.marketplace.domain.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho Money value object.
 * Money là domain concept quan trọng nhất — sai tiền = bug nghiêm trọng.
 */
class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmount() {
        Money money = Money.of(1999); // $19.99
        assertThat(money.getAmountCents()).isEqualTo(1999);
    }

    @Test
    void shouldCreateZeroMoney() {
        Money money = Money.of(0);
        assertThat(money.getAmountCents()).isEqualTo(0);
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldAddTwoAmounts() {
        Money a = Money.of(1000);
        Money b = Money.of(500);
        assertThat(a.add(b)).isEqualTo(Money.of(1500));
    }

    @Test
    void shouldSubtractTwoAmounts() {
        Money a = Money.of(1000);
        Money b = Money.of(300);
        assertThat(a.subtract(b)).isEqualTo(Money.of(700));
    }

    @Test
    void shouldRejectSubtractResultingInNegative() {
        Money a = Money.of(100);
        Money b = Money.of(200);
        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMultiplyByInteger() {
        Money price = Money.of(5000);
        assertThat(price.multiply(3)).isEqualTo(Money.of(15000));
    }

    @Test
    void shouldMultiplyByDecimal() {
        Money price = Money.of(10000); // $100.00
        Money result = price.multiply(new BigDecimal("1.5")); // × 1.5
        assertThat(result).isEqualTo(Money.of(15000)); // $150.00
    }

    @Test
    void shouldBeImmutable() {
        Money original = Money.of(1000);
        Money added = original.add(Money.of(500));
        // Original không thay đổi
        assertThat(original.getAmountCents()).isEqualTo(1000);
        assertThat(added.getAmountCents()).isEqualTo(1500);
    }

    @Test
    void shouldConvertToBigDecimal() {
        Money money = Money.of(1999);
        assertThat(money.toBigDecimal()).isEqualByComparingTo(new BigDecimal("19.99"));
    }

    @Test
    void shouldBeEqualWhenSameAmount() {
        // Value Object equality: hai Money bằng nhau nếu cùng amountCents
        Money a = Money.of(1000);
        Money b = Money.of(1000);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentAmount() {
        Money a = Money.of(1000);
        Money b = Money.of(2000);
        assertThat(a).isNotEqualTo(b);
    }
}
