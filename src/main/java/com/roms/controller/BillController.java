package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.response.BillPreviewResponse;
import com.roms.dto.response.BillResponse;
import com.roms.service.BillService;
import com.roms.service.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;
    private final PrinterService printerService;

    @GetMapping("/sessions/{sessionId}/preview")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<BillPreviewResponse>> getBillPreview(@PathVariable Long sessionId) {
        BillPreviewResponse response = billService.getBillPreview(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tạm tính hóa đơn thành công", response));
    }

    @PostMapping("/sessions/{sessionId}/checkout")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BillResponse>> checkout(
            @PathVariable Long sessionId,
            @Valid @RequestBody CheckoutBillRequest request) {
        BillResponse response = billService.checkout(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thanh toán và tạo hóa đơn thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BillResponse>> getBillById(@PathVariable Long id) {
        BillResponse response = billService.getBillById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hóa đơn thành công", response));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BillResponse>> getBillBySessionId(@PathVariable Long sessionId) {
        BillResponse response = billService.getBillBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hóa đơn theo phiên thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Page<BillResponse>>> getBills(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<BillResponse> response = billService.getBills(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hóa đơn thành công", response));
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> printBill(@PathVariable Long id) {
        printerService.printBill(id);
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu in hóa đơn đã được gửi tới máy in", null));
    }

    @PostMapping("/sessions/{sessionId}/print-proforma")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> printProforma(@PathVariable Long sessionId) {
        printerService.printProforma(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu in phiếu tạm tính đã được gửi tới máy in", null));
    }
}
