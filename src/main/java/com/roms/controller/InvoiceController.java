package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.CheckoutPreviewRequest;
import com.roms.dto.request.CheckoutRequest;
import com.roms.dto.response.CheckoutPreviewResponse;
import com.roms.dto.response.InvoiceResponse;
import com.roms.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Xem trước thông tin tạm tính, mức giảm giá và số tiền phải thu của bàn trước khi xuất hóa đơn.
     * Quyền: OWNER, SERVICE_STAFF
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> previewCheckout(
            @Valid @RequestBody CheckoutPreviewRequest request) {
        CheckoutPreviewResponse preview = invoiceService.previewCheckout(request);
        return ResponseEntity.ok(ApiResponse.success("Checkout preview generated successfully", preview));
    }

    /**
     * Xuất hóa đơn chính thức, áp dụng giảm giá và đóng phiên phục vụ (giải phóng bàn).
     * Quyền: OWNER, SERVICE_STAFF
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {
        InvoiceResponse invoice = invoiceService.checkoutAndCloseSession(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice created and session closed successfully", invoice));
    }

    /**
     * Lấy chi tiết hóa đơn theo ID hóa đơn (dùng để in lại hoặc kiểm tra thông tin).
     * Quyền: OWNER, SERVICE_STAFF, TABLE_ACCOUNT (chỉ xem được hóa đơn của bàn mình)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceById(@PathVariable Long id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    /**
     * Lấy hóa đơn của phiên phục vụ chỉ định.
     * Quyền: OWNER, SERVICE_STAFF, TABLE_ACCOUNT (chỉ xem được hóa đơn của bàn mình)
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceBySessionId(@PathVariable Long sessionId) {
        InvoiceResponse invoice = invoiceService.getInvoiceBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    /**
     * Tra cứu danh sách lịch sử hóa đơn phân trang, hỗ trợ lọc theo bàn và khoảng thời gian.
     * Quyền: OWNER, SERVICE_STAFF
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> getInvoices(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.length >= 2) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sort[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortOrder = Sort.by(direction, sort[0]);
        } else if (sort.length == 1) {
            sortOrder = Sort.by(Sort.Direction.ASC, sort[0]);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), sortOrder);
        PageResponse<InvoiceResponse> invoices = invoiceService.getInvoices(tableId, startDate, endDate, pageable);

        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
    }
}

