package com.hien.marketplace.domain.service;

import com.hien.marketplace.domain.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho PricingType strategy pattern.
 * Đảm bảo mỗi loại pricing tính đúng giá.
 */
class PricingTypeTest {

    @Test
    void fixedPriceReturnsBasePrice() {
        Money basePrice = Money.of(50000); // $500.00

        Money result = PricingType.FIXED.calculatePrice(basePrice, 90);

        // FIXED: giá không đổi dù thời gian bất kỳ
        assertThat(result).isEqualTo(Money.of(50000));
    }

    @Test
    void fixedPriceIgnoresDuration() {
        Money basePrice = Money.of(10000);

        assertThat(PricingType.FIXED.calculatePrice(basePrice, 30))
                .isEqualTo(PricingType.FIXED.calculatePrice(basePrice, 120));
    }

    @Test
    void hourlyPriceFor60Minutes() {
        Money basePricePerHour = Money.of(50000); // $500/giờ

        Money result = PricingType.HOURLY.calculatePrice(basePricePerHour, 60);

        // 60 phút = 1 giờ
        assertThat(result).isEqualTo(Money.of(50000));
    }

    @Test
    void hourlyPriceRoundsUp() {
        Money basePricePerHour = Money.of(50000);

        // 90 phút = làm tròn lên 2 giờ
        Money result = PricingType.HOURLY.calculatePrice(basePricePerHour, 90);

        assertThat(result).isEqualTo(Money.of(100000));
    }

    @Test
    void hourlyPriceFor1Minute() {
        Money basePricePerHour = Money.of(50000);

        // 1 phút = làm tròn lên 1 giờ
        Money result = PricingType.HOURLY.calculatePrice(basePricePerHour, 1);

        assertThat(result).isEqualTo(Money.of(50000));
    }

    @Test
    void hourlyPriceFor120Minutes() {
        Money basePricePerHour = Money.of(50000);

        // 120 phút = chính xác 2 giờ
        Money result = PricingType.HOURLY.calculatePrice(basePricePerHour, 120);

        assertThat(result).isEqualTo(Money.of(100000));
    }

    @Test
    void variablePriceReturnsBasePriceForNow() {
        Money basePrice = Money.of(30000);

        // VARIABLE chưa implement (Phase 3), tạm trả basePrice
        assertThat(PricingType.VARIABLE.calculatePrice(basePrice, 60))
                .isEqualTo(Money.of(30000));
    }
}
