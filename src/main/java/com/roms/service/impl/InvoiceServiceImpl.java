package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.request.CheckoutPreviewRequest;
import com.roms.dto.request.CheckoutRequest;
import com.roms.dto.response.CheckoutItemResponse;
import com.roms.dto.response.CheckoutPreviewResponse;
import com.roms.dto.response.InvoiceResponse;
import com.roms.entity.Invoice;
import com.roms.entity.OrderItem;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import com.roms.enums.DiscountType;
import com.roms.enums.OrderStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.mapper.InvoiceMapper;
import com.roms.repository.InvoiceRepository;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.InvoiceService;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ServiceSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final InvoiceMapper invoiceMapper;
    private final StompEventPublisher stompEventPublisher;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              ServiceSessionRepository sessionRepository,
                              OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              UserRepository userRepository,
                              InvoiceMapper invoiceMapper,
                              StompEventPublisher stompEventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.invoiceMapper = invoiceMapper;
        this.stompEventPublisher = stompEventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(CheckoutPreviewRequest request) {
        ServiceSession session = sessionRepository.findByIdWithDetails(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + request.getSessionId()));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("Service session is already closed", "SESSION_ALREADY_CLOSED");
        }

        if (invoiceRepository.existsByServiceSessionId(request.getSessionId())) {
            throw new BusinessRuleException("Invoice already created for this session", "INVOICE_ALREADY_EXISTS");
        }

        boolean hasUnfinishedOrders = orderRepository.existsByServiceSessionIdAndStatusIn(
                request.getSessionId(),
                List.of(OrderStatus.PENDING, OrderStatus.COOKING)
        );
        if (hasUnfinishedOrders) {
            throw new BusinessRuleException("Cannot preview checkout while orders are still pending or cooking", "SESSION_HAS_UNFINISHED_ORDERS");
        }

        List<CheckoutItemResponse> items = getCompletedItemsForSession(request.getSessionId());
        BigDecimal subtotal = items.stream()
                .map(CheckoutItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DiscountCalculation calc = calculateDiscount(subtotal, request.getDiscountType(), request.getDiscountValue());

        String tableCode = session.getTable() != null ? session.getTable().getTableCode() : null;
        Long tableId = session.getTable() != null ? session.getTable().getId() : null;

        return CheckoutPreviewResponse.builder()
                .sessionId(session.getId())
                .tableId(tableId)
                .tableCode(tableCode)
                .subtotal(subtotal)
                .discountType(calc.discountType)
                .discountValue(calc.discountValue)
                .discountAmount(calc.discountAmount)
                .finalAmount(calc.finalAmount)
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public InvoiceResponse checkoutAndCloseSession(CheckoutRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        if (principal.getRole() == UserRole.TABLE_ACCOUNT) {
            throw new BusinessRuleException("TABLE_ACCOUNT is not permitted to checkout and generate invoices", "FORBIDDEN_OPERATION");
        }

        User currentUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        ServiceSession session = sessionRepository.findByIdWithDetails(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + request.getSessionId()));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("Service session is already closed", "SESSION_ALREADY_CLOSED");
        }

        if (invoiceRepository.existsByServiceSessionId(request.getSessionId())) {
            throw new BusinessRuleException("Invoice already created for this session", "INVOICE_ALREADY_EXISTS");
        }

        boolean hasUnfinishedOrders = orderRepository.existsByServiceSessionIdAndStatusIn(
                request.getSessionId(),
                List.of(OrderStatus.PENDING, OrderStatus.COOKING)
        );
        if (hasUnfinishedOrders) {
            throw new BusinessRuleException("Cannot checkout while orders are still pending or cooking", "SESSION_HAS_UNFINISHED_ORDERS");
        }

        List<CheckoutItemResponse> items = getCompletedItemsForSession(request.getSessionId());
        BigDecimal subtotal = items.stream()
                .map(CheckoutItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DiscountCalculation calc = calculateDiscount(subtotal, request.getDiscountType(), request.getDiscountValue());

        String invoiceCode = generateInvoiceCode(session.getId());

        Invoice invoice = Invoice.builder()
                .invoiceCode(invoiceCode)
                .serviceSession(session)
                .subtotal(subtotal)
                .discountType(calc.discountType)
                .discountValue(calc.discountValue)
                .discountAmount(calc.discountAmount)
                .discountReason(request.getDiscountReason())
                .finalAmount(calc.finalAmount)
                .note(request.getNote())
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        session.setStatus(ServiceSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(currentUser);
        sessionRepository.save(session);

        log.info("Invoice created id: {}, code: {}, finalAmount: {} for session id: {} by user: {}",
                savedInvoice.getId(), savedInvoice.getInvoiceCode(), savedInvoice.getFinalAmount(),
                session.getId(), principal.getUsername());

        stompEventPublisher.publishAfterCommit(
                StompDestinations.tables(),
                SessionStatusChangedEvent.builder()
                        .sessionId(session.getId())
                        .tableId(session.getTable().getId())
                        .tableName(session.getTable().getTableCode())
                        .status(ServiceSessionStatus.CLOSED)
                        .occurredAt(session.getClosedAt())
                        .build()
        );

        return invoiceMapper.toResponse(savedInvoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));

        validateTableAccess(invoice.getServiceSession());

        List<CheckoutItemResponse> items = getCompletedItemsForSession(invoice.getServiceSession().getId());
        return invoiceMapper.toResponse(invoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceBySessionId(Long sessionId) {
        Invoice invoice = invoiceRepository.findBySessionIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for session id: " + sessionId));

        validateTableAccess(invoice.getServiceSession());

        List<CheckoutItemResponse> items = getCompletedItemsForSession(sessionId);
        return invoiceMapper.toResponse(invoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> getInvoices(Long tableId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessRuleException("Start date cannot be after end date", "INVALID_DATE_RANGE");
        }

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        Page<Invoice> page = invoiceRepository.findWithFilters(tableId, startDateTime, endDateTime, pageable);
        return PageResponse.from(page, inv -> invoiceMapper.toResponse(inv, null));
    }

    private void validateTableAccess(ServiceSession session) {
        SecurityUtils.getCurrentUserPrincipal().ifPresent(principal -> {
            if (principal.getRole() == UserRole.TABLE_ACCOUNT) {
                if (principal.getTableId() == null || session.getTable() == null
                        || !principal.getTableId().equals(session.getTable().getId())) {
                    throw new BusinessRuleException("TABLE_ACCOUNT can only view invoices of their assigned table", "TABLE_NOT_ASSIGNED");
                }
            }
        });
    }

    private List<CheckoutItemResponse> getCompletedItemsForSession(Long sessionId) {
        List<OrderItem> items = orderItemRepository.findCompletedItemsBySessionId(sessionId);
        Map<String, CheckoutItemResponse> map = new LinkedHashMap<>();
        for (OrderItem oi : items) {
            if (oi.getMenuItem() == null) continue;
            String key = oi.getMenuItem().getId() + "_" + oi.getUnitPrice();
            if (map.containsKey(key)) {
                CheckoutItemResponse existing = map.get(key);
                existing.setQuantity(existing.getQuantity() + oi.getQuantity());
                existing.setSubtotal(existing.getSubtotal().add(
                        oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity()))
                ));
            } else {
                BigDecimal lineTotal = oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity()));
                map.put(key, CheckoutItemResponse.builder()
                        .menuItemId(oi.getMenuItem().getId())
                        .menuItemName(oi.getMenuItem().getName())
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .subtotal(lineTotal)
                        .build());
            }
        }
        return new ArrayList<>(map.values());
    }

    private DiscountCalculation calculateDiscount(BigDecimal subtotal, DiscountType discountType, BigDecimal discountValue) {
        if (discountType == null) {
            discountType = DiscountType.NONE;
        }
        if (discountValue == null) {
            discountValue = BigDecimal.ZERO;
        }

        if (discountValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Discount value cannot be negative", "INVALID_DISCOUNT_VALUE");
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountType == DiscountType.PERCENTAGE) {
            if (discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessRuleException("Percentage discount cannot exceed 100%", "INVALID_DISCOUNT_PERCENTAGE");
            }
            discountAmount = subtotal.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (discountType == DiscountType.FIXED_AMOUNT) {
            discountAmount = discountValue.min(subtotal);
        } else {
            discountValue = BigDecimal.ZERO;
            discountAmount = BigDecimal.ZERO;
        }

        BigDecimal finalAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        return new DiscountCalculation(discountType, discountValue, discountAmount, finalAmount);
    }

    private String generateInvoiceCode(Long sessionId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String candidate = String.format("INV-%s-%06d", datePart, sessionId);
        if (invoiceRepository.findByInvoiceCode(candidate).isPresent()) {
            candidate = candidate + "-" + (System.currentTimeMillis() % 10000);
        }
        return candidate;
    }

    private record DiscountCalculation(DiscountType discountType, BigDecimal discountValue,
                                      BigDecimal discountAmount, BigDecimal finalAmount) {}
}

