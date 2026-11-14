package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.event.StaffCallEvent;
import com.roms.dto.request.*;
import com.roms.dto.response.*;
import com.roms.entity.*;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.QrOrderRequestStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.mapper.CategoryMapper;
import com.roms.mapper.MenuItemImageMapper;
import com.roms.mapper.MenuItemMapper;
import com.roms.repository.*;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.*;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrOrderingServiceImpl implements QrOrderingService {

    private final RestaurantTableRepository tableRepository;
    private final ServiceSessionRepository sessionRepository;
    private final QrOrderRequestRepository qrOrderRequestRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemImageRepository menuItemImageRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantSettingService restaurantSettingService;
    private final MenuAvailabilityService menuAvailabilityService;
    private final OrderService orderService;
    private final MenuItemMapper menuItemMapper;
    private final MenuItemImageMapper menuItemImageMapper;
    private final CategoryMapper categoryMapper;
    private final StompEventPublisher stompEventPublisher;

    @Override
    @Transactional
    public QrContextResponse resolveContext(String qrToken) {
        RestaurantSettingResponse settings = requireQrOrderingEnabled();
        RestaurantTable table = requireActiveTable(qrToken);
        ServiceSession session = getOrCreateActiveSession(table);

        return QrContextResponse.builder()
                .qrOrderingEnabled(true)
                .tableId(table.getId())
                .tableCode(table.getTableCode())
                .area(table.getArea())
                .sessionId(session.getId())
                .restaurantName(settings.getRestaurantName())
                .address(settings.getAddress())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getCategories(String qrToken, Pageable pageable) {
        requireQrOrderingEnabled();
        requireActiveTable(qrToken);
        return PageResponse.from(categoryRepository.findByIsActiveTrue(pageable), categoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MenuItemReadResponse> getMenu(String qrToken, Long categoryId, Pageable pageable) {
        requireQrOrderingEnabled();
        requireActiveTable(qrToken);

        Set<MenuItemStatus> statuses = EnumSet.of(MenuItemStatus.AVAILABLE, MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        Page<MenuItem> page = menuItemRepository.findCatalog(categoryId, statuses, false, true, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, item -> toMenuReadResponse(item, List.of()));
        }

        List<Long> ids = page.getContent().stream().map(MenuItem::getId).toList();
        Map<Long, List<MenuItemImageResponse>> groupedImages = menuItemImageRepository.findCatalogImages(ids).stream()
                .collect(Collectors.groupingBy(
                        img -> img.getMenuItem().getId(),
                        Collectors.mapping(menuItemImageMapper::toResponse, Collectors.toList())));

        return PageResponse.from(page, item -> toMenuReadResponse(item, groupedImages.getOrDefault(item.getId(), List.of())));
    }

    @Override
    @Transactional
    public QrOrderRequestResponse submitQrOrder(String qrToken, QrOrderRequestCreateRequest request) {
        RestaurantSettingResponse settings = requireQrOrderingEnabled();
        RestaurantTable table = requireActiveTable(qrToken);
        ServiceSession session = getOrCreateActiveSession(table);

        // Batch-load requested menu items (anti-N+1)
        List<Long> requestedIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getMenuItemId)
                .distinct()
                .toList();

        Map<Long, MenuItem> menuItemMap = menuItemRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        // Aggregate total quantity per menu item for light sellability pre-check
        Map<Long, Integer> totalQtyByItem = new LinkedHashMap<>();
        for (CreateOrderItemRequest line : request.getItems()) {
            if (!menuItemMap.containsKey(line.getMenuItemId())) {
                throw new ResourceNotFoundException("Menu item not found with id: " + line.getMenuItemId());
            }
            totalQtyByItem.merge(line.getMenuItemId(), line.getQuantity(), Integer::sum);
        }

        for (Map.Entry<Long, Integer> entry : totalQtyByItem.entrySet()) {
            MenuItem menuItem = menuItemMap.get(entry.getKey());
            MenuItemSellabilityResponse sellability = menuAvailabilityService.checkSellability(entry.getKey(), entry.getValue());
            if (!sellability.isSellable()) {
                throw new BusinessRuleException(
                        "Món '" + menuItem.getName() + "' hiện không đủ nguyên liệu hoặc đang tạm ngưng phục vụ",
                        "MENU_ITEM_NOT_SELLABLE");
            }
        }

        QrOrderRequest qrRequest = QrOrderRequest.builder()
                .serviceSession(session)
                .status(QrOrderRequestStatus.PENDING_CONFIRMATION)
                .build();

        boolean isVatInclusive = !Boolean.FALSE.equals(settings.getMenuPriceIncludesVat());

        for (CreateOrderItemRequest line : request.getItems()) {
            MenuItem menuItem = menuItemMap.get(line.getMenuItemId());
            String note = line.getNote() != null && !line.getNote().isBlank() ? line.getNote().trim() : null;
            java.math.BigDecimal taxRate = menuItem.getTaxCategory() != null && menuItem.getTaxCategory().getRate() != null
                    ? menuItem.getTaxCategory().getRate()
                    : new java.math.BigDecimal("8.00");
            QrOrderRequestItem item = QrOrderRequestItem.builder()
                    .menuItem(menuItem)
                    .quantity(line.getQuantity())
                    .unitPrice(menuItem.getPrice())
                    .taxRate(taxRate)
                    .isVatInclusive(isVatInclusive)
                    .note(note)
                    .build();
            qrRequest.addItem(item);
        }

        QrOrderRequest saved = qrOrderRequestRepository.save(qrRequest);
        QrOrderRequestResponse response = toResponse(saved, session, table);

        stompEventPublisher.publishAfterCommit(StompDestinations.staffQrOrders(), response);
        stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(session.getId()), response);

        log.info("Submitted QR order request id={} for table={} session={}",
                saved.getId(), table.getTableCode(), session.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public QrOrdersTrackingResponse getSessionOrders(String qrToken) {
        requireQrOrderingEnabled();
        RestaurantTable table = requireActiveTable(qrToken);

        Optional<ServiceSession> activeSessionOpt = sessionRepository.findByTableIdAndStatus(
                table.getId(), ServiceSessionStatus.ACTIVE);
        if (activeSessionOpt.isEmpty()) {
            return QrOrdersTrackingResponse.builder()
                    .sessionId(null)
                    .tableId(table.getId())
                    .tableCode(table.getTableCode())
                    .pendingRequests(List.of())
                    .confirmedOrders(List.of())
                    .build();
        }

        ServiceSession session = activeSessionOpt.get();
        List<QrOrderRequestResponse> pendingRequests = qrOrderRequestRepository
                .findByServiceSessionIdWithDetails(session.getId())
                .stream()
                .filter(r -> r.getStatus() == QrOrderRequestStatus.PENDING_CONFIRMATION)
                .map(r -> toResponse(r, session, table))
                .toList();

        List<OrderResponse> confirmedOrders = orderService.getOrders(session.getId(), null);

        return QrOrdersTrackingResponse.builder()
                .sessionId(session.getId())
                .tableId(table.getId())
                .tableCode(table.getTableCode())
                .pendingRequests(pendingRequests)
                .confirmedOrders(confirmedOrders)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void callStaff(String qrToken) {
        requireQrOrderingEnabled();
        RestaurantTable table = requireActiveTable(qrToken);

        StaffCallEvent event = StaffCallEvent.builder()
                .tableId(table.getId())
                .tableCode(table.getTableCode())
                .requestedAt(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();

        stompEventPublisher.send(StompDestinations.serviceStaffCalls(), event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QrOrderRequestResponse> getPendingRequests() {
        return qrOrderRequestRepository.findByStatusWithDetails(QrOrderRequestStatus.PENDING_CONFIRMATION)
                .stream()
                .map(r -> toResponse(r, r.getServiceSession(), r.getServiceSession().getTable()))
                .toList();
    }

    @Override
    @Transactional
    public QrOrderRequestResponse confirmQrOrder(Long requestId) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        QrOrderRequest qrRequest = qrOrderRequestRepository.findByIdForUpdateWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("QR order request not found with id: " + requestId));

        if (qrRequest.getStatus() != QrOrderRequestStatus.PENDING_CONFIRMATION) {
            throw new BusinessRuleException(
                    "QR order request has already been processed",
                    "QR_ORDER_ALREADY_PROCESSED");
        }

        ServiceSession session = qrRequest.getServiceSession();
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("Service session is not ACTIVE", "SESSION_NOT_ACTIVE");
        }

        User staffUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        List<CreateOrderItemRequest> orderItems = qrRequest.getItems().stream()
                .map(i -> CreateOrderItemRequest.builder()
                        .menuItemId(i.getMenuItem().getId())
                        .quantity(i.getQuantity())
                        .note(i.getNote())
                        .build())
                .toList();

        OrderResponse createdOrder = orderService.createOrder(CreateOrderRequest.builder()
                .sessionId(session.getId())
                .items(orderItems)
                .build());

        qrRequest.setStatus(QrOrderRequestStatus.CONFIRMED);
        qrRequest.setConfirmedBy(staffUser);
        qrRequest.setConfirmedOrder(orderRepository.getReferenceById(createdOrder.getId()));

        QrOrderRequest saved = qrOrderRequestRepository.save(qrRequest);
        QrOrderRequestResponse response = toResponse(saved, session, session.getTable());

        stompEventPublisher.publishAfterCommit(StompDestinations.staffQrOrders(), response);
        stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(session.getId()), response);

        log.info("Confirmed QR order request id={} -> orderId={} by staff={}",
                saved.getId(), createdOrder.getId(), principal.getUsername());
        return response;
    }

    @Override
    @Transactional
    public QrOrderRequestResponse cancelQrOrder(Long requestId, CancelQrOrderRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        QrOrderRequest qrRequest = qrOrderRequestRepository.findByIdForUpdateWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("QR order request not found with id: " + requestId));

        if (qrRequest.getStatus() != QrOrderRequestStatus.PENDING_CONFIRMATION) {
            throw new BusinessRuleException(
                    "Yêu cầu gọi món QR này đã được xử lý trước đó",
                    "QR_ORDER_ALREADY_PROCESSED");
        }

        User staffUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        qrRequest.setStatus(QrOrderRequestStatus.CANCELLED);
        qrRequest.setCancelledBy(staffUser);
        qrRequest.setCancellationReason(request.getReason().trim());

        QrOrderRequest saved = qrOrderRequestRepository.save(qrRequest);
        ServiceSession session = saved.getServiceSession();
        QrOrderRequestResponse response = toResponse(saved, session, session.getTable());

        stompEventPublisher.publishAfterCommit(StompDestinations.staffQrOrders(), response);
        stompEventPublisher.publishAfterCommit(StompDestinations.qrSession(session.getId()), response);

        log.info("Cancelled QR order request id={} by staff={}", saved.getId(), principal.getUsername());
        return response;
    }

    private RestaurantSettingResponse requireQrOrderingEnabled() {
        RestaurantSettingResponse settings = restaurantSettingService.getSettings();
        if (!Boolean.TRUE.equals(settings.getQrOrderingEnabled())) {
            throw new BusinessRuleException(
                    "QR ordering is currently disabled",
                    "QR_ORDERING_DISABLED");
        }
        return settings;
    }

    private RestaurantTable requireActiveTable(String qrToken) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired table QR code"));
        if (!Boolean.TRUE.equals(table.getIsActive())) {
            throw new BusinessRuleException(
                    "Table " + table.getTableCode() + " is currently inactive",
                    "TABLE_INACTIVE");
        }
        return table;
    }

    private ServiceSession getOrCreateActiveSession(RestaurantTable table) {
        Optional<ServiceSession> existing = sessionRepository.findByTableIdAndStatus(
                table.getId(), ServiceSessionStatus.ACTIVE);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            ServiceSession created = sessionRepository.saveAndFlush(ServiceSession.builder()
                    .table(table)
                    .openedBy(null)
                    .status(ServiceSessionStatus.ACTIVE)
                    .openedAt(LocalDateTime.now())
                    .build());

            stompEventPublisher.publishAfterCommit(
                    StompDestinations.tables(),
                    SessionStatusChangedEvent.builder()
                            .sessionId(created.getId())
                            .tableId(table.getId())
                            .tableName(table.getTableCode())
                            .status(ServiceSessionStatus.ACTIVE)
                            .occurredAt(created.getOpenedAt())
                            .build());

            log.info("Auto-opened service session id={} via QR for table={}", created.getId(), table.getTableCode());
            return created;
        } catch (DataIntegrityViolationException ex) {
            return sessionRepository.findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                    .orElseThrow(() -> ex);
        }
    }

    private MenuItemReadResponse toMenuReadResponse(MenuItem item, List<MenuItemImageResponse> images) {
        CategoryResponse category = item.getCategory() != null ? categoryMapper.toResponse(item.getCategory()) : null;
        MenuItemSellabilityResponse sellability = menuAvailabilityService.checkSellability(item.getId(), 1);
        return menuItemMapper.toReadResponse(item, category, images, sellability);
    }

    private QrOrderRequestResponse toResponse(QrOrderRequest r, ServiceSession session, RestaurantTable table) {
        List<QrOrderRequestResponse.QrOrderRequestItemResponse> itemResponses = r.getItems().stream()
                .map(i -> QrOrderRequestResponse.QrOrderRequestItemResponse.builder()
                        .id(i.getId())
                        .menuItemId(i.getMenuItem().getId())
                        .menuItemName(i.getMenuItem().getName())
                        .unitPrice(i.getUnitPrice() != null ? i.getUnitPrice() : i.getMenuItem().getPrice())
                        .quantity(i.getQuantity())
                        .note(i.getNote())
                        .build())
                .toList();

        return QrOrderRequestResponse.builder()
                .id(r.getId())
                .serviceSessionId(session.getId())
                .tableId(table.getId())
                .tableCode(table.getTableCode())
                .area(table.getArea())
                .status(r.getStatus())
                .confirmedOrderId(r.getConfirmedOrder() != null ? r.getConfirmedOrder().getId() : null)
                .confirmedById(r.getConfirmedBy() != null ? r.getConfirmedBy().getId() : null)
                .confirmedByName(r.getConfirmedBy() != null ? r.getConfirmedBy().getFullName() : null)
                .cancelledById(r.getCancelledBy() != null ? r.getCancelledBy().getId() : null)
                .cancelledByName(r.getCancelledBy() != null ? r.getCancelledBy().getFullName() : null)
                .cancellationReason(r.getCancellationReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
