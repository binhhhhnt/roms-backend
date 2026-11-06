package com.roms.service.impl;

import com.roms.dto.request.CancelOrderItemRequest;
import com.roms.dto.request.CancelOrderRequest;
import com.roms.dto.request.CreateOrderItemRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.request.ReturnOrderItemsRequest;
import com.roms.dto.response.DirectSaleItemDto;
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

                String movementNote = "Đơn #" + savedOrder.getId() + " (" + menuItem.getName() + ") • Bởi: "
                        + currentUser.getUsername() + " (#" + currentUser.getId() + ")";
                StockMovement movement = StockMovement.builder()
                        .ingredient(ing)
                        .movementType(StockMovementType.ORDER_CONSUMPTION)
                        .quantityChange(consumption.negate()) // Negative for consumption
                        .balanceAfter(newBalance)
                        .orderItem(savedItem)
                        .note(movementNote)
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

        int currentQty = (item.getQuantity() != null && item.getQuantity() > 0) ? item.getQuantity() : 1;
        int cancelQty = (request != null && request.getQuantity() != null)
                ? request.getQuantity()
                : currentQty;

        if (cancelQty < 1 || cancelQty > currentQty) {
            throw new BusinessRuleException("INVALID_CANCEL_QUANTITY",
                    "Cancel quantity must be between 1 and " + currentQty);
        }

        User canceller = userRepository.findById(currentUser.getId()).orElse(null);
        String reason = request != null ? request.getReason() : null;

        if (cancelQty < currentQty) {
            String cancellerInfo = (canceller != null) ? canceller.getUsername() + " (#" + canceller.getId() + ")"
                    : currentUser.getUsername() + " (#" + currentUser.getId() + ")";
            String itemName = (item.getMenuItem() != null) ? item.getMenuItem().getName() : ("Item #" + item.getId());
            Long parentOrderId = (item.getOrder() != null) ? item.getOrder().getId() : null;
            String movementNote = "Hoàn kho - Hủy Đơn #" + parentOrderId + " (" + itemName + " x" + cancelQty + ") • Bởi: " + cancellerInfo;

            restoreStockForItem(item, cancelQty, currentQty, movementNote);

            item.setQuantity(currentQty - cancelQty);
            orderItemRepository.save(item);

            OrderItem cancelledSplitItem = OrderItem.builder()
                    .order(item.getOrder())
                    .menuItem(item.getMenuItem())
                    .quantity(cancelQty)
                    .unitPrice(item.getUnitPrice())
                    .taxRate(item.getTaxRate())
                    .isVatInclusive(item.getIsVatInclusive())
                    .note(item.getNote())
                    .status(OrderItemStatus.CANCELLED)
                    .terminationReason(reason)
                    .terminatedBy(canceller)
                    .terminatedAt(LocalDateTime.now())
                    .build();
            OrderItem savedCancelledItem = orderItemRepository.save(cancelledSplitItem);

            orderEventPublisher.publishOrderItemStatusChanged(
                    savedCancelledItem, OrderItemStatus.PENDING, OrderItemStatus.CANCELLED);

            return mapToOrderItemResponse(savedCancelledItem);
        }

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
        int currentQty = (item.getQuantity() != null && item.getQuantity() > 0) ? item.getQuantity() : 1;
        String cancellerInfo = (canceller != null) ? canceller.getUsername() + " (#" + canceller.getId() + ")" : "Hệ thống";
        String itemName = (item.getMenuItem() != null) ? item.getMenuItem().getName() : ("Item #" + item.getId());
        Long parentOrderId = (item.getOrder() != null) ? item.getOrder().getId() : null;
        String movementNote = "Hoàn kho - Hủy Đơn #" + parentOrderId + " (" + itemName + ") • Bởi: " + cancellerInfo;

        restoreStockForItem(item, currentQty, currentQty, movementNote);

        item.setStatus(OrderItemStatus.CANCELLED);
        item.setTerminationReason(reason);
        item.setTerminatedBy(canceller);
        item.setTerminatedAt(LocalDateTime.now());
        orderItemRepository.save(item);
    }

    /**
     * Restore stock proportionally (or fully) based on remaining net ORDER_CONSUMPTION movements.
     */
    private void restoreStockForItem(OrderItem item, int cancelQty, int currentQty, String movementNote) {
        List<StockMovement> originalMovements = stockMovementRepository.findByOrderItemId(item.getId());

        Map<Long, BigDecimal> netConsumedByIngId = new LinkedHashMap<>();
        for (StockMovement m : originalMovements) {
            if (m.getMovementType() == StockMovementType.ORDER_CONSUMPTION && m.getIngredient() != null) {
                Long ingId = m.getIngredient().getId();
                BigDecimal consumedDelta = m.getQuantityChange().negate();
                netConsumedByIngId.merge(ingId, consumedDelta, BigDecimal::add);
            }
        }

        List<Long> ingIdsToRestore = netConsumedByIngId.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (ingIdsToRestore.isEmpty()) {
            return;
        }

        Map<Long, Ingredient> lockedIngredients = new LinkedHashMap<>();
        for (Long ingId : ingIdsToRestore) {
            Ingredient ing = ingredientRepository.findByIdForUpdate(ingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + ingId));
            refreshEntity(ing);
            lockedIngredients.put(ingId, ing);
        }

        for (Long ingId : ingIdsToRestore) {
            BigDecimal netConsumed = netConsumedByIngId.get(ingId);
            BigDecimal toRestore = (cancelQty == currentQty)
                    ? netConsumed
                    : netConsumed.multiply(BigDecimal.valueOf(cancelQty))
                            .divide(BigDecimal.valueOf(currentQty), 3, java.math.RoundingMode.HALF_UP);

            if (toRestore.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Ingredient ing = lockedIngredients.get(ingId);
            BigDecimal newBalance = ing.getCurrentStock().add(toRestore);
            ing.setCurrentStock(newBalance);
            ingredientRepository.save(ing);

            StockMovement compensating = StockMovement.builder()
                    .ingredient(ing)
                    .movementType(StockMovementType.ORDER_CONSUMPTION)
                    .quantityChange(toRestore) // Positive for restoration
                    .balanceAfter(newBalance)
                    .orderItem(item)
                    .note(movementNote)
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
        int returned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
        int effectiveQty = Math.max(0, (item.getQuantity() != null ? item.getQuantity() : 0) - returned);
        BigDecimal subtotal = (item.getUnitPrice() != null)
                ? item.getUnitPrice().multiply(BigDecimal.valueOf(effectiveQty))
                : BigDecimal.ZERO;

        return OrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                .menuItemName(item.getMenuItem() != null ? item.getMenuItem().getName() : null)
                .quantity(item.getQuantity())
                .returnedQuantity(returned)
                .isDirectSale(item.getMenuItem() != null ? item.getMenuItem().getIsDirectSale() : false)
                .unitPrice(item.getUnitPrice())
                .subtotal(subtotal)
                .taxRate(item.getTaxRate())
                .isVatInclusive(item.getIsVatInclusive())
                .status(item.getStatus())
                .note(item.getNote())
                .terminationReason(item.getTerminationReason())
                .terminatedById(item.getTerminatedBy() != null ? item.getTerminatedBy().getId() : null)
                .terminatedAt(item.getTerminatedAt())
                .returnedById(item.getReturnedBy() != null ? item.getReturnedBy().getId() : null)
                .returnedByName(item.getReturnedBy() != null ? item.getReturnedBy().getFullName() : null)
                .createdAt(item.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectSaleItemDto> getSessionDirectSaleItems(Long sessionId) {
        if (!serviceSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Service session not found with id: " + sessionId);
        }
        List<OrderItem> items = orderItemRepository.findDirectSaleItemsBySessionId(sessionId);
        return items.stream().map(this::mapToDirectSaleItemDto).toList();
    }

    @Override
    @Transactional
    public List<DirectSaleItemDto> returnSessionDirectSaleItems(Long sessionId, ReturnOrderItemsRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.SERVICE_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Only service staff or owner can process item returns");
        }

        ServiceSession session = serviceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("SESSION_NOT_ACTIVE", "Cannot return items for a closed service session");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("EMPTY_ITEMS", "No items provided for return");
        }

        // 1. Lock all target order items in ascending ID order
        List<Long> itemIds = request.getItems().stream()
                .map(ReturnOrderItemsRequest.ItemReturnDto::getOrderItemId)
                .distinct()
                .sorted()
                .toList();

        List<OrderItem> lockedItems = orderItemRepository.findAllByIdInForUpdate(itemIds);
        Map<Long, OrderItem> itemMap = lockedItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, item -> item));

        if (itemMap.size() != itemIds.size()) {
            throw new ResourceNotFoundException("One or more order items not found");
        }

        // Map request return qty by orderItemId
        Map<Long, Integer> returnQtyByItemId = request.getItems().stream()
                .collect(Collectors.toMap(
                        ReturnOrderItemsRequest.ItemReturnDto::getOrderItemId,
                        ReturnOrderItemsRequest.ItemReturnDto::getReturnQuantity,
                        Integer::sum
                ));

        // 2. Validate items
        Set<Long> menuItemIds = new HashSet<>();
        for (Long itemId : itemIds) {
            OrderItem item = itemMap.get(itemId);
            refreshEntity(item);

            if (item.getOrder() == null || item.getOrder().getServiceSession() == null
                    || !item.getOrder().getServiceSession().getId().equals(sessionId)) {
                throw new BusinessRuleException("INVALID_ORDER_ITEM",
                        "Order item #" + itemId + " does not belong to session #" + sessionId);
            }

            if (item.getStatus() == OrderItemStatus.CANCELLED) {
                throw new BusinessRuleException("ITEM_CANCELLED",
                        "Order item #" + itemId + " has been cancelled and cannot be returned");
            }

            if (item.getMenuItem() == null || !Boolean.TRUE.equals(item.getMenuItem().getIsDirectSale())) {
                throw new BusinessRuleException("NOT_DIRECT_SALE",
                        "Order item #" + itemId + " (" + (item.getMenuItem() != null ? item.getMenuItem().getName() : "")
                                + ") is not a direct-sale item and cannot be returned");
            }

            int currentReturned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
            int addReturn = returnQtyByItemId.get(itemId);
            if (addReturn <= 0) {
                throw new BusinessRuleException("INVALID_RETURN_QUANTITY", "Return quantity must be greater than 0");
            }

            if (currentReturned + addReturn > item.getQuantity()) {
                throw new BusinessRuleException("RETURN_EXCEEDS_QUANTITY",
                        "Total returned quantity (" + (currentReturned + addReturn)
                                + ") cannot exceed ordered quantity (" + item.getQuantity() + ") for item #" + itemId);
            }

            menuItemIds.add(item.getMenuItem().getId());
        }

        // 3. Batch load recipes for affected menu items (Zero N+1 query)
        List<RecipeItem> allRecipes = recipeItemRepository.findByMenuItemIdInWithIngredient(menuItemIds);
        Map<Long, List<RecipeItem>> recipesByMenuItem = allRecipes.stream()
                .collect(Collectors.groupingBy(r -> r.getMenuItem().getId()));

        // 4. Calculate total ingredients to restore across all items
        Map<Long, BigDecimal> qtyToRestoreByIngId = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            OrderItem item = itemMap.get(itemId);
            int returnQty = returnQtyByItemId.get(itemId);
            List<RecipeItem> recipes = recipesByMenuItem.getOrDefault(item.getMenuItem().getId(), Collections.emptyList());
            for (RecipeItem recipe : recipes) {
                if (recipe.getIngredient() != null) {
                    BigDecimal toRestore = recipe.getQuantityRequired().multiply(BigDecimal.valueOf(returnQty));
                    qtyToRestoreByIngId.merge(recipe.getIngredient().getId(), toRestore, BigDecimal::add);
                }
            }
        }

        // 5. DEADLOCK PREVENTION: Sort ingredient IDs ascending prior to pessimistic write lock
        List<Long> sortedIngIds = qtyToRestoreByIngId.keySet().stream().sorted().toList();
        Map<Long, Ingredient> lockedIngredients = new LinkedHashMap<>();
        for (Long ingId : sortedIngIds) {
            Ingredient ing = ingredientRepository.findByIdForUpdate(ingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + ingId));
            refreshEntity(ing);
            lockedIngredients.put(ingId, ing);
        }

        // 6. Restore ingredient stock and record StockMovement (ORDER_RETURN)
        User currentUserEntity = userRepository.findById(currentUser.getId()).orElse(null);
        String userSnapshot = currentUser.getUsername() + " (#" + currentUser.getId() + ")";

        for (Long itemId : itemIds) {
            OrderItem item = itemMap.get(itemId);
            int returnQty = returnQtyByItemId.get(itemId);
            int currentReturned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;

            item.setReturnedQuantity(currentReturned + returnQty);
            item.setReturnedBy(currentUserEntity);
            orderItemRepository.save(item);

            List<RecipeItem> recipes = recipesByMenuItem.getOrDefault(item.getMenuItem().getId(), Collections.emptyList());
            for (RecipeItem recipe : recipes) {
                if (recipe.getIngredient() == null) continue;
                Long ingId = recipe.getIngredient().getId();
                Ingredient ing = lockedIngredients.get(ingId);

                BigDecimal returnDelta = recipe.getQuantityRequired().multiply(BigDecimal.valueOf(returnQty));
                BigDecimal newBalance = ing.getCurrentStock().add(returnDelta);
                ing.setCurrentStock(newBalance);
                ingredientRepository.save(ing);

                String note = "Trả đồ Đơn #" + item.getOrder().getId() + " (" + item.getMenuItem().getName()
                        + " x" + returnQty + ") • Bởi: " + userSnapshot;

                StockMovement movement = StockMovement.builder()
                        .ingredient(ing)
                        .movementType(StockMovementType.ORDER_RETURN)
                        .quantityChange(returnDelta)
                        .balanceAfter(newBalance)
                        .orderItem(item)
                        .note(note)
                        .build();
                stockMovementRepository.save(movement);
            }

            // Publish item change event to session and kitchen topics
            orderEventPublisher.publishOrderItemStatusChanged(item, item.getStatus(), item.getStatus());
        }

        log.info("Returned direct sale items for session id={} by user={}", sessionId, userSnapshot);

        return getSessionDirectSaleItems(sessionId);
    }

    private DirectSaleItemDto mapToDirectSaleItemDto(OrderItem item) {
        int ordered = item.getQuantity() != null ? item.getQuantity() : 0;
        int returned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
        int used = Math.max(0, ordered - returned);
        BigDecimal subtotal = (item.getUnitPrice() != null)
                ? item.getUnitPrice().multiply(BigDecimal.valueOf(used))
                : BigDecimal.ZERO;

        return DirectSaleItemDto.builder()
                .orderItemId(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null)
                .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                .menuItemName(item.getMenuItem() != null ? item.getMenuItem().getName() : null)
                .orderedQuantity(ordered)
                .returnedQuantity(returned)
                .usedQuantity(used)
                .unitPrice(item.getUnitPrice())
                .subtotal(subtotal)
                .status(item.getStatus())
                .orderCreatedAt(item.getOrder() != null ? item.getOrder().getCreatedAt() : null)
                .build();
    }
}
