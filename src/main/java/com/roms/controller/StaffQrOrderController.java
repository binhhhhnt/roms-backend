package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.CancelQrOrderRequest;
import com.roms.dto.response.QrOrderRequestResponse;
import com.roms.service.QrOrderingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qr-orders")
@RequiredArgsConstructor
public class StaffQrOrderController {

    private final QrOrderingService qrOrderingService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<List<QrOrderRequestResponse>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success("Pending QR order requests retrieved successfully",
                qrOrderingService.getPendingRequests()));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<QrOrderRequestResponse>> confirmQrOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("QR order request confirmed successfully",
                qrOrderingService.confirmQrOrder(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<QrOrderRequestResponse>> cancelQrOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelQrOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("QR order request cancelled successfully",
                qrOrderingService.cancelQrOrder(id, request)));
    }
}
