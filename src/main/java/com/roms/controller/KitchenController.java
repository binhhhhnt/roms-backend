package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.UpdateOrderItemStatusRequest;
import com.roms.dto.response.KitchenOrderResponse;
import com.roms.dto.response.OrderItemResponse;
import com.roms.enums.OrderStatus;
import com.roms.service.KitchenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/kitchen/orders")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService kitchenService;

    @GetMapping
    @PreAuthorize("hasAnyRole('KITCHEN_STAFF', 'SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getOrders(
            @RequestParam(name = "status", required = false) OrderStatus status) {
        List<KitchenOrderResponse> response = kitchenService.getKitchenOrders(status);
        return ResponseEntity.ok(ApiResponse.success("Kitchen orders retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('KITCHEN_STAFF', 'SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> getOrderById(@PathVariable("id") Long id) {
        KitchenOrderResponse response = kitchenService.getKitchenOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Kitchen order retrieved successfully", response));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('KITCHEN_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> acceptOrder(@PathVariable("id") Long id) {
        KitchenOrderResponse response = kitchenService.acceptOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order accepted by kitchen", response));
    }

    @PatchMapping("/{id}/items/{itemId}/status")
    @PreAuthorize("hasAnyRole('KITCHEN_STAFF', 'SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<OrderItemResponse>> updateOrderItemStatus(
            @PathVariable("id") Long id,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody UpdateOrderItemStatusRequest request) {
        OrderItemResponse response = kitchenService.updateOrderItemStatus(id, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Order item status updated successfully", response));
    }
}
