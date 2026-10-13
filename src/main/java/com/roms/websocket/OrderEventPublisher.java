package com.roms.websocket;

import com.roms.dto.event.KitchenOrderEvent;
import com.roms.dto.event.OrderItemStatusChangedEvent;
import com.roms.dto.event.OrderStatusChangedEvent;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Dedicated event publisher for Order and Kitchen WebSocket STOMP notifications (BE-021).
 * All notifications are published strictly AFTER database transaction commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final StompEventPublisher stompEventPublisher;

    /**
     * Publish events when a new order is placed:
     * - KitchenOrderEvent to /topic/kitchen/orders (notifies kitchen staff)
     * - OrderStatusChangedEvent to /topic/session/{sessionId}/orders (notifies table & service staff)
     *
     * @param order created order
     * @param totalItems number of active items in order
     */
    public void publishOrderCreated(Order order, int totalItems) {
        Long sessionId = order.getServiceSession() != null ? order.getServiceSession().getId() : null;
        Long tableId = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getId() : null;
        String tableName = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getTableCode() : null;

        KitchenOrderEvent kitchenEvent = KitchenOrderEvent.builder()
                .orderId(order.getId())
                .sessionId(sessionId)
                .tableId(tableId)
                .tableName(tableName)
                .status(order.getStatus())
                .totalItems(totalItems)
                .occurredAt(LocalDateTime.now())
                .build();
        stompEventPublisher.publishAfterCommit(StompDestinations.kitchenOrders(), kitchenEvent);

        if (sessionId != null) {
            OrderStatusChangedEvent sessionEvent = OrderStatusChangedEvent.builder()
                    .orderId(order.getId())
                    .sessionId(sessionId)
                    .tableId(tableId)
                    .oldStatus(null)
                    .newStatus(order.getStatus())
                    .occurredAt(LocalDateTime.now())
                    .build();
            stompEventPublisher.publishAfterCommit(StompDestinations.sessionOrders(sessionId), sessionEvent);
        }
    }

    /**
     * Publish events when an order status changes (e.g., accepted into COOKING, cancelled, completed):
     * - OrderStatusChangedEvent to /topic/kitchen/orders
     * - OrderStatusChangedEvent to /topic/session/{sessionId}/orders
     *
     * @param order order whose status changed
     * @param oldStatus previous order status
     * @param newStatus updated order status
     */
    public void publishOrderStatusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        Long sessionId = order.getServiceSession() != null ? order.getServiceSession().getId() : null;
        Long tableId = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getId() : null;

        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .sessionId(sessionId)
                .tableId(tableId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .occurredAt(LocalDateTime.now())
                .build();

        stompEventPublisher.publishAfterCommit(StompDestinations.kitchenOrders(), event);
        if (sessionId != null) {
            stompEventPublisher.publishAfterCommit(StompDestinations.sessionOrders(sessionId), event);
        }
    }

    /**
     * Publish events when an individual order item status changes (e.g., COOKING, READY, COMPLETED, CANCELLED):
     * - OrderItemStatusChangedEvent to /topic/kitchen/orders
     * - OrderItemStatusChangedEvent to /topic/session/{sessionId}/orders
     *
     * @param item order item whose status changed
     * @param oldStatus previous item status
     * @param newStatus updated item status
     */
    public void publishOrderItemStatusChanged(OrderItem item, OrderItemStatus oldStatus, OrderItemStatus newStatus) {
        Order parentOrder = item.getOrder();
        Long sessionId = (parentOrder != null && parentOrder.getServiceSession() != null)
                ? parentOrder.getServiceSession().getId() : null;

        OrderItemStatusChangedEvent event = OrderItemStatusChangedEvent.builder()
                .orderItemId(item.getId())
                .orderId(parentOrder != null ? parentOrder.getId() : null)
                .sessionId(sessionId)
                .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                .itemName(item.getMenuItem() != null ? item.getMenuItem().getName() : null)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .occurredAt(LocalDateTime.now())
                .build();

        stompEventPublisher.publishAfterCommit(StompDestinations.kitchenOrders(), event);
        if (sessionId != null) {
            stompEventPublisher.publishAfterCommit(StompDestinations.sessionOrders(sessionId), event);
        }
    }
}
