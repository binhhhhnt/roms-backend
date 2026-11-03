package com.roms.enums;

/**
 * Các loại hình giảm giá được hỗ trợ khi xuất hóa đơn.
 */
public enum DiscountType {
    NONE,           // Không áp dụng giảm giá
    PERCENTAGE,     // Giảm giá theo tỷ lệ phần trăm (%)
    FIXED_AMOUNT    // Giảm giá theo số tiền cố định (VNĐ)
}

