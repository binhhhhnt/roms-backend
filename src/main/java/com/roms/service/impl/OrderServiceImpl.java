package com.roms.service.impl;

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
import com.roms.service.RestaurantSettingService;
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
    private final RestaurantSettingService restaurantSettingService;
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

        // 3. Check sufficiency for each locked ingredient; reject entire order if any is insufficient
        List<Long> insufficientIngIds = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : aggregatedRequirements.entrySet()) {
            Long ingId = entry.getKey();
            BigDecimal required = entry.getValue();
            Ingredient ing = lockedIngredients.get(ingId);
            if (ing.getCurrentStock().compareTo(required) < 0) {
                insufficientIngIds.add(ingId);
            }
        }

        if (!insufficientIngIds.isEmpty()) {
            List<Map<String, Object>> affectedMenuItems = new ArrayList<>();
            List<Map<String, Object>> insufficientList = new ArrayList<>();

            for (CreateOrderItemRequest line : request.getItems()) {
                MenuItem item = menuItemMap.get(line.getMenuItemId());
                List<RecipeItem> recipes = recipeMap.get(item.getId());
                boolean usesInsufficient = recipes.stream()
                        .anyMatch(r -> insufficientIngIds.contains(r.getIngredient().getId()));

                if (usesInsufficient) {
                    Map<String, Object> itemDetail = new LinkedHashMap<>();
                    itemDetail.put("menuItemId", item.getId());
                    itemDetail.put("name", item.getName());
                    itemDetail.put("requestedQuantity", line.getQuantity());
                    affectedMenuItems.add(itemDetail);
                }
            }

            for (Long ingId : insufficientIngIds) {
                Ingredient ing = lockedIngredients.get(ingId);
                BigDecimal required = aggregatedRequirements.get(ingId);
                Map<String, Object> ingDetail = new LinkedHashMap<>();
                ingDetail.put("ingredientId", ing.getId());
                ingDetail.put("name", ing.getName());
                ingDetail.put("currentStock", ing.getCurrentStock());
                ingDetail.put("requiredStock", required);
                ingDetail.put("unit", ing.getUnit());
                insufficientList.add(ingDetail);
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
        boolean menuPriceIncludesVat = restaurantSettingService.isMenuPriceIncludesVat();
        List<OrderItem> savedItems = new ArrayList<>();

        for (CreateOrderItemRequest line : request.getItems()) {
            MenuItem menuItem = menuItemMap.get(line.getMenuItemId());

            BigDecimal taxRate = (menuItem.getTaxCategory() != null && menuItem.getTaxCategory().getRate() != null)
                    ? menuItem.getTaxCategory().getRate()
                    : BigDecimal.valueOf(8.00);

            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .menuItem(menuItem)
                    .quantity(line.getQuantity())
                    .unitPrice(menuItem.getPrice())
                    .taxRate(taxRate)
                    .isVatInclusive(menuPriceIncludesVat)
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

        // Broadcast STOMP events after commit
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

        UserRole role = currentUser.getRole();
        if (role != UserRole.TABLE_ACCOUNT && role != UserRole.SERVICE_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Role '" + role + "' is not permitted to cancel orders");
        }

        if (role == UserRole.TABLE_ACCOUNT) {
            Long sessionTableId = order.getServiceSession().getTable() != null
                    ? order.getServiceSession().getTable().getId() : null;
            if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
                throw new ForbiddenException("Cannot cancel order from other tables");
            }
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

        UserRole role = currentUser.getRole();
        if (role != UserRole.TABLE_ACCOUNT && role != UserRole.SERVICE_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Role '" + role + "' is not permitted to cancel order items");
        }

        if (role == UserRole.TABLE_ACCOUNT) {
            Long sessionTableId = item.getOrder().getServiceSession().getTable() != null
                    ? item.getOrder().getServiceSession().getTable().getId() : null;
            if (!Objects.equals(currentUser.getTableId(), sessionTableId)) {
                throw new ForbiddenException("Cannot cancel item from other tables");
            }
        }

        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw new BusinessRuleException("INVALID_ORDER_ITEM_STATUS",
                    "Only PENDING order items can be cancelled. Current status: " + item.getStatus());
        }

        User canceller = userRepository.findById(currentUser.getId()).orElse(null);
        String reason = request != null ? request.getReason() : null;

        cancelAndRestoreItem(item, reason, canceller);

        // Check if all non-cancelled items in the parent order are resolved
        Order parentOrder = item.getOrder();
        List<OrderItem> allItems = orderItemRepository.findByOrderId(parentOrder.getId());
        boolean allCancelled = allItems.stream().allMatch(i -> i.getStatus() == OrderItemStatus.CANCELLED);

        if (allCancelled && parentOrder.getStatus() == OrderStatus.PENDING) {
            parentOrder.setStatus(OrderStatus.CANCELLED);
            parentOrder.setTerminationReason("All items cancelled");
            parentOrder.setTerminatedBy(canceller);
            parentOrder.setTerminatedAt(LocalDateTime.now());
            orderRepository.save(parentOrder);
            orderEventPublisher.publishOrderStatusChanged(parentOrder, OrderStatus.PENDING, OrderStatus.CANCELLED);
        }

        // Broadcast STOMP item status change event
        orderEventPublisher.publishOrderItemStatusChanged(item, OrderItemStatus.PENDING, OrderItemStatus.CANCELLED);

        return mapToOrderItemResponse(item);
    }

    @Override
    @Transactional
    public OrderItemResponse completeOrderItem(Long orderId, Long itemId) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.SERVICE_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Role '" + role + "' cannot complete order items");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        refreshEntity(order);

        OrderItem item = orderItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));
        refreshEntity(item);

        if (!item.getOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("Order item #" + itemId + " does not belong to Order #" + orderId);
        }

        if (item.getStatus() != OrderItemStatus.READY) {
            throw new BusinessRuleException("INVALID_ITEM_STATUS",
                    "Only READY items can be marked as COMPLETED. Current status: " + item.getStatus());
        }

        item.setStatus(OrderItemStatus.COMPLETED);
        OrderItem savedItem = orderItemRepository.save(item);

        // Check if all non-cancelled items in parent order are COMPLETED
        List<OrderItem> allItems = orderItemRepository.findByOrderIdWithMenuItem(orderId);
        boolean allFinished = allItems.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .allMatch(i -> i.getId().equals(itemId) || i.getStatus() == OrderItemStatus.COMPLETED);

        long activeCount = allItems.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).count();
        if (allFinished && activeCount > 0 && order.getStatus() != OrderStatus.COMPLETED) {
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.COMPLETED);
            Order savedOrder = orderRepository.save(order);
            orderEventPublisher.publishOrderStatusChanged(savedOrder, oldStatus, OrderStatus.COMPLETED);
        }

        orderEventPublisher.publishOrderItemStatusChanged(savedItem, OrderItemStatus.READY, OrderItemStatus.COMPLETED);

        return mapToOrderItemResponse(savedItem);
    }

    /**
     * Restore stock and cancel item based on recorded ORDER_CONSUMPTION movements.
     */
    private void cancelAndRestoreItem(OrderItem item, String reason, User canceller) {
        List<StockMovement> originalMovements = stockMovementRepository.findByOrderItemId(item.getId());

        boolean alreadyRestored = originalMovements.stream()
                .anyMatch(m -> m.getQuantityChange().compareTo(BigDecimal.ZERO) > 0);

        if (!alreadyRestored) {
            List<StockMovement> consumptionMovements = originalMovements.stream()
                    .filter(m -> m.getMovementType() == StockMovementType.ORDER_CONSUMPTION
                            && m.getQuantityChange().compareTo(BigDecimal.ZERO) < 0)
                    .toList();

            List<Long> ingIds = consumptionMovements.stream()
                    .map(m -> m.getIngredient().getId())
                    .sorted()
                    .toList();

            Map<Long, Ingredient> lockedIngredients = new LinkedHashMap<>();
            for (Long ingId : ingIds) {
                Ingredient ing = ingredientRepository.findByIdForUpdate(ingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + ingId));
                refreshEntity(ing);
                lockedIngredients.put(ingId, ing);
            }

            for (StockMovement m : consumptionMovements) {
                BigDecimal consumed = m.getQuantityChange().abs();
                Ingredient ing = lockedIngredients.get(m.getIngredient().getId());
                BigDecimal newBalance = ing.getCurrentStock().add(consumed);
                ing.setCurrentStock(newBalance);
                ingredientRepository.save(ing);

                StockMovement compensating = StockMovement.builder()
                        .ingredient(ing)
                        .movementType(StockMovementType.ORDER_CONSUMPTION)
                        .quantityChange(consumed) // Positive for restoration
                        .balanceAfter(newBalance)
                        .orderItem(item)
                        .note("Item #" + item.getId() + " cancellation stock restoration")
                        .build();
                stockMovementRepository.save(compensating);
            }
        }

        item.setStatus(OrderItemStatus.CANCELLED);
        item.setTerminationReason(reason);
        item.setTerminatedBy(canceller);
        item.setTerminatedAt(LocalDateTime.now());
        orderItemRepository.save(item);
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
                .taxRate(item.getTaxRate())
                .isVatInclusive(item.getIsVatInclusive())
                .status(item.getStatus())
                .note(item.getNote())
                .terminationReason(item.getTerminationReason())
                .terminatedById(item.getTerminatedBy() != null ? item.getTerminatedBy().getId() : null)
                .terminatedAt(item.getTerminatedAt())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
