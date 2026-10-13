package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.CancelOrderItemRequest;
import com.roms.dto.request.CancelOrderRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.response.OrderItemResponse;
import com.roms.dto.response.OrderResponse;
import com.roms.enums.OrderStatus;
import com.roms.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TABLE_ACCOUNT', 'SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable("id") Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrders(
            @RequestParam(name = "sessionId", required = false) Long sessionId,
            @RequestParam(name = "status", required = false) OrderStatus status) {
        List<OrderResponse> response = orderService.getOrders(sessionId, status);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TABLE_ACCOUNT', 'SERVICE_STAFF', 'KITCHEN_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable("id") Long id,
            @RequestBody(required = false) CancelOrderRequest request) {
        OrderResponse response = orderService.cancelOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }

    @PostMapping("/{id}/items/{itemId}/cancel")
    @PreAuthorize("hasAnyRole('TABLE_ACCOUNT', 'SERVICE_STAFF', 'KITCHEN_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<OrderItemResponse>> cancelOrderItem(
            @PathVariable("id") Long id,
            @PathVariable("itemId") Long itemId,
            @RequestBody(required = false) CancelOrderItemRequest request) {
        OrderItemResponse response = orderService.cancelOrderItem(id, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Order item cancelled successfully", response));
    }
}
