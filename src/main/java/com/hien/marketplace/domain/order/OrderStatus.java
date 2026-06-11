package com.hien.marketplace.domain.order;

public enum OrderStatus {
    CREATED,            // Vừa tạo, chưa thanh toán
    PENDING_PAYMENT,    // Đang chờ thanh toán
    PAID,               // Đã thanh toán thành công
    FULFILLED,          // Đã hoàn thành dịch vụ
    CANCELLED,          // Đã hủy
    REFUNDED            // Đã hoàn tiền
}
