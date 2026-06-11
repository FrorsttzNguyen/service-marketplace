package com.hien.marketplace.domain.user;

/**
 * Enum cho vai trò người dùng.
 *
 * Tại sao dùng Enum thay vì String?
 * - Compiler bắt lỗi nếu gõ sai: UserRole.CUSTOMER ✓ vs "CUSTMER" ✗
 * - Code completion trong IDE
 * - Type safety: method chỉ nhận UserRole, không nhận String bậy
 *
 * Dùng @Enumerated(STRING) trong JPA để lưu tên enum vào DB (không dùng ORDINAL).
 * ORDINAL = số thứ tự (0,1,2) — nếu thêm enum ở giữa thì DB bị sai!
 */
public enum UserRole {
    CUSTOMER,   // Người đặt dịch vụ
    VENDOR,     // Nhà cung cấp dịch vụ
    ADMIN       // Quản trị viên
}
