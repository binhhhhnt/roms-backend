package com.roms.websocket;

import com.roms.dto.event.KitchenOrderEvent;
import com.roms.dto.event.OrderItemStatusChangedEvent;
import com.roms.dto.event.OrderStatusChangedEvent;
import com.roms.dto.event.ReadyDishesEvent;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Dedicated event publisher for Order and Kitchen WebSocket STOMP notifications (BE-021, BE-037).
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
            stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(sessionId), sessionEvent);
        }
    }

    /**
     * Publish events when an order's overall status changes (e.g., PENDING -> COOKING -> COMPLETED, or CANCELLED):
     * - OrderStatusChangedEvent to /topic/kitchen/orders
     * - OrderStatusChangedEvent to /topic/session/{sessionId}/orders
     *
     * @param order updated order
     * @param oldStatus previous status
     * @param newStatus updated status
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
            stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(sessionId), event);
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
            stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(sessionId), event);
        }

        if (newStatus == OrderItemStatus.COMPLETED || newStatus == OrderItemStatus.CANCELLED) {
            ReadyDishesEvent removeEvent = ReadyDishesEvent.builder()
                    .orderId(parentOrder != null ? parentOrder.getId() : null)
                    .sessionId(sessionId)
                    .orderItemId(item.getId())
                    .status(newStatus.name())
                    .build();
            stompEventPublisher.publishAfterCommit(StompDestinations.serviceReadyDishes(), removeEvent);
        }
    }

    /**
     * Publish ready dish alert to service staff topic (/topic/service/ready-dishes).
     *
     * @param item ready order item
     */
    public void publishDishReady(OrderItem item) {
        Order parentOrder = item.getOrder();
        Long sessionId = (parentOrder != null && parentOrder.getServiceSession() != null)
                ? parentOrder.getServiceSession().getId() : null;
        Long tableId = (parentOrder != null && parentOrder.getServiceSession() != null && parentOrder.getServiceSession().getTable() != null)
                ? parentOrder.getServiceSession().getTable().getId() : null;
        String tableCode = (parentOrder != null && parentOrder.getServiceSession() != null && parentOrder.getServiceSession().getTable() != null)
                ? parentOrder.getServiceSession().getTable().getTableCode() : null;

        ReadyDishesEvent readyEvent = ReadyDishesEvent.builder()
                .orderId(parentOrder != null ? parentOrder.getId() : null)
                .sessionId(sessionId)
                .tableId(tableId)
                .tableCode(tableCode)
                .orderItemId(item.getId())
                .menuItemName(item.getMenuItem() != null ? item.getMenuItem().getName() : null)
                .quantity(item.getQuantity())
                .note(item.getNote())
                .status(OrderItemStatus.READY.name())
                .readyAt(LocalDateTime.now())
                .build();

        stompEventPublisher.publishAfterCommit(StompDestinations.serviceReadyDishes(), readyEvent);
    }
}
