package com.roms.websocket;

import com.roms.dto.event.KitchenOrderEvent;
import com.roms.dto.event.OrderItemStatusChangedEvent;
import com.roms.dto.event.OrderStatusChangedEvent;
import com.roms.entity.MenuItem;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private StompEventPublisher stompEventPublisher;

    private OrderEventPublisher orderEventPublisher;

    private RestaurantTable table;
    private ServiceSession session;
    private Order order;
    private MenuItem menuItem;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        orderEventPublisher = new OrderEventPublisher(stompEventPublisher);

        table = RestaurantTable.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .build();

        session = ServiceSession.builder()
                .id(100L)
                .table(table)
                .openedAt(LocalDateTime.now())
                .build();

        order = Order.builder()
                .id(200L)
                .serviceSession(session)
                .status(OrderStatus.PENDING)
                .build();

        menuItem = MenuItem.builder()
                .id(300L)
                .name("Phở Bò Tái Nạm")
                .price(BigDecimal.valueOf(65000))
                .build();

        orderItem = OrderItem.builder()
                .id(400L)
                .order(order)
                .menuItem(menuItem)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("publishOrderCreated: sends KitchenOrderEvent to kitchen and OrderStatusChangedEvent to session")
    void shouldPublishOrderCreatedEvents() {
        orderEventPublisher.publishOrderCreated(order, 2);

        ArgumentCaptor<KitchenOrderEvent> kitchenCaptor = ArgumentCaptor.forClass(KitchenOrderEvent.class);
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), kitchenCaptor.capture());
        KitchenOrderEvent kitchenEvent = kitchenCaptor.getValue();
        assertThat(kitchenEvent.getOrderId()).isEqualTo(200L);
        assertThat(kitchenEvent.getSessionId()).isEqualTo(100L);
        assertThat(kitchenEvent.getTableId()).isEqualTo(1L);
        assertThat(kitchenEvent.getTableName()).isEqualTo("T01");
        assertThat(kitchenEvent.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(kitchenEvent.getTotalItems()).isEqualTo(2);
        assertThat(kitchenEvent.getOccurredAt()).isNotNull();

        ArgumentCaptor<OrderStatusChangedEvent> sessionCaptor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.sessionOrders(100L)), sessionCaptor.capture());
        OrderStatusChangedEvent sessionEvent = sessionCaptor.getValue();
        assertThat(sessionEvent.getOrderId()).isEqualTo(200L);
        assertThat(sessionEvent.getSessionId()).isEqualTo(100L);
        assertThat(sessionEvent.getTableId()).isEqualTo(1L);
        assertThat(sessionEvent.getOldStatus()).isNull();
        assertThat(sessionEvent.getNewStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(sessionEvent.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("publishOrderCreated: handles order without session gracefully")
    void shouldPublishOrderCreatedWithoutSession() {
        Order standaloneOrder = Order.builder()
                .id(201L)
                .serviceSession(null)
                .status(OrderStatus.PENDING)
                .build();

        orderEventPublisher.publishOrderCreated(standaloneOrder, 1);

        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), any(KitchenOrderEvent.class));
        verify(stompEventPublisher, never()).publishAfterCommit(startsWith("/topic/session/"), any());
    }

    @Test
    @DisplayName("publishOrderStatusChanged: sends OrderStatusChangedEvent to kitchen and session")
    void shouldPublishOrderStatusChangedEvents() {
        order.setStatus(OrderStatus.COOKING);
        orderEventPublisher.publishOrderStatusChanged(order, OrderStatus.PENDING, OrderStatus.COOKING);

        ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), captor.capture());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.sessionOrders(100L)), captor.capture());

        for (OrderStatusChangedEvent event : captor.getAllValues()) {
            assertThat(event.getOrderId()).isEqualTo(200L);
            assertThat(event.getSessionId()).isEqualTo(100L);
            assertThat(event.getTableId()).isEqualTo(1L);
            assertThat(event.getOldStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(event.getNewStatus()).isEqualTo(OrderStatus.COOKING);
            assertThat(event.getOccurredAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("publishOrderStatusChanged: handles order without session gracefully")
    void shouldPublishOrderStatusChangedWithoutSession() {
        Order standaloneOrder = Order.builder()
                .id(202L)
                .serviceSession(null)
                .status(OrderStatus.CANCELLED)
                .build();

        orderEventPublisher.publishOrderStatusChanged(standaloneOrder, OrderStatus.PENDING, OrderStatus.CANCELLED);

        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), any(OrderStatusChangedEvent.class));
        verify(stompEventPublisher, never()).publishAfterCommit(startsWith("/topic/session/"), any());
    }

    @Test
    @DisplayName("publishOrderItemStatusChanged: sends OrderItemStatusChangedEvent to kitchen and session")
    void shouldPublishOrderItemStatusChangedEvents() {
        orderItem.setStatus(OrderItemStatus.COOKING);
        orderEventPublisher.publishOrderItemStatusChanged(orderItem, OrderItemStatus.PENDING, OrderItemStatus.COOKING);

        ArgumentCaptor<OrderItemStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderItemStatusChangedEvent.class);
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), captor.capture());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.sessionOrders(100L)), captor.capture());

        for (OrderItemStatusChangedEvent event : captor.getAllValues()) {
            assertThat(event.getOrderItemId()).isEqualTo(400L);
            assertThat(event.getOrderId()).isEqualTo(200L);
            assertThat(event.getSessionId()).isEqualTo(100L);
            assertThat(event.getMenuItemId()).isEqualTo(300L);
            assertThat(event.getItemName()).isEqualTo("Phở Bò Tái Nạm");
            assertThat(event.getOldStatus()).isEqualTo(OrderItemStatus.PENDING);
            assertThat(event.getNewStatus()).isEqualTo(OrderItemStatus.COOKING);
            assertThat(event.getOccurredAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("publishOrderItemStatusChanged: handles item without session or menuItem gracefully")
    void shouldPublishOrderItemStatusChangedWithNullSessionOrMenuItem() {
        OrderItem bareItem = OrderItem.builder()
                .id(401L)
                .order(null)
                .menuItem(null)
                .quantity(1)
                .unitPrice(BigDecimal.TEN)
                .status(OrderItemStatus.CANCELLED)
                .build();

        orderEventPublisher.publishOrderItemStatusChanged(bareItem, OrderItemStatus.PENDING, OrderItemStatus.CANCELLED);

        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.kitchenOrders()), any(OrderItemStatusChangedEvent.class));
        verify(stompEventPublisher, never()).publishAfterCommit(startsWith("/topic/session/"), any());
    }
}
