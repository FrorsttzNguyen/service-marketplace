package com.hien.marketplace.domain.service;

import com.hien.marketplace.domain.common.Money;

/**
 * Enum cho loại giá dịch vụ — minh họa Strategy Pattern.
 *
 * Mỗi enum value override calculatePrice() với logic riêng:
 * - FIXED: trả về giá gốc, bất kể thời gian
 * - HOURLY: nhân giá theo số giờ (làm tròn lên)
 * - VARIABLE: để sau (Phase 3), hiện trả về giá gốc
 *
 * Tại sao Strategy Pattern bằng enum?
 * - Thêm loại giá mới = thêm enum value, KHÔNG sửa code cũ (Open/Closed Principle)
 * - Caller chỉ cần gọi pricingType.calculatePrice(), không cần if-else
 * - Enum-based strategy đơn giản hơn interface + class cho case này
 */
public enum PricingType {

    FIXED {
        @Override
        public Money calculatePrice(Money basePrice, int durationMinutes) {
            return basePrice; // Giá cố định, thời gian không ảnh hưởng
        }
    },

    HOURLY {
        @Override
        public Money calculatePrice(Money basePrice, int durationMinutes) {
            // basePrice = giá mỗi giờ. Tính số giờ, làm tròn lên.
            // Ví dụ: 90 phút → 2 giờ, 60 phút → 1 giờ
            int hours = (durationMinutes + 59) / 60;
            return basePrice.multiply(hours);
        }
    },

    VARIABLE {
        @Override
        public Money calculatePrice(Money basePrice, int durationMinutes) {
            // Variable pricing chưa implement (Phase 3). Tạm dùng giá gốc.
            return basePrice;
        }
    };

    /**
     * Tính giá cuối cùng dựa trên loại pricing và thời lượng.
     *
     * @param basePrice        giá cơ bản (cents)
     * @param durationMinutes  thời lượng dịch vụ (phút)
     * @return giá cuối cùng
     */
    public abstract Money calculatePrice(Money basePrice, int durationMinutes);
}
