package com.roms.service;

import com.roms.dto.request.UpdateOrderItemStatusRequest;
import com.roms.dto.response.KitchenOrderResponse;
import com.roms.dto.response.OrderItemResponse;
import com.roms.enums.OrderStatus;

import java.util.List;

public interface KitchenService {

    /**
     * Retrieve orders for kitchen workflow, optionally filtered by status (e.g. PENDING, COOKING).
     *
     * @param status optional order status filter
     * @return list of kitchen orders
     */
    List<KitchenOrderResponse> getKitchenOrders(OrderStatus status);

    /**
     * Retrieve a specific kitchen order by ID.
     *
     * @param id order ID
     * @return kitchen order details
     */
    KitchenOrderResponse getKitchenOrderById(Long id);

    /**
     * Accept a PENDING order into COOKING state.
     * Must NOT deduct ingredients again (already deducted at order creation in BE-010).
     *
     * @param id order ID
     * @return updated kitchen order response
     */
    KitchenOrderResponse acceptOrder(Long id);

    /**
     * Update an individual OrderItem status (PENDING -> COOKING -> READY -> COMPLETED).
     * Automatically completes parent order when all non-cancelled items are COMPLETED.
     *
     * @param orderId parent order ID
     * @param itemId order item ID
     * @param request status update request
     * @return updated order item response
     */
    OrderItemResponse updateOrderItemStatus(Long orderId, Long itemId, UpdateOrderItemStatusRequest request);
}
