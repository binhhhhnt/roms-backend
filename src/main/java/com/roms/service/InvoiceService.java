package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CheckoutPreviewRequest;
import com.roms.dto.request.CheckoutRequest;
import com.roms.dto.response.CheckoutPreviewResponse;
import com.roms.dto.response.InvoiceResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface InvoiceService {

    /**
     * Xem trước thông tin hóa đơn bao gồm tạm tính, mức giảm giá và số tiền phải thanh toán.
     *
     * @param request yêu cầu xem trước chứa sessionId, discountType, discountValue
     * @return CheckoutPreviewResponse thông tin chi tiết xem trước
     */
    CheckoutPreviewResponse previewCheckout(CheckoutPreviewRequest request);

    /**
     * Xuất hóa đơn chính thức, áp dụng giảm giá, lưu hóa đơn, đóng phiên phục vụ
     * và phát sự kiện STOMP giải phóng bàn.
     *
     * @param request yêu cầu thanh toán xuất hóa đơn
     * @return InvoiceResponse chi tiết hóa đơn đã xuất
     */
    InvoiceResponse checkoutAndCloseSession(CheckoutRequest request);

    /**
     * Lấy thông tin chi tiết hóa đơn theo ID hóa đơn.
     *
     * @param id mã ID của hóa đơn
     * @return InvoiceResponse chi tiết hóa đơn
     */
    InvoiceResponse getInvoiceById(Long id);

    /**
     * Lấy thông tin hóa đơn theo mã phiên phục vụ (sessionId).
     *
     * @param sessionId mã ID của phiên phục vụ
     * @return InvoiceResponse chi tiết hóa đơn
     */
    InvoiceResponse getInvoiceBySessionId(Long sessionId);

    /**
     * Lấy danh sách hóa đơn có phân trang và bộ lọc theo bàn, khoảng thời gian.
     *
     * @param tableId mã ID bàn ăn (tùy chọn)
     * @param startDate ngày bắt đầu (tùy chọn)
     * @param endDate ngày kết thúc (tùy chọn)
     * @param pageable thông tin phân trang
     * @return PageResponse<InvoiceResponse> danh sách hóa đơn phân trang
     */
    PageResponse<InvoiceResponse> getInvoices(Long tableId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}

