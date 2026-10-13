package com.roms.service.impl;

import com.roms.dto.event.OrderItemStatusChangedEvent;
import com.roms.dto.event.OrderStatusChangedEvent;
import com.roms.dto.request.UpdateOrderItemStatusRequest;
import com.roms.dto.response.KitchenOrderResponse;
import com.roms.dto.response.OrderItemResponse;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.KitchenService;
import com.roms.websocket.OrderEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenServiceImpl implements KitchenService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
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
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getKitchenOrders(OrderStatus status) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.KITCHEN_STAFF && role != UserRole.OWNER && role != UserRole.SERVICE_STAFF) {
            throw new ForbiddenException("Role '" + role + "' cannot view kitchen orders");
        }

        List<Order> orders = orderRepository.findOrdersFiltered(null, status);
        return orders.stream()
                .map(this::mapToKitchenOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public KitchenOrderResponse getKitchenOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        return mapToKitchenOrderResponse(order);
    }

    @Override
    @Transactional
    public KitchenOrderResponse acceptOrder(Long id) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.KITCHEN_STAFF && role != UserRole.OWNER) {
            throw new ForbiddenException("Only KITCHEN_STAFF or OWNER can accept orders");
        }

        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        refreshEntity(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("INVALID_ORDER_STATUS",
                    "Only PENDING orders can be accepted into COOKING state. Current status: " + order.getStatus());
        }

        // Transition order status to COOKING (no stock deduction!)
        order.setStatus(OrderStatus.COOKING);
        Order savedOrder = orderRepository.save(order);

        // Broadcast STOMP event after commit
        orderEventPublisher.publishOrderStatusChanged(savedOrder, OrderStatus.PENDING, OrderStatus.COOKING);

        return mapToKitchenOrderResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderItemResponse updateOrderItemStatus(Long orderId, Long itemId, UpdateOrderItemStatusRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        UserRole role = currentUser.getRole();
        if (role != UserRole.KITCHEN_STAFF && role != UserRole.OWNER && role != UserRole.SERVICE_STAFF) {
            throw new ForbiddenException("Role '" + role + "' cannot update order item status");
        }

        OrderItem item = orderItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + itemId));
        refreshEntity(item);

        if (!item.getOrder().getId().equals(orderId)) {
            throw new ResourceNotFoundException("Order item #" + itemId + " does not belong to Order #" + orderId);
        }

        OrderItemStatus currentStatus = item.getStatus();
        OrderItemStatus targetStatus = request.getStatus();

        // Validate state transitions & role permissions
        validateItemTransition(role, currentStatus, targetStatus);

        item.setStatus(targetStatus);
        OrderItem savedItem = orderItemRepository.save(item);

        Order parentOrder = item.getOrder();
        Long sessionId = parentOrder.getServiceSession() != null ? parentOrder.getServiceSession().getId() : null;
        Long tableId = (parentOrder.getServiceSession() != null && parentOrder.getServiceSession().getTable() != null)
                ? parentOrder.getServiceSession().getTable().getId() : null;

        // Publish STOMP event for item status transition
        orderEventPublisher.publishOrderItemStatusChanged(savedItem, currentStatus, targetStatus);

        // If item transitioned to COOKING while parent order is PENDING, advance parent to COOKING
        if (targetStatus == OrderItemStatus.COOKING && parentOrder.getStatus() == OrderStatus.PENDING) {
            parentOrder.setStatus(OrderStatus.COOKING);
            orderRepository.save(parentOrder);
            orderEventPublisher.publishOrderStatusChanged(parentOrder, OrderStatus.PENDING, OrderStatus.COOKING);
        }

        // Evaluate parent order completion: all non-cancelled items must be COMPLETED and count > 0
        List<OrderItem> allItems = orderItemRepository.findByOrderId(parentOrder.getId());
        List<OrderItem> nonCancelledItems = allItems.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .toList();

        if (!nonCancelledItems.isEmpty() && nonCancelledItems.stream().allMatch(i -> i.getStatus() == OrderItemStatus.COMPLETED)) {
            if (parentOrder.getStatus() != OrderStatus.COMPLETED) {
                OrderStatus oldOrderStatus = parentOrder.getStatus();
                parentOrder.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(parentOrder);
                orderEventPublisher.publishOrderStatusChanged(parentOrder, oldOrderStatus, OrderStatus.COMPLETED);
            }
        }

        return mapToOrderItemResponse(savedItem);
    }

    private void validateItemTransition(UserRole role, OrderItemStatus current, OrderItemStatus target) {
        if (current == OrderItemStatus.PENDING && target == OrderItemStatus.COOKING) {
            if (role != UserRole.KITCHEN_STAFF && role != UserRole.OWNER) {
                throw new ForbiddenException("Only KITCHEN_STAFF or OWNER can start cooking an item");
            }
            return;
        }

        if (current == OrderItemStatus.COOKING && target == OrderItemStatus.READY) {
            if (role != UserRole.KITCHEN_STAFF && role != UserRole.OWNER) {
                throw new ForbiddenException("Only KITCHEN_STAFF or OWNER can mark an item READY");
            }
            return;
        }

        if (current == OrderItemStatus.READY && target == OrderItemStatus.COMPLETED) {
            // Both SERVICE_STAFF, KITCHEN_STAFF and OWNER can complete/serve the dish
            return;
        }

        throw new BusinessRuleException("INVALID_ORDER_ITEM_TRANSITION",
                "Invalid status transition from " + current + " to " + target);
    }

    private KitchenOrderResponse mapToKitchenOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithMenuItem(order.getId());
        List<OrderItemResponse> itemResponses = items.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .map(this::mapToOrderItemResponse)
                .toList();

        Long sessionId = order.getServiceSession() != null ? order.getServiceSession().getId() : null;
        Long tableId = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getId() : null;
        String tableCode = (order.getServiceSession() != null && order.getServiceSession().getTable() != null)
                ? order.getServiceSession().getTable().getTableCode() : null;

        return KitchenOrderResponse.builder()
                .id(order.getId())
                .sessionId(sessionId)
                .tableId(tableId)
                .tableCode(tableCode)
                .status(order.getStatus())
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
