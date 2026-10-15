package com.roms.service;

import com.roms.dto.request.CancelOrderItemRequest;
import com.roms.dto.request.CancelOrderRequest;
import com.roms.dto.request.CreateOrderItemRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.request.UpdateOrderItemStatusRequest;
import com.roms.dto.response.KitchenOrderResponse;
import com.roms.dto.response.OrderItemResponse;
import com.roms.dto.response.OrderResponse;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private KitchenService kitchenService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private RestaurantTable testTable;
    private ServiceSession testSession;
    private User serviceStaffUser;
    private User kitchenStaffUser;
    private User tableUser;
    private Category defaultCategory;

    // Track test-specific entities created during tests for clean teardown
    private final List<Long> createdOrderIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdRecipeIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdMenuItemIds = new CopyOnWriteArrayList<>();
    private final List<Long> createdIngredientIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        testTable = tableRepository.findByTableCode("T06")
                .orElseThrow(() -> new IllegalStateException("Seed table T06 not found"));
        serviceStaffUser = userRepository.findByUsername("service01")
                .orElseThrow(() -> new IllegalStateException("Seed user service01 not found"));
        kitchenStaffUser = userRepository.findByUsername("kitchen01")
                .orElseThrow(() -> new IllegalStateException("Seed user kitchen01 not found"));
        tableUser = userRepository.findByUsername("table_t06")
                .orElseThrow(() -> new IllegalStateException("Seed user table_t06 not found"));

        User owner = userRepository.findByUsername("owner")
                .orElseThrow(() -> new IllegalStateException("Seed user owner not found"));

        testSession = sessionRepository.findByTableIdAndStatus(testTable.getId(), ServiceSessionStatus.ACTIVE)
                .orElseGet(() -> sessionRepository.save(ServiceSession.builder()
                        .table(testTable)
                        .openedBy(owner)
                        .status(ServiceSessionStatus.ACTIVE)
                        .build()));

        defaultCategory = menuItemRepository.findAll().stream()
                .filter(m -> m.getCategory() != null)
                .map(MenuItem::getCategory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No category found from seed menu items"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        // 1. Delete stock movements created by test orders
        if (!createdOrderIds.isEmpty()) {
            List<OrderItem> items = orderItemRepository.findAll().stream()
                    .filter(i -> i.getOrder() != null && createdOrderIds.contains(i.getOrder().getId()))
                    .toList();
            for (OrderItem item : items) {
                List<StockMovement> movements = stockMovementRepository.findByOrderItemId(item.getId());
                stockMovementRepository.deleteAll(movements);
            }
            orderItemRepository.deleteAll(items);
            orderRepository.deleteAllById(createdOrderIds);
            createdOrderIds.clear();
        }

        // 2. Delete recipe items
        if (!createdRecipeIds.isEmpty()) {
            recipeItemRepository.deleteAllById(createdRecipeIds);
            createdRecipeIds.clear();
        }

        // 3. Delete menu items
        if (!createdMenuItemIds.isEmpty()) {
            menuItemRepository.deleteAllById(createdMenuItemIds);
            createdMenuItemIds.clear();
        }

        // 4. Delete ingredients
        if (!createdIngredientIds.isEmpty()) {
            ingredientRepository.deleteAllById(createdIngredientIds);
            createdIngredientIds.clear();
        }
    }

    private void authenticate(User user, Long sessionId) {
        UserPrincipal principal = UserPrincipal.create(user, sessionId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Concurrency: Two concurrent orders competing for last remaining ingredient stock -> exactly 1 succeeds, 1 gets 409")
    void concurrentOrdersCompeteForLimitedStock_pessimisticLockEnsuresIntegrity() throws Exception {
        // Create an ingredient with stock = 1.000 kg
        Ingredient limitedIngredient = ingredientRepository.save(Ingredient.builder()
                .name("Tôm Hùm Thử Nghiệm Concurrency")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(1.000))
                .minimumStock(BigDecimal.ZERO)
                .isActive(true)
                .build());
        createdIngredientIds.add(limitedIngredient.getId());

        // Create a menu item requiring 1.000 kg of this ingredient per dish
        MenuItem testDish = menuItemRepository.save(MenuItem.builder()
                .category(defaultCategory)
                .name("Tôm Hùm Nướng Test")
                .price(BigDecimal.valueOf(350000))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(false)
                .build());
        createdMenuItemIds.add(testDish.getId());

        RecipeItem recipe = recipeItemRepository.save(RecipeItem.builder()
                .menuItem(testDish)
                .ingredient(limitedIngredient)
                .quantityRequired(BigDecimal.valueOf(1.000))
                .build());
        createdRecipeIds.add(recipe.getId());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<OrderResponse>> futures = new ArrayList<>();
        List<OrderResponse> successes = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(testSession.getId())
                .items(List.of(CreateOrderItemRequest.builder()
                        .menuItemId(testDish.getId())
                        .quantity(1) // requires 1.000 kg
                        .build()))
                .build();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                authenticate(serviceStaffUser, testSession.getId());
                readyLatch.countDown();
                startLatch.await(); // synchronized starting gun
                try {
                    OrderResponse res = orderService.createOrder(request);
                    successes.add(res);
                    createdOrderIds.add(res.getId());
                    return res;
                } catch (Throwable t) {
                    failures.add(t);
                    throw t;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Launch both threads simultaneously
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assertions:
        // Exactly 1 order created successfully
        assertThat(successes).hasSize(1);
        // Exactly 1 request failed due to insufficient stock
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock");

        // Verify database stock state: stock was deducted exactly once to 0.000, NEVER negative
        Ingredient updatedIng = ingredientRepository.findById(limitedIngredient.getId()).orElseThrow();
        assertThat(updatedIng.getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify stock movements: exactly 1 negative ORDER_CONSUMPTION movement recorded
        List<StockMovement> movements = stockMovementRepository.findByIngredientId(limitedIngredient.getId());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getQuantityChange()).isEqualByComparingTo(BigDecimal.valueOf(-1.000));
        assertThat(movements.get(0).getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous cancellation requests for same order do not double-restore stock")
    void concurrentOrderCancellation_idempotentStockRestoration() throws Exception {
        Ingredient cancelIng = ingredientRepository.save(Ingredient.builder()
                .name("Thịt Bò Test Hủy Đồng Thời")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(2.000))
                .minimumStock(BigDecimal.ZERO)
                .isActive(true)
                .build());
        createdIngredientIds.add(cancelIng.getId());

        MenuItem cancelDish = menuItemRepository.save(MenuItem.builder()
                .category(defaultCategory)
                .name("Bò Nướng Test Hủy")
                .price(BigDecimal.valueOf(150000))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(false)
                .build());
        createdMenuItemIds.add(cancelDish.getId());

        RecipeItem recipe = recipeItemRepository.save(RecipeItem.builder()
                .menuItem(cancelDish)
                .ingredient(cancelIng)
                .quantityRequired(BigDecimal.valueOf(0.500))
                .build());
        createdRecipeIds.add(recipe.getId());

        // Create order first
        authenticate(serviceStaffUser, testSession.getId());
        CreateOrderRequest createReq = CreateOrderRequest.builder()
                .sessionId(testSession.getId())
                .items(List.of(CreateOrderItemRequest.builder()
                        .menuItemId(cancelDish.getId())
                        .quantity(1) // consumes 0.500 kg -> balance becomes 1.500 kg
                        .build()))
                .build();
        OrderResponse createdOrder = orderService.createOrder(createReq);
        createdOrderIds.add(createdOrder.getId());

        // Verify initial deduction: 2.000 - 0.500 = 1.500 kg
        Ingredient afterOrderIng = ingredientRepository.findById(cancelIng.getId()).orElseThrow();
        assertThat(afterOrderIng.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(1.500));

        // Now cancel concurrently from two threads
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        CancelOrderRequest cancelReq = new CancelOrderRequest("Khách đổi ý đồng thời");
        List<OrderResponse> cancelSuccesses = new CopyOnWriteArrayList<>();
        List<Throwable> cancelFailures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                authenticate(tableUser, testSession.getId());
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OrderResponse res = orderService.cancelOrder(createdOrder.getId(), cancelReq);
                    cancelSuccesses.add(res);
                } catch (Throwable t) {
                    cancelFailures.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // In both cases, stock must only be restored by +0.500 ONCE -> exactly 2.000 kg, NEVER 2.500 kg!
        Ingredient finalIng = ingredientRepository.findById(cancelIng.getId()).orElseThrow();
        assertThat(finalIng.getCurrentStock()).isEqualByComparingTo(BigDecimal.valueOf(2.000));

        // Verify compensating movements: total positive movement sum is exactly +0.500
        List<StockMovement> movements = stockMovementRepository.findByIngredientId(cancelIng.getId());
        BigDecimal totalPositiveRestored = movements.stream()
                .map(StockMovement::getQuantityChange)
                .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalPositiveRestored).isEqualByComparingTo(BigDecimal.valueOf(0.500));
    }

    @Test
    @DisplayName("End-to-End State Machine: Order creation -> Kitchen accept -> item progression -> Order completion")
    void endToEndOrderKitchenLifecycle_stateTransitions() {
        // Step 1: Create Order with 2 items (Pho + Drink from seed data)
        authenticate(serviceStaffUser, testSession.getId());
        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(testSession.getId())
                .items(List.of(
                        CreateOrderItemRequest.builder().menuItemId(1L).quantity(1).build(), // Phở Bò Tái Nạm
                        CreateOrderItemRequest.builder().menuItemId(8L).quantity(1).build()  // Khoai Tây Chiên
                ))
                .build();

        OrderResponse orderRes = orderService.createOrder(request);
        createdOrderIds.add(orderRes.getId());
        assertThat(orderRes.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRes.getItems()).hasSize(2);

        Long orderId = orderRes.getId();
        Long item1Id = orderRes.getItems().get(0).getId();
        Long item2Id = orderRes.getItems().get(1).getId();

        // Step 2: Kitchen accepts order: transitions to COOKING (no stock re-deducted)
        authenticate(kitchenStaffUser, null);
        KitchenOrderResponse kitchenRes = kitchenService.acceptOrder(orderId);
        assertThat(kitchenRes.getStatus()).isEqualTo(OrderStatus.COOKING);

        // Step 3: Cancelling order when it is COOKING must fail with 409 Conflict
        authenticate(tableUser, testSession.getId());
        assertThatThrownBy(() -> orderService.cancelOrder(orderId, new CancelOrderRequest("Hủy giữa chừng")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be cancelled");

        // Step 4: Advance item 1: PENDING -> COOKING -> READY
        authenticate(kitchenStaffUser, null);
        OrderItemResponse item1Cooking = kitchenService.updateOrderItemStatus(orderId, item1Id,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING));
        assertThat(item1Cooking.getStatus()).isEqualTo(OrderItemStatus.COOKING);

        OrderItemResponse item1Ready = kitchenService.updateOrderItemStatus(orderId, item1Id,
                new UpdateOrderItemStatusRequest(OrderItemStatus.READY));
        assertThat(item1Ready.getStatus()).isEqualTo(OrderItemStatus.READY);

        // Step 5: Advance item 1: READY -> COMPLETED.
        // Parent order must STILL be COOKING because item 2 is still PENDING
        authenticate(serviceStaffUser, testSession.getId());
        OrderItemResponse item1Completed = kitchenService.updateOrderItemStatus(orderId, item1Id,
                new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED));
        assertThat(item1Completed.getStatus()).isEqualTo(OrderItemStatus.COMPLETED);

        Order currentOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(currentOrder.getStatus()).isEqualTo(OrderStatus.COOKING);

        // Step 6: Advance item 2: PENDING -> COOKING -> READY -> COMPLETED
        authenticate(kitchenStaffUser, null);
        kitchenService.updateOrderItemStatus(orderId, item2Id, new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING));
        kitchenService.updateOrderItemStatus(orderId, item2Id, new UpdateOrderItemStatusRequest(OrderItemStatus.READY));

        authenticate(serviceStaffUser, testSession.getId());
        kitchenService.updateOrderItemStatus(orderId, item2Id, new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED));

        // Step 7: All non-cancelled items are COMPLETED -> Parent order MUST automatically be COMPLETED!
        Order completedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Step 8: Verify kitchen cannot accept an already COMPLETED order (409 Conflict)
        authenticate(kitchenStaffUser, null);
        assertThatThrownBy(() -> kitchenService.acceptOrder(orderId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING orders can be accepted");
    }

    @Test
    @DisplayName("Edge Case: Mixed order with 1 COMPLETED item and 1 CANCELLED item completes automatically")
    void mixedOrder_oneCancelledOneCompleted_completesOrder() {
        authenticate(serviceStaffUser, testSession.getId());
        CreateOrderRequest request = CreateOrderRequest.builder()
                .sessionId(testSession.getId())
                .items(List.of(
                        CreateOrderItemRequest.builder().menuItemId(1L).quantity(1).build(),
                        CreateOrderItemRequest.builder().menuItemId(8L).quantity(1).build()
                ))
                .build();

        OrderResponse orderRes = orderService.createOrder(request);
        createdOrderIds.add(orderRes.getId());
        Long orderId = orderRes.getId();
        Long item1Id = orderRes.getItems().get(0).getId();
        Long item2Id = orderRes.getItems().get(1).getId();

        // Cancel Item 2 while PENDING (restores stock)
        authenticate(tableUser, testSession.getId());
        orderService.cancelOrderItem(orderId, item2Id, new CancelOrderItemRequest("Khách hủy món 2"));

        // Kitchen accepts order -> COOKING
        authenticate(kitchenStaffUser, null);
        kitchenService.acceptOrder(orderId);

        // Advance Item 1: PENDING -> COOKING -> READY
        kitchenService.updateOrderItemStatus(orderId, item1Id, new UpdateOrderItemStatusRequest(OrderItemStatus.COOKING));
        kitchenService.updateOrderItemStatus(orderId, item1Id, new UpdateOrderItemStatusRequest(OrderItemStatus.READY));

        // Service staff completes Item 1
        authenticate(serviceStaffUser, testSession.getId());
        kitchenService.updateOrderItemStatus(orderId, item1Id, new UpdateOrderItemStatusRequest(OrderItemStatus.COMPLETED));

        // Since item 2 is CANCELLED and item 1 is COMPLETED, all active items are done -> order COMPLETED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
}
