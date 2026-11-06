package com.roms.service.impl;

import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.request.PaymentItemDto;
import com.roms.dto.response.*;
import com.roms.entity.*;
import com.roms.enums.DiscountType;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.*;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.BillService;
import com.roms.service.RestaurantSettingService;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    private final BillRepository billRepository;
    private final ServiceSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PromotionRepository promotionRepository;
    private final UserRepository userRepository;
    private final RestaurantSettingService restaurantSettingService;
    private final StompEventPublisher stompEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public BillPreviewResponse getBillPreview(Long sessionId) {
        ServiceSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("Service session is not ACTIVE", "SESSION_NOT_ACTIVE");
        }

        List<OrderItem> allItems = orderItemRepository.findAllBySessionIdWithMenuItem(sessionId);

        // Precondition check: all non-cancelled order items must be COMPLETED
        boolean hasUnfinished = allItems.stream()
                .anyMatch(i -> i.getStatus() == OrderItemStatus.PENDING
                        || i.getStatus() == OrderItemStatus.COOKING
                        || i.getStatus() == OrderItemStatus.READY);
        if (hasUnfinished) {
            throw new BusinessRuleException(
                    "Bàn vẫn còn món đang chờ chế biến hoặc chờ bưng (PENDING/COOKING/READY). Vui lòng hoàn tất hoặc hủy trước khi tính tiền.",
                    "SESSION_HAS_UNFINISHED_ITEMS");
        }

        // Filter effective completed items
        List<BillPreviewItemDto> previewItems = allItems.stream()
                .filter(i -> i.getStatus() == OrderItemStatus.COMPLETED)
                .map(i -> {
                    int returnedQty = i.getReturnedQuantity() != null ? i.getReturnedQuantity() : 0;
                    int effectiveQty = (i.getQuantity() != null ? i.getQuantity() : 0) - returnedQty;
                    if (effectiveQty <= 0) return null;

                    BigDecimal unitPrice = i.getUnitPrice() != null ? i.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(effectiveQty));

                    return BillPreviewItemDto.builder()
                            .orderItemId(i.getId())
                            .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                            .itemName(i.getMenuItem() != null ? i.getMenuItem().getName() : "Món")
                            .quantity(effectiveQty)
                            .unitPrice(unitPrice)
                            .taxRate(i.getTaxRate() != null ? i.getTaxRate() : BigDecimal.ZERO)
                            .isVatInclusive(Boolean.TRUE.equals(i.getIsVatInclusive()))
                            .subtotal(subtotal)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        BigDecimal subtotal = previewItems.stream()
                .map(BillPreviewItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean menuPriceIncludesVat = restaurantSettingService.isMenuPriceIncludesVat();
        List<TaxBreakdownDto> taxBreakdowns = new ArrayList<>();
        BigDecimal totalTax = BigDecimal.ZERO;

        if (!menuPriceIncludesVat) {
            // Group by taxRate
            Map<BigDecimal, BigDecimal> taxGroup = previewItems.stream()
                    .collect(Collectors.groupingBy(
                            BillPreviewItemDto::getTaxRate,
                            Collectors.reducing(BigDecimal.ZERO, BillPreviewItemDto::getSubtotal, BigDecimal::add)
                    ));

            List<BigDecimal> sortedRates = new ArrayList<>(taxGroup.keySet());
            Collections.sort(sortedRates);

            for (BigDecimal rate : sortedRates) {
                BigDecimal taxableAmt = taxGroup.get(rate);
                BigDecimal taxAmt = taxableAmt.multiply(rate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                taxBreakdowns.add(TaxBreakdownDto.builder()
                        .rate(rate)
                        .taxableAmount(taxableAmt)
                        .taxAmount(taxAmt)
                        .build());
                totalTax = totalTax.add(taxAmt);
            }
        }

        BigDecimal amountBeforeDiscount = subtotal.add(totalTax);

        // Fetch valid promotions
        List<Promotion> validPromos = promotionRepository.findValidPromotions(LocalDate.now());
        List<PromotionDto> availablePromos = validPromos.stream()
                .map(p -> {
                    long usedCount = promotionRepository.countUsedBills(p.getId());
                    if (p.getMaxUses() != null && usedCount >= p.getMaxUses()) {
                        return null;
                    }
                    return PromotionDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .code(p.getCode())
                            .discountType(p.getDiscountType())
                            .discountValue(p.getDiscountValue())
                            .maxUses(p.getMaxUses())
                            .usedCount(usedCount)
                            .startDate(p.getStartDate())
                            .endDate(p.getEndDate())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return BillPreviewResponse.builder()
                .sessionId(session.getId())
                .tableCode(session.getTable().getTableCode())
                .openedAt(session.getOpenedAt())
                .items(previewItems)
                .subtotal(subtotal)
                .menuPriceIncludesVat(menuPriceIncludesVat)
                .taxBreakdowns(taxBreakdowns)
                .totalTax(totalTax)
                .amountBeforeDiscount(amountBeforeDiscount)
                .availablePromotions(availablePromos)
                .build();
    }

    @Override
    @Transactional
    public BillResponse checkout(Long sessionId, CheckoutBillRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        User createdByUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        // Pessimistic write lock on service session to prevent double-checkout race conditions
        ServiceSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));

        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException("Service session is not ACTIVE", "SESSION_NOT_ACTIVE");
        }

        if (billRepository.findByServiceSessionId(sessionId).isPresent()) {
            throw new BusinessRuleException("Phiên này đã có hóa đơn thanh toán", "BILL_ALREADY_EXISTS");
        }

        List<OrderItem> allItems = orderItemRepository.findAllBySessionIdWithMenuItem(sessionId);

        // Precondition check: all non-cancelled order items must be COMPLETED
        boolean hasUnfinished = allItems.stream()
                .anyMatch(i -> i.getStatus() == OrderItemStatus.PENDING
                        || i.getStatus() == OrderItemStatus.COOKING
                        || i.getStatus() == OrderItemStatus.READY);
        if (hasUnfinished) {
            throw new BusinessRuleException(
                    "Bàn vẫn còn món đang chờ chế biến hoặc chờ bưng (PENDING/COOKING/READY). Vui lòng hoàn tất hoặc hủy trước khi thanh toán.",
                    "SESSION_HAS_UNFINISHED_ITEMS");
        }

        List<OrderItem> effectiveOrderItems = allItems.stream()
                .filter(i -> i.getStatus() == OrderItemStatus.COMPLETED)
                .filter(i -> {
                    int returnedQty = i.getReturnedQuantity() != null ? i.getReturnedQuantity() : 0;
                    return ((i.getQuantity() != null ? i.getQuantity() : 0) - returnedQty) > 0;
                })
                .toList();

        if (effectiveOrderItems.isEmpty()) {
            throw new BusinessRuleException("Bàn không có món ăn nào để thanh toán", "EMPTY_BILL");
        }

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem oi : effectiveOrderItems) {
            int effectiveQty = oi.getQuantity() - (oi.getReturnedQuantity() != null ? oi.getReturnedQuantity() : 0);
            BigDecimal itemSubtotal = oi.getUnitPrice().multiply(BigDecimal.valueOf(effectiveQty));
            subtotal = subtotal.add(itemSubtotal);
        }

        boolean menuPriceIncludesVat = restaurantSettingService.isMenuPriceIncludesVat();
        BigDecimal totalTax = BigDecimal.ZERO;

        if (!menuPriceIncludesVat) {
            Map<BigDecimal, BigDecimal> taxGroup = effectiveOrderItems.stream()
                    .collect(Collectors.groupingBy(
                            oi -> oi.getTaxRate() != null ? oi.getTaxRate() : BigDecimal.ZERO,
                            Collectors.reducing(
                                    BigDecimal.ZERO,
                                    oi -> {
                                        int effectiveQty = oi.getQuantity() - (oi.getReturnedQuantity() != null ? oi.getReturnedQuantity() : 0);
                                        return oi.getUnitPrice().multiply(BigDecimal.valueOf(effectiveQty));
                                    },
                                    BigDecimal::add
                            )
                    ));

            for (Map.Entry<BigDecimal, BigDecimal> entry : taxGroup.entrySet()) {
                BigDecimal rate = entry.getKey();
                BigDecimal taxableAmt = entry.getValue();
                BigDecimal taxAmt = taxableAmt.multiply(rate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                totalTax = totalTax.add(taxAmt);
            }
        }

        BigDecimal amountBeforeDiscount = subtotal.add(totalTax);

        // Process Promotion
        Promotion appliedPromo = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getPromotionCode() != null && !request.getPromotionCode().trim().isEmpty()) {
            String code = request.getPromotionCode().trim().toUpperCase();
            appliedPromo = promotionRepository.findByCodeAndIsDeletedFalse(code)
                    .orElseThrow(() -> new BusinessRuleException("Mã khuyến mãi không tồn tại: " + code, "INVALID_PROMOTION_CODE"));

            if (!Boolean.TRUE.equals(appliedPromo.getIsActive())) {
                throw new BusinessRuleException("Mã khuyến mãi đã bị vô hiệu hóa", "PROMOTION_INACTIVE");
            }

            LocalDate today = LocalDate.now();
            if (appliedPromo.getStartDate() != null && today.isBefore(appliedPromo.getStartDate())) {
                throw new BusinessRuleException("Mã khuyến mãi chưa đến ngày bắt đầu", "PROMOTION_NOT_STARTED");
            }
            if (appliedPromo.getEndDate() != null && today.isAfter(appliedPromo.getEndDate())) {
                throw new BusinessRuleException("Mã khuyến mãi đã hết hạn", "PROMOTION_EXPIRED");
            }

            if (appliedPromo.getMaxUses() != null) {
                // Acquire pessimistic write lock to serialize concurrent checkouts using limited promotions
                appliedPromo = promotionRepository.findByIdForUpdate(appliedPromo.getId())
                        .orElseThrow(() -> new BusinessRuleException("Mã khuyến mãi không tồn tại", "INVALID_PROMOTION_CODE"));
                long usedCount = promotionRepository.countUsedBills(appliedPromo.getId());
                if (usedCount >= appliedPromo.getMaxUses()) {
                    throw new BusinessRuleException("Mã khuyến mãi đã hết lượt sử dụng", "PROMOTION_MAX_USES_REACHED");
                }
            }

            if (appliedPromo.getDiscountType() == DiscountType.PERCENT) {
                discountAmount = amountBeforeDiscount.multiply(appliedPromo.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (discountAmount.compareTo(amountBeforeDiscount) > 0) {
                    discountAmount = amountBeforeDiscount;
                }
            } else if (appliedPromo.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                discountAmount = appliedPromo.getDiscountValue().min(amountBeforeDiscount);
            }
        }

        BigDecimal totalAmount = amountBeforeDiscount.subtract(discountAmount);

        // Validate Payments
        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            throw new BusinessRuleException("Bắt buộc phải chọn phương thức thanh toán", "PAYMENT_REQUIRED");
        }

        BigDecimal totalPaid = request.getPayments().stream()
                .map(PaymentItemDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaid.compareTo(totalAmount) != 0) {
            throw new BusinessRuleException(
                    "Tổng số tiền thanh toán (" + totalPaid + ") không khớp với số tiền cần thanh toán (" + totalAmount + ")",
                    "PAYMENT_AMOUNT_MISMATCH");
        }

        // Generate atomic unique bill_code: BILL-YYYYMMDD-XXXX
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int nextSeq = billRepository.findMaxSequenceForDate("BILL-" + dateStr + "-%") + 1;
        String billCode = String.format("BILL-%s-%04d", dateStr, nextSeq);

        // Build Bill
        Bill bill = Bill.builder()
                .billCode(billCode)
                .serviceSession(session)
                .tableCode(session.getTable().getTableCode())
                .amountBeforeDiscount(amountBeforeDiscount)
                .promotion(appliedPromo)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .createdBy(createdByUser)
                .build();

        // Build BillItems
        Set<BillItem> billItems = new LinkedHashSet<>();
        for (OrderItem oi : effectiveOrderItems) {
            int effectiveQty = oi.getQuantity() - (oi.getReturnedQuantity() != null ? oi.getReturnedQuantity() : 0);
            billItems.add(BillItem.builder()
                    .bill(bill)
                    .orderItem(oi)
                    .itemName(oi.getMenuItem().getName())
                    .quantity(effectiveQty)
                    .unitPrice(oi.getUnitPrice())
                    .taxRate(oi.getTaxRate() != null ? oi.getTaxRate() : BigDecimal.ZERO)
                    .isVatInclusive(Boolean.TRUE.equals(oi.getIsVatInclusive()))
                    .build());
        }
        bill.setItems(billItems);

        // Build Payments
        Set<Payment> paymentEntities = new LinkedHashSet<>();
        for (PaymentItemDto pDto : request.getPayments()) {
            paymentEntities.add(Payment.builder()
                    .bill(bill)
                    .paymentMethod(pDto.getPaymentMethod())
                    .amount(pDto.getAmount())
                    .build());
        }
        bill.setPayments(paymentEntities);

        Bill savedBill = billRepository.save(bill);

        // Close service session
        session.setStatus(ServiceSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(createdByUser);
        sessionRepository.save(session);

        // Complete orders of session if not completed
        List<Order> orders = orderRepository.findByServiceSessionId(sessionId);
        for (Order order : orders) {
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.COMPLETED) {
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
            }
        }

        log.info("Bill created successfully: id={}, code={}, totalAmount={}, session={}",
                savedBill.getId(), savedBill.getBillCode(), savedBill.getTotalAmount(), sessionId);

        BillResponse response = mapToBillResponse(savedBill);

        // Realtime notifications via WebSocket STOMP
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

        stompEventPublisher.publishAfterCommit(
                StompDestinations.bills(),
                response
        );

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BillResponse getBillById(Long id) {
        Bill bill = billRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + id));
        return mapToBillResponse(bill);
    }

    @Override
    @Transactional(readOnly = true)
    public BillResponse getBillBySessionId(Long sessionId) {
        Bill bill = billRepository.findByServiceSessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found for session id: " + sessionId));
        return mapToBillResponse(bill);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BillResponse> getBills(Pageable pageable) {
        return billRepository.findAllWithDetails(pageable)
                .map(this::mapToBillResponse);
    }

    private BillResponse mapToBillResponse(Bill bill) {
        List<BillItemResponse> itemResponses = bill.getItems() != null
                ? bill.getItems().stream()
                .map(i -> {
                    BigDecimal subtotal = i.getUnitPrice() != null && i.getQuantity() != null
                            ? i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                            : BigDecimal.ZERO;
                    return BillItemResponse.builder()
                            .id(i.getId())
                            .orderItemId(i.getOrderItem() != null ? i.getOrderItem().getId() : null)
                            .itemName(i.getItemName())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .taxRate(i.getTaxRate())
                            .isVatInclusive(i.getIsVatInclusive())
                            .subtotal(subtotal)
                            .build();
                })
                .toList()
                : Collections.emptyList();

        List<PaymentResponse> paymentResponses = bill.getPayments() != null
                ? bill.getPayments().stream()
                .map(p -> PaymentResponse.builder()
                        .id(p.getId())
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .build())
                .toList()
                : Collections.emptyList();

        Long promotionId = bill.getPromotion() != null ? bill.getPromotion().getId() : null;
        String promotionCode = bill.getPromotion() != null ? bill.getPromotion().getCode() : null;
        String promotionName = bill.getPromotion() != null ? bill.getPromotion().getName() : null;

        Long createdById = bill.getCreatedBy() != null ? bill.getCreatedBy().getId() : null;
        String createdByName = bill.getCreatedBy() != null ? bill.getCreatedBy().getFullName() : null;

        Long sessionId = bill.getServiceSession() != null ? bill.getServiceSession().getId() : null;
        LocalDateTime openedAt = bill.getServiceSession() != null ? bill.getServiceSession().getOpenedAt() : null;
        LocalDateTime closedAt = bill.getServiceSession() != null ? bill.getServiceSession().getClosedAt() : null;

        return BillResponse.builder()
                .id(bill.getId())
                .billCode(bill.getBillCode())
                .sessionId(sessionId)
                .tableCode(bill.getTableCode())
                .amountBeforeDiscount(bill.getAmountBeforeDiscount())
                .promotionId(promotionId)
                .promotionCode(promotionCode)
                .promotionName(promotionName)
                .discountAmount(bill.getDiscountAmount())
                .totalAmount(bill.getTotalAmount())
                .items(itemResponses)
                .payments(paymentResponses)
                .createdById(createdById)
                .createdByName(createdByName)
                .createdAt(bill.getCreatedAt())
                .openedAt(openedAt)
                .closedAt(closedAt)
                .build();
    }
}

