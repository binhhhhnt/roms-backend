package com.roms.service.impl;

import com.roms.dto.event.OrderStatusChangedEvent;
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
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.OrderService;
import com.roms.websocket.OrderEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final IngredientRepository ingredientRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ServiceSessionRepository serviceSessionRepository;
    private final UserRepository userRepository;
    private final OrderEventPublisher orderEventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private void refreshEntity(Object entity) {
        if (entityManager != null) {
            try {
                entityManager.refresh(entity);
            } catch (Exception e) {
                log.debug("Entity refresh skipped: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.TABLE_ACCOUNT && role != UserRole.SERVICE_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Role '" + role + "' is not permitted to create orders");
        }

        ServiceSession session = serviceSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + request.getSessionId()));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("SESSION_NOT_ACTIVE", "Service session is not ACTIVE");
        }

        if (role == UserRole.TABLE_ACCOUNT) {
            Long userTableId = currentUser.getTableId();
            Long sessionTableId = session.getTable() != null ? session.getTable().getId() : null;
            if (userTableId == null || !userTableId.equals(sessionTableId)) {
                throw new ForbiddenException("Table account cannot place orders for other tables");
            }
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_ORDER_ITEMS", "Order must contain at least one item");
        }

        // 1. Validate all menu items are AVAILABLE and compute aggregate ingredient requirements
        Map<Long, MenuItem> menuItemMap = new HashMap<>();
        Map<Long, List<RecipeItem>> recipeMap = new HashMap<>();
        Map<Long, BigDecimal> aggregatedRequirements = new HashMap<>();

        for (CreateOrderItemRequest line : request.getItems()) {
            MenuItem menuItem = menuItemMap.computeIfAbsent(line.getMenuItemId(), id ->
                    menuItemRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id))
            );

            if (menuItem.getStatus() != MenuItemStatus.AVAILABLE) {
                throw new BusinessRuleException("MENU_ITEM_UNAVAILABLE",
                        "Menu item '" + menuItem.getName() + "' is currently " + menuItem.getStatus());
            }

            List<RecipeItem> recipes = recipeMap.computeIfAbsent(menuItem.getId(),
                    recipeItemRepository::findByMenuItemIdWithIngredient);

            for (RecipeItem recipe : recipes) {
                Long ingId = recipe.getIngredient().getId();
                BigDecimal perItem = recipe.getQuantityRequired();
                BigDecimal totalLine = perItem.multiply(BigDecimal.valueOf(line.getQuantity()));
                aggregatedRequirements.merge(ingId, totalLine, BigDecimal::add);
            }
        }

        // 2. Lock ingredients in consistent ascending ID order to prevent concurrency deadlocks
        List<Long> sortedIngIds = aggregatedRequirements.keySet().stream().sorted().toList();
        Map<Long, Ingredient> lockedIngredients = new LinkedHashMap<>();
        for (Long ingId : sortedIngIds) {
            Ingredient ing = ingredientRepository.findByIdForUpdate(ingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + ingId));
            refreshEntity(ing);
            lockedIngredients.put(ingId, ing);
        }

        // 3. Check stock sufficiency: if ANY ingredient is insufficient, reject with 409 Conflict
        Set<Long> insufficientIngIds = new LinkedHashSet<>();
        for (Map.Entry<Long, BigDecimal> entry : aggregatedRequirements.entrySet()) {
            Ingredient ing = lockedIngredients.get(entry.getKey());
            BigDecimal required = entry.getValue();
            if (!Boolean.TRUE.equals(ing.getIsActive()) || ing.getCurrentStock().compareTo(required) < 0) {
                insufficientIngIds.add(entry.getKey());
            }
        }

        if (!insufficientIngIds.isEmpty()) {
            List<Map<String, Object>> affectedMenuItems = new ArrayList<>();
            for (CreateOrderItemRequest line : request.getItems()) {
                MenuItem menuItem = menuItemMap.get(line.getMenuItemId());
                List<RecipeItem> recipes = recipeMap.get(menuItem.getId());
                boolean affected = recipes != null && recipes.stream()
                        .anyMatch(r -> r.getIngredient() != null && insufficientIngIds.contains(r.getIngredient().getId()));
                if (affected) {
                    Map<String, Object> itemDetail = new LinkedHashMap<>();
                    itemDetail.put("menuItemId", menuItem.getId());
                    itemDetail.put("menuItemName", menuItem.getName());
                    itemDetail.put("requestedQuantity", line.getQuantity());
                    affectedMenuItems.add(itemDetail);
                }
            }

            List<Map<String, Object>> insufficientList = new ArrayList<>();
            for (Long ingId : insufficientIngIds) {
                Ingredient ing = lockedIngredients.get(ingId);
                if (ing != null) {
                    Map<String, Object> ingDetail = new LinkedHashMap<>();
                    ingDetail.put("ingredientId", ing.getId());
                    ingDetail.put("ingredientName", ing.getName());
                    insufficientList.add(ingDetail);
                }
            }

            Map<String, Object> errorDetails = new LinkedHashMap<>();
            errorDetails.put("affectedMenuItems", affectedMenuItems);
            errorDetails.put("insufficientIngredients", insufficientList);

            throw new BusinessRuleException(
                    "INSUFFICIENT_STOCK",
                    "Insufficient stock for some items in your order. Please reduce quantity or remove affected items.",
                    errorDetails
            );
        }

        // 4. Persist Order
        User creator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUser.getId()));

        Order order = Order.builder()
                .serviceSession(session)
                .createdBy(creator)
                .status(OrderStatus.PENDING)
                .build();
        Order savedOrder = orderRepository.save(order);

        // 5. Persist OrderItems, deduct stock, and record negative ORDER_CONSUMPTION movements
        List<OrderItem> savedItems = new ArrayList<>();
        for (CreateOrderItemRequest line : request.getItems()) {
            MenuItem menuItem = menuItemMap.get(line.getMenuItemId());
            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .menuItem(menuItem)
                    .quantity(line.getQuantity())
                    .unitPrice(menuItem.getPrice())
                    .note(line.getNote())
                    .status(OrderItemStatus.PENDING)
                    .build();
            OrderItem savedItem = orderItemRepository.save(item);
            savedItems.add(savedItem);

            List<RecipeItem> recipes = recipeMap.get(menuItem.getId());
            for (RecipeItem recipe : recipes) {
                Ingredient ing = lockedIngredients.get(recipe.getIngredient().getId());
                BigDecimal consumption = recipe.getQuantityRequired().multiply(BigDecimal.valueOf(line.getQuantity()));
                BigDecimal newBalance = ing.getCurrentStock().subtract(consumption);
                ing.setCurrentStock(newBalance);
                ingredientRepository.save(ing);

                StockMovement movement = StockMovement.builder()
                        .ingredient(ing)
                        .movementType(StockMovementType.ORDER_CONSUMPTION)
                        .quantityChange(consumption.negate()) // Negative for consumption
                        .balanceAfter(newBalance)
                        .orderItem(savedItem)
                        .note("Order #" + savedOrder.getId() + " Item #" + savedItem.getId() + " consumption")
                        .build();
                stockMovementRepository.save(movement);
            }
        }

        // 6. Broadcast STOMP events after commit
        orderEventPublisher.publishOrderCreated(savedOrder, savedItems.size());

        return mapToOrderResponse(savedOrder, savedItems);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal().orElse(null);
        if (currentUser != null && currentUser.getRole() == UserRole.TABLE_ACCOUNT) {
            Long sessionTableId = order.getServiceSession().getTable() != null
                    ? order.getServiceSession().getTable().getId() : null;
            if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
                throw new ForbiddenException("Cannot view order from other tables");
            }
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdWithMenuItem(order.getId());
        return mapToOrderResponse(order, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(Long sessionId, OrderStatus status) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal().orElse(null);

        if (currentUser != null && currentUser.getRole() == UserRole.TABLE_ACCOUNT) {
            if (sessionId != null) {
                ServiceSession session = serviceSessionRepository.findById(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));
                Long sessionTableId = session.getTable() != null ? session.getTable().getId() : null;
                if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
                    throw new ForbiddenException("Cannot view orders from other tables");
                }
            }
        }

        List<Order> orders = orderRepository.findOrdersFiltered(sessionId, status);

        if (currentUser != null && currentUser.getRole() == UserRole.TABLE_ACCOUNT && sessionId == null) {
            Long tableId = currentUser.getTableId();
            orders = orders.stream()
                    .filter(o -> o.getServiceSession().getTable() != null && Objects.equals(o.getServiceSession().getTable().getId(), tableId))
                    .toList();
        }

        return orders.stream()
                .map(o -> {
                    List<OrderItem> items = orderItemRepository.findByOrderIdWithMenuItem(o.getId());
                    return mapToOrderResponse(o, items);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, CancelOrderRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        refreshEntity(order);

        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        if (currentUser.getRole() != UserRole.TABLE_ACCOUNT) {
            throw new ForbiddenException("Only table accounts can cancel orders");
        }

        Long sessionTableId = order.getServiceSession().getTable() != null
                ? order.getServiceSession().getTable().getId() : null;
        if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
            throw new ForbiddenException("Cannot cancel order from other tables");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("INVALID_ORDER_STATUS",
                    "Only PENDING orders can be cancelled. Current status: " + order.getStatus());
        }

        User canceller = userRepository.findById(currentUser.getId()).orElse(null);
        String reason = request != null ? request.getReason() : null;

        order.setStatus(OrderStatus.CANCELLED);
        order.setTerminationReason(reason);
        order.setTerminatedBy(canceller);
        order.setTerminatedAt(LocalDateTime.now());
        Order updatedOrder = orderRepository.save(order);

        List<OrderItem> items = orderItemRepository.findByOrderIdWithMenuItem(order.getId());
        for (OrderItem item : items) {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                cancelAndRestoreItem(item, reason, canceller);
            }
        }

        // Broadcast STOMP event after commit
        orderEventPublisher.publishOrderStatusChanged(updatedOrder, OrderStatus.PENDING, OrderStatus.CANCELLED);

        return mapToOrderResponse(updatedOrder, items);
    }

    @Override
    @Transactional
    public OrderItemResponse cancelOrderItem(Long orderId, Long itemId, CancelOrderItemRequest request) {
        OrderItem item = orderItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));
        refreshEntity(item);

        if (!item.getOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("Order item #" + itemId + " does not belong to Order #" + orderId);
        }

        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        if (currentUser.getRole() != UserRole.TABLE_ACCOUNT) {
            throw new ForbiddenException("Only table accounts can cancel order items");
        }

        Long sessionTableId = item.getOrder().getServiceSession().getTable() != null
                ? item.getOrder().getServiceSession().getTable().getId() : null;
        if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
            throw new ForbiddenException("Cannot cancel order item from other tables");
        }

        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw new BusinessRuleException("INVALID_ORDER_ITEM_STATUS",
                    "Only PENDING order items can be cancelled. Current status: " + item.getStatus());
        }

        User canceller = userRepository.findById(currentUser.getId()).orElse(null);
        String reason = request != null ? request.getReason() : null;

        cancelAndRestoreItem(item, reason, canceller);

        // Check parent order state transitions
        Order parentOrder = item.getOrder();
        List<OrderItem> allItems = orderItemRepository.findByOrderId(parentOrder.getId());
        List<OrderItem> activeItems = allItems.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .toList();

        if (activeItems.isEmpty()) {
            if (parentOrder.getStatus() == OrderStatus.PENDING) {
                parentOrder.setStatus(OrderStatus.CANCELLED);
                parentOrder.setTerminationReason(reason);
                parentOrder.setTerminatedBy(canceller);
                parentOrder.setTerminatedAt(LocalDateTime.now());
                orderRepository.save(parentOrder);
            }
            // If parentOrder is COOKING, do NOT auto-complete; keep state per architecture rules
        } else {
            boolean allActiveCompleted = activeItems.stream().allMatch(i -> i.getStatus() == OrderItemStatus.COMPLETED);
            if (allActiveCompleted) {
                parentOrder.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(parentOrder);
                orderEventPublisher.publishOrderStatusChanged(parentOrder, OrderStatus.COOKING, OrderStatus.COMPLETED);
            }
        }

        orderEventPublisher.publishOrderItemStatusChanged(item, OrderItemStatus.PENDING, OrderItemStatus.CANCELLED);

        return mapToOrderItemResponse(item);
    }

    private void cancelAndRestoreItem(OrderItem item, String reason, User canceller) {
        item.setStatus(OrderItemStatus.CANCELLED);
        item.setTerminationReason(reason);
        item.setTerminatedBy(canceller);
        item.setTerminatedAt(LocalDateTime.now());
        orderItemRepository.save(item);

        List<StockMovement> movements = stockMovementRepository.findByOrderItemId(item.getId());

        // Check idempotency: if compensating positive movement already exists, do not restore again
        boolean alreadyRestored = movements.stream()
                .anyMatch(m -> m.getQuantityChange().compareTo(BigDecimal.ZERO) > 0);
        if (alreadyRestored) {
            log.warn("OrderItem #{} already has compensating stock movements; skipping restoration", item.getId());
            return;
        }

        List<StockMovement> consumptions = movements.stream()
                .filter(m -> m.getQuantityChange().compareTo(BigDecimal.ZERO) < 0)
                .toList();

        // Lock ingredients in ascending order to prevent deadlocks
        List<Long> ingIds = consumptions.stream()
                .map(m -> m.getIngredient().getId())
                .distinct()
                .sorted()
                .toList();

        Map<Long, Ingredient> lockedIngs = new HashMap<>();
        for (Long ingId : ingIds) {
            Ingredient ing = ingredientRepository.findByIdForUpdate(ingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + ingId));
            refreshEntity(ing);
            lockedIngs.put(ingId, ing);
        }

        // Restore stock based on historical deduction movements and append compensating movements
        for (StockMovement consumption : consumptions) {
            Ingredient ing = lockedIngs.get(consumption.getIngredient().getId());
            BigDecimal restoredQty = consumption.getQuantityChange().abs();
            BigDecimal newBalance = ing.getCurrentStock().add(restoredQty);
            ing.setCurrentStock(newBalance);
            ingredientRepository.save(ing);

            StockMovement compensating = StockMovement.builder()
                    .ingredient(ing)
                    .movementType(StockMovementType.ORDER_CONSUMPTION)
                    .quantityChange(restoredQty) // Positive compensating movement
                    .balanceAfter(newBalance)
                    .orderItem(item)
                    .note("Compensating restoration for cancelled OrderItem #" + item.getId())
                    .build();
            stockMovementRepository.save(compensating);
        }
    }

    private OrderResponse mapToOrderResponse(Order order, List<OrderItem> items) {
        Long sessionId = order.getServiceSession() != null ? order.getServiceSession().getId() : null;
        Long tableId = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getId() : null;
        String tableCode = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getTableCode() : null;

        List<OrderItemResponse> itemResponses = (items != null)
                ? items.stream().map(this::mapToOrderItemResponse).toList()
                : Collections.emptyList();

        BigDecimal totalAmount = itemResponses.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .map(OrderItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .id(order.getId())
                .sessionId(sessionId)
                .tableId(tableId)
                .tableCode(tableCode)
                .status(order.getStatus())
                .totalAmount(totalAmount)
                .createdById(order.getCreatedBy() != null ? order.getCreatedBy().getId() : null)
                .createdByName(order.getCreatedBy() != null ? order.getCreatedBy().getFullName() : null)
                .terminationReason(order.getTerminationReason())
                .terminatedById(order.getTerminatedBy() != null ? order.getTerminatedBy().getId() : null)
                .terminatedAt(order.getTerminatedAt())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        BigDecimal subtotal = (item.getUnitPrice() != null && item.getQuantity() != null)
                ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                : BigDecimal.ZERO;

        return OrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                .menuItemName(item.getMenuItem() != null ? item.getMenuItem().getName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(subtotal)
                .status(item.getStatus())
                .note(item.getNote())
                .terminationReason(item.getTerminationReason())
                .terminatedById(item.getTerminatedBy() != null ? item.getTerminatedBy().getId() : null)
                .terminatedAt(item.getTerminatedAt())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
