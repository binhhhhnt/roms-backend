package com.roms.service;

import com.roms.dto.request.CancelOrderItemRequest;
import com.roms.dto.request.CancelOrderRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.response.OrderItemResponse;
import com.roms.dto.response.OrderResponse;
import com.roms.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    /**
     * Create a new Order with atomic stock deduction and negative ORDER_CONSUMPTION movement recording.
     * Rejects with 409 Conflict if any ingredient stock is insufficient or menu item is unavailable.
     * Emits STOMP event after successful commit.
     *
     * @param request create order request containing session ID and item lines
     * @return created order response
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * Retrieve order details by ID.
     * Enforces table isolation if requested by a TABLE_ACCOUNT.
     *
     * @param id order ID
     * @return order details
     */
    OrderResponse getOrderById(Long id);

    /**
     * Retrieve orders with optional filters by session ID and status.
     *
     * @param sessionId optional session ID filter
     * @param status optional order status filter
     * @return list of orders
     */
    List<OrderResponse> getOrders(Long sessionId, OrderStatus status);

    /**
     * Cancel an entire Order when in PENDING status.
     * Restores deducted ingredient quantities from recorded consumption movements
     * and appends compensating positive movements. Rejects with 409 Conflict if not PENDING.
     *
     * @param id order ID
     * @param request cancellation request containing reason
     * @return updated order response
     */
    OrderResponse cancelOrder(Long id, CancelOrderRequest request);

    /**
     * Cancel an individual OrderItem when in PENDING status.
     * Restores deducted ingredient quantities and appends compensating movements.
     * Rejects with 409 Conflict if not PENDING.
     *
     * @param orderId order ID
     * @param itemId order item ID
     * @param request cancellation request containing reason
     * @return updated order item response
     */
    OrderItemResponse cancelOrderItem(Long orderId, Long itemId, CancelOrderItemRequest request);
}
