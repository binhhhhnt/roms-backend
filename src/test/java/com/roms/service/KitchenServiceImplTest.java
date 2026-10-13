package com.roms.service;

import com.roms.dto.request.UpdateOrderItemStatusRequest;
import com.roms.dto.response.KitchenOrderResponse;
import com.roms.dto.response.OrderItemResponse;
import com.roms.entity.MenuItem;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.entity.ServiceSession;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.KitchenServiceImpl;
import com.roms.websocket.OrderEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KitchenServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private KitchenServiceImpl kitchenService;

    private Order testOrder;
    private OrderItem testItem1;
    private OrderItem testItem2;

    @BeforeEach
    void setUp() {
        ServiceSession session = ServiceSession.builder()
                .id(100L)
                .build();

        testOrder = Order.builder()
                .id(10L)
                .serviceSession(session)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        MenuItem pho = MenuItem.builder()
                .id(1L)
                .name("Phở Bò Tái Nạm")
                .price(BigDecimal.valueOf(65000))
                .build();

        testItem1 = OrderItem.builder()
                .id(101L)
                .order(testOrder)
                .menuItem(pho)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();

        testItem2 = OrderItem.builder()
                .id(102L)
                .order(testOrder)
                .menuItem(pho)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(5L)
                .username("chef")
                .role(role)
                .active(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("acceptOrder: transitions PENDING order to COOKING and emits STOMP (no stock deducted)")
    void shouldAcceptOrderSuccessfully() {
        authenticate(UserRole.KITCHEN_STAFF);

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdWithMenuItem(10L)).thenReturn(List.of(testItem1, testItem2));

        KitchenOrderResponse response = kitchenService.acceptOrder(10L);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.COOKING);
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.COOKING);
        verify(orderRepository).save(testOrder);
        verify(orderEventPublisher).publishOrderStatusChanged(testOrder, OrderStatus.PENDING, OrderStatus.COOKING);
    }

    @Test
    @DisplayName("acceptOrder: throws 409 Conflict if order is already COOKING")
    void shouldThrowConflictWhenOrderAlreadyCooking() {
        authenticate(UserRole.KITCHEN_STAFF);
        testOrder.setStatus(OrderStatus.COOKING);

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> kitchenService.acceptOrder(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be accepted");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptOrder: throws 403 Forbidden if SERVICE_STAFF attempts to accept order")
    void shouldThrowForbiddenWhenServiceStaffAcceptsOrder() {
        authenticate(UserRole.SERVICE_STAFF);

        assertThatThrownBy(() -> kitchenService.acceptOrder(10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only KITCHEN_STAFF or OWNER can accept orders");
    }

    @Test
    @DisplayName("updateOrderItemStatus: PENDING -> COOKING advances parent order from PENDING to COOKING")
    void shouldAdvanceParentOrderToCookingWhenItemStartsCooking() {
        authenticate(UserRole.KITCHEN_STAFF);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(testItem1, testItem2));

        OrderItemResponse response = kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING));

        assertThat(response.getStatus()).isEqualTo(OrderItemStatus.COOKING);
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.COOKING);
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("updateOrderItemStatus: COOKING -> READY transitions successfully")
    void shouldTransitionCookingToReady() {
        authenticate(UserRole.KITCHEN_STAFF);
        testItem1.setStatus(OrderItemStatus.COOKING);
        testOrder.setStatus(OrderStatus.COOKING);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(testItem1, testItem2));

        OrderItemResponse response = kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.READY));

        assertThat(response.getStatus()).isEqualTo(OrderItemStatus.READY);
    }

    @Test
    @DisplayName("updateOrderItemStatus: READY -> COMPLETED by SERVICE_STAFF completes order when all items done")
    void shouldAutoCompleteOrderWhenAllNonCancelledItemsAreCompleted() {
        authenticate(UserRole.SERVICE_STAFF);
        testItem1.setStatus(OrderItemStatus.READY);
        testItem2.setStatus(OrderItemStatus.COMPLETED); // Item 2 already completed
        testOrder.setStatus(OrderStatus.COOKING);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem i = invocation.getArgument(0);
            i.setStatus(OrderItemStatus.COMPLETED);
            return i;
        });
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(testItem1, testItem2));

        OrderItemResponse response = kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED));

        assertThat(response.getStatus()).isEqualTo(OrderItemStatus.COMPLETED);
        // Parent order must automatically transition to COMPLETED!
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("updateOrderItemStatus: throws 403 Forbidden if SERVICE_STAFF tries to start cooking")
    void shouldThrowForbiddenWhenServiceStaffStartsCooking() {
        authenticate(UserRole.SERVICE_STAFF);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));

        assertThatThrownBy(() -> kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only KITCHEN_STAFF or OWNER can start cooking");
    }

    @Test
    @DisplayName("updateOrderItemStatus: throws 409 Conflict on invalid transition (e.g. PENDING -> COMPLETED)")
    void shouldThrowConflictOnInvalidTransition() {
        authenticate(UserRole.KITCHEN_STAFF);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));

        assertThatThrownBy(() -> kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid status transition from PENDING to COMPLETED");
    }

    @Test
    @DisplayName("acceptOrder: throws 409 Conflict if order is already COMPLETED")
    void shouldThrowConflictWhenAcceptingCompletedOrder() {
        authenticate(UserRole.KITCHEN_STAFF);
        testOrder.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> kitchenService.acceptOrder(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be accepted");
    }

    @Test
    @DisplayName("updateOrderItemStatus: mixed order with 1 COMPLETED and 1 CANCELLED -> parent order completes")
    void shouldAutoCompleteOrderWithMixedCompletedAndCancelledItems() {
        authenticate(UserRole.SERVICE_STAFF);
        testItem1.setStatus(OrderItemStatus.READY);
        testItem2.setStatus(OrderItemStatus.CANCELLED);
        testOrder.setStatus(OrderStatus.COOKING);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem i = invocation.getArgument(0);
            i.setStatus(OrderItemStatus.COMPLETED);
            return i;
        });
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(testItem1, testItem2));

        OrderItemResponse response = kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED));

        assertThat(response.getStatus()).isEqualTo(OrderItemStatus.COMPLETED);
        // Because item 2 was CANCELLED and item 1 is COMPLETED, all active items are done -> order COMPLETED
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("updateOrderItemStatus: throws 409 Conflict on invalid backward transition (READY -> COOKING)")
    void shouldThrowConflictOnBackwardTransition() {
        authenticate(UserRole.KITCHEN_STAFF);
        testItem1.setStatus(OrderItemStatus.READY);

        when(orderItemRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(testItem1));

        assertThatThrownBy(() -> kitchenService.updateOrderItemStatus(10L, 101L,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid status transition from READY to COOKING");
    }
}
