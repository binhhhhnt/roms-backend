package com.roms.service;

import com.roms.dto.request.CancelOrderItemRequest;
import com.roms.dto.request.CancelOrderRequest;
import com.roms.dto.request.CreateOrderItemRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.response.OrderItemResponse;
import com.roms.dto.response.OrderResponse;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.OrderServiceImpl;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ServiceSessionRepository serviceSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private RestaurantTable table1;
    private ServiceSession session1;
    private User tableUser;
    private MenuItem menuItemPho;
    private Ingredient beefIngredient;
    private RecipeItem recipePho;

    @BeforeEach
    void setUp() {
        table1 = RestaurantTable.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .isActive(true)
                .build();

        session1 = ServiceSession.builder()
                .id(100L)
                .table(table1)
                .status(ServiceSessionStatus.ACTIVE)
                .openedAt(LocalDateTime.now())
                .build();

        tableUser = User.builder()
                .id(10L)
                .username("table_t01")
                .role(UserRole.TABLE_ACCOUNT)
                .table(table1)
                .isActive(true)
                .build();

        menuItemPho = MenuItem.builder()
                .id(1L)
                .name("Phở Bò Tái Nạm")
                .price(BigDecimal.valueOf(65000))
                .status(MenuItemStatus.AVAILABLE)
                .build();

        beefIngredient = Ingredient.builder()
                .id(201L)
                .name("Thịt Bò")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(10.0))
                .isActive(true)
                .build();

        recipePho = RecipeItem.builder()
                .id(501L)
                .menuItem(menuItemPho)
                .ingredient(beefIngredient)
                .quantityRequired(BigDecimal.valueOf(0.150)) // 150g beef per dish
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role, Long tableId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(10L)
                .username("test_user")
                .role(role)
                .tableId(tableId)
                .active(true)
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("createOrder: successfully creates order, deducts stock, records negative movements, and publishes STOMP event")
    void shouldCreateOrderSuccessfully() {
        authenticate(UserRole.TABLE_ACCOUNT, 1L);

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1));
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItemPho));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(recipePho));
        when(ingredientRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(beefIngredient));
        when(userRepository.findById(10L)).thenReturn(Optional.of(tableUser));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(901L);
            o.setCreatedAt(LocalDateTime.now());
            o.setUpdatedAt(LocalDateTime.now());
            return o;
        });

        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem oi = invocation.getArgument(0);
            oi.setId(801L);
            oi.setCreatedAt(LocalDateTime.now());
            return oi;
        });

        CreateOrderItemRequest itemReq = CreateOrderItemRequest.builder()
                .menuItemId(1L)
                .quantity(2)
                .note("Ít hành")
                .build();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(itemReq))
                .build();

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(901L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(130000));

        // 2 dishes * 0.150kg = 0.300kg deducted from 10.0kg -> 9.700kg
        assertThat(beefIngredient.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(9.700));
        verify(ingredientRepository).save(beefIngredient);

        // Verify negative ORDER_CONSUMPTION movement
        verify(stockMovementRepository).save(argThat(m ->
                m.getMovementType() == StockMovementType.ORDER_CONSUMPTION &&
                m.getQuantityChange().compareTo(BigDecimal.valueOf(-0.300)) == 0 &&
                m.getBalanceAfter().compareTo(BigDecimal.valueOf(9.700)) == 0
        ));

        // Verify STOMP after-commit events published
        verify(orderEventPublisher).publishOrderCreated(any(Order.class), eq(1));
    }

    @Test
    @DisplayName("createOrder: throws 403 Forbidden if TABLE_ACCOUNT places order for another table")
    void shouldThrowForbiddenWhenTableAccountOrdersForOtherTable() {
        authenticate(UserRole.TABLE_ACCOUNT, 2L); // Table 2 authenticated

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1)); // Session is table 1

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(new CreateOrderItemRequest(1L, 1, null)))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot place orders for other tables");
    }

    @Test
    @DisplayName("createOrder: throws 409 Conflict if service session is CLOSED")
    void shouldThrowConflictWhenSessionNotActive() {
        authenticate(UserRole.SERVICE_STAFF, null);
        session1.setStatus(ServiceSessionStatus.CLOSED);

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(new CreateOrderItemRequest(1L, 1, null)))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Service session is not ACTIVE");
    }

    @Test
    @DisplayName("createOrder: throws 409 Conflict if menu item is TEMPORARILY_UNAVAILABLE")
    void shouldThrowConflictWhenMenuItemUnavailable() {
        authenticate(UserRole.SERVICE_STAFF, null);
        menuItemPho.setStatus(MenuItemStatus.TEMPORARILY_UNAVAILABLE);

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1));
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItemPho));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(new CreateOrderItemRequest(1L, 1, null)))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("currently TEMPORARILY_UNAVAILABLE");
    }

    @Test
    @DisplayName("createOrder: throws 409 Conflict if ingredient stock is insufficient (no order saved, no stock changed)")
    void shouldThrowConflictWhenStockInsufficient() {
        authenticate(UserRole.SERVICE_STAFF, null);
        beefIngredient.setCurrentStock(BigDecimal.valueOf(0.100)); // Only 100g in stock, needs 300g

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1));
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItemPho));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(recipePho));
        when(ingredientRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(beefIngredient));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(new CreateOrderItemRequest(1L, 2, null))) // needs 0.300
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock");

        // Assert nothing saved
        verify(orderRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        assertThat(beefIngredient.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(0.100));
    }

    @Test
    @DisplayName("cancelOrder: cancels PENDING order, restores ingredient stock, appends compensating movement")
    void shouldCancelPendingOrderAndRestoreStock() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.PENDING)
                .build();

        OrderItem item = OrderItem.builder()
                .id(801L)
                .order(order)
                .menuItem(menuItemPho)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();

        StockMovement initialConsumption = StockMovement.builder()
                .id(301L)
                .ingredient(beefIngredient)
                .movementType(StockMovementType.ORDER_CONSUMPTION)
                .quantityChange(BigDecimal.valueOf(-0.300))
                .orderItem(item)
                .build();

        beefIngredient.setCurrentStock(BigDecimal.valueOf(9.700));

        when(orderRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.of(tableUser));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderIdWithMenuItem(901L)).thenReturn(List.of(item));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.findByOrderItemId(801L)).thenReturn(List.of(initialConsumption));
        when(ingredientRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(beefIngredient));

        CancelOrderRequest cancelReq = new CancelOrderRequest("Khách đổi ý");
        OrderResponse response = orderService.cancelOrder(901L, cancelReq);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(item.getTerminationReason()).isEqualTo("Khách đổi ý");

        // 9.700 + 0.300 = 10.000 restored
        assertThat(beefIngredient.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(10.000));

        // Positive compensating movement
        verify(stockMovementRepository).save(argThat(m ->
                m.getMovementType() == StockMovementType.ORDER_CONSUMPTION &&
                m.getQuantityChange().compareTo(BigDecimal.valueOf(0.300)) == 0 &&
                m.getBalanceAfter().compareTo(BigDecimal.valueOf(10.000)) == 0
        ));
    }

    @Test
    @DisplayName("cancelOrder: throws 409 Conflict if order is COOKING or already CANCELLED")
    void shouldThrowConflictWhenCancellingNonPendingOrder() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.COOKING)
                .build();

        when(orderRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(901L, new CancelOrderRequest("Lý do")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be cancelled");

        verify(orderRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrderItem: cancels single item, restores stock, and cancels parent order if all items cancelled")
    void shouldCancelSingleOrderItemAndCancelParentOrder() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.PENDING)
                .build();

        OrderItem item = OrderItem.builder()
                .id(801L)
                .order(order)
                .menuItem(menuItemPho)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();

        StockMovement consumption = StockMovement.builder()
                .id(301L)
                .ingredient(beefIngredient)
                .movementType(StockMovementType.ORDER_CONSUMPTION)
                .quantityChange(BigDecimal.valueOf(-0.150))
                .orderItem(item)
                .build();

        beefIngredient.setCurrentStock(BigDecimal.valueOf(9.850));

        when(orderItemRepository.findByIdForUpdate(801L)).thenReturn(Optional.of(item));
        when(userRepository.findById(10L)).thenReturn(Optional.of(tableUser));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.findByOrderItemId(801L)).thenReturn(List.of(consumption));
        when(ingredientRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(beefIngredient));
        when(orderItemRepository.findByOrderId(901L)).thenReturn(List.of(item)); // Only 1 item, now cancelled

        OrderItemResponse response = orderService.cancelOrderItem(901L, 801L, new CancelOrderItemRequest("Món này hết"));

        assertThat(response.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(beefIngredient.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(10.000));

        // Parent order also marked CANCELLED because it was PENDING and all items are cancelled
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("cancelOrderItem: idempotent protection prevents restoring stock twice")
    void shouldNotRestoreStockTwiceForSameItem() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.PENDING)
                .build();

        OrderItem item = OrderItem.builder()
                .id(801L)
                .order(order)
                .menuItem(menuItemPho)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(65000))
                .status(OrderItemStatus.PENDING)
                .build();

        StockMovement consumption = StockMovement.builder()
                .id(301L)
                .ingredient(beefIngredient)
                .movementType(StockMovementType.ORDER_CONSUMPTION)
                .quantityChange(BigDecimal.valueOf(-0.150))
                .orderItem(item)
                .build();

        StockMovement alreadyRestored = StockMovement.builder()
                .id(302L)
                .ingredient(beefIngredient)
                .movementType(StockMovementType.ORDER_CONSUMPTION)
                .quantityChange(BigDecimal.valueOf(0.150)) // Positive already exists!
                .orderItem(item)
                .build();

        when(orderItemRepository.findByIdForUpdate(801L)).thenReturn(Optional.of(item));
        when(userRepository.findById(10L)).thenReturn(Optional.of(tableUser));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.findByOrderItemId(801L)).thenReturn(List.of(consumption, alreadyRestored));
        when(orderItemRepository.findByOrderId(901L)).thenReturn(List.of(item));

        orderService.cancelOrderItem(901L, 801L, new CancelOrderItemRequest("Hủy lại"));

        // No new stock movements saved because it was already compensated
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: throws 409 Conflict if menu item is DISCONTINUED")
    void shouldThrowConflictWhenMenuItemDiscontinued() {
        authenticate(UserRole.SERVICE_STAFF, null);
        menuItemPho.setStatus(MenuItemStatus.DISCONTINUED);

        when(serviceSessionRepository.findById(100L)).thenReturn(Optional.of(session1));
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(menuItemPho));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(100L)
                .items(List.of(new CreateOrderItemRequest(1L, 1, null)))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("currently DISCONTINUED");
    }

    @Test
    @DisplayName("cancelOrder: throws 409 Conflict if order is COMPLETED")
    void shouldThrowConflictWhenCancellingCompletedOrder() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.COMPLETED)
                .build();

        when(orderRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(901L, new CancelOrderRequest("Lý do")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be cancelled");
    }

    @Test
    @DisplayName("cancelOrderItem: throws 409 Conflict if order item is COOKING")
    void shouldThrowConflictWhenCancellingCookingOrderItem() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.COOKING)
                .build();

        OrderItem item = OrderItem.builder()
                .id(801L)
                .order(order)
                .menuItem(menuItemPho)
                .quantity(1)
                .status(OrderItemStatus.COOKING)
                .build();

        when(orderItemRepository.findByIdForUpdate(801L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> orderService.cancelOrderItem(901L, 801L, new CancelOrderItemRequest("Lý do")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING order items can be cancelled");
    }

    @Test
    @DisplayName("cancelOrderItem: throws 409 Conflict if order item is COMPLETED")
    void shouldThrowConflictWhenCancellingCompletedOrderItem() {
        authenticate(UserRole.SERVICE_STAFF, null);

        Order order = Order.builder()
                .id(901L)
                .serviceSession(session1)
                .status(OrderStatus.COMPLETED)
                .build();

        OrderItem item = OrderItem.builder()
                .id(801L)
                .order(order)
                .menuItem(menuItemPho)
                .quantity(1)
                .status(OrderItemStatus.COMPLETED)
                .build();

        when(orderItemRepository.findByIdForUpdate(801L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> orderService.cancelOrderItem(901L, 801L, new CancelOrderItemRequest("Lý do")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING order items can be cancelled");
    }
}
