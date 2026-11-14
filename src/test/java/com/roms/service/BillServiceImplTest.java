package com.roms.service;

import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.request.PaymentItemDto;
import com.roms.dto.response.BillPreviewResponse;
import com.roms.dto.response.BillResponse;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.BillServiceImpl;
import com.roms.websocket.StompEventPublisher;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    @Mock
    private BillRepository billRepository;

    @Mock
    private ServiceSessionRepository sessionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private QrOrderRequestRepository qrOrderRequestRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantSettingService restaurantSettingService;

    @Mock
    private StompEventPublisher stompEventPublisher;

    @InjectMocks
    private BillServiceImpl billService;

    private User ownerUser;
    private RestaurantTable table;
    private ServiceSession activeSession;
    private MenuItem beerItem;
    private MenuItem beefItem;
    private OrderItem completedBeer;
    private OrderItem completedBeef;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder()
                .id(1L)
                .username("owner")
                .fullName("Owner Test")
                .role(UserRole.OWNER)
                .build();

        UserPrincipal principal = UserPrincipal.create(ownerUser, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        table = RestaurantTable.builder()
                .id(10L)
                .tableCode("T01")
                .isActive(true)
                .build();

        activeSession = ServiceSession.builder()
                .id(100L)
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedAt(LocalDateTime.now().minusHours(1))
                .openedBy(ownerUser)
                .build();

        beerItem = MenuItem.builder()
                .id(50L)
                .name("Bia Heineken")
                .isDirectSale(true)
                .build();

        beefItem = MenuItem.builder()
                .id(51L)
                .name("Bò bít tết")
                .isDirectSale(false)
                .build();

        completedBeer = OrderItem.builder()
                .id(201L)
                .menuItem(beerItem)
                .quantity(10)
                .returnedQuantity(3) // 7 effective
                .unitPrice(new BigDecimal("30000"))
                .taxRate(new BigDecimal("10.00"))
                .isVatInclusive(true)
                .status(OrderItemStatus.COMPLETED)
                .build();

        completedBeef = OrderItem.builder()
                .id(202L)
                .menuItem(beefItem)
                .quantity(2)
                .returnedQuantity(0) // 2 effective
                .unitPrice(new BigDecimal("150000"))
                .taxRate(new BigDecimal("8.00"))
                .isVatInclusive(true)
                .status(OrderItemStatus.COMPLETED)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getBillPreview - thành công khi menu price bao gồm VAT")
    void getBillPreview_VatInclusive_Success() {
        when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, completedBeef));
        when(restaurantSettingService.isMenuPriceIncludesVat()).thenReturn(true);
        when(promotionRepository.findValidPromotions(any(LocalDate.class))).thenReturn(Collections.emptyList());

        BillPreviewResponse preview = billService.getBillPreview(100L);

        assertThat(preview).isNotNull();
        assertThat(preview.getSessionId()).isEqualTo(100L);
        assertThat(preview.getTableCode()).isEqualTo("T01");
        assertThat(preview.getItems()).hasSize(2);

        // Beer: 7 * 30,000 = 210,000
        // Beef: 2 * 150,000 = 300,000
        // Total subtotal: 510,000
        assertThat(preview.getSubtotal()).isEqualByComparingTo(new BigDecimal("510000"));
        assertThat(preview.getMenuPriceIncludesVat()).isTrue();
        assertThat(preview.getTaxBreakdowns()).isEmpty();
        assertThat(preview.getTotalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.getAmountBeforeDiscount()).isEqualByComparingTo(new BigDecimal("510000"));
    }

    @Test
    @DisplayName("getBillPreview - thành công khi menu price KHÔNG gồm VAT (phân rã dòng thuế 8% và 10%)")
    void getBillPreview_VatExclusive_Success() {
        when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, completedBeef));
        when(restaurantSettingService.isMenuPriceIncludesVat()).thenReturn(false);
        when(promotionRepository.findValidPromotions(any(LocalDate.class))).thenReturn(Collections.emptyList());

        BillPreviewResponse preview = billService.getBillPreview(100L);

        assertThat(preview).isNotNull();
        assertThat(preview.getMenuPriceIncludesVat()).isFalse();
        assertThat(preview.getTaxBreakdowns()).hasSize(2);

        // Tax 8% on Beef: 300,000 * 8% = 24,000
        // Tax 10% on Beer: 210,000 * 10% = 21,000
        // Total tax: 45,000
        // Amount before discount: 510,000 + 45,000 = 555,000
        assertThat(preview.getTotalTax()).isEqualByComparingTo(new BigDecimal("45000.00"));
        assertThat(preview.getAmountBeforeDiscount()).isEqualByComparingTo(new BigDecimal("555000.00"));
    }

    @Test
    @DisplayName("getBillPreview - ném lỗi khi bàn còn món PENDING hoặc COOKING")
    void getBillPreview_ThrowsWhenUnfinishedItems() {
        OrderItem cookingItem = OrderItem.builder()
                .id(203L)
                .menuItem(beefItem)
                .quantity(1)
                .status(OrderItemStatus.COOKING)
                .build();

        when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, cookingItem));

        assertThatThrownBy(() -> billService.getBillPreview(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still has items pending preparation or serving");
    }

    @Test
    @DisplayName("checkout - thành công khi thanh toán 100% tiền mặt, không mã giảm giá")
    void checkout_CashPayment_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));
        when(sessionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeSession));
        when(billRepository.findByServiceSessionId(100L)).thenReturn(Optional.empty());
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, completedBeef));
        when(restaurantSettingService.isMenuPriceIncludesVat()).thenReturn(true);
        when(billRepository.findMaxSequenceForDate(anyString())).thenReturn(5);

        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            b.setId(1000L);
            return b;
        });

        CheckoutBillRequest request = CheckoutBillRequest.builder()
                .payments(List.of(
                        PaymentItemDto.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(new BigDecimal("510000"))
                                .build()
                ))
                .build();

        BillResponse response = billService.checkout(100L, request);

        assertThat(response).isNotNull();
        assertThat(response.getBillCode()).contains("-0006");
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("510000"));
        assertThat(response.getPayments()).hasSize(1);
        assertThat(activeSession.getStatus()).isEqualTo(ServiceSessionStatus.CLOSED);

        verify(billRepository).save(any(Bill.class));
        verify(sessionRepository).save(activeSession);
    }

    @Test
    @DisplayName("checkout - thành công khi kết hợp 2 phương thức thanh toán và áp mã giảm giá PERCENT")
    void checkout_SplitPayment_WithPromotion_Success() {
        Promotion promo = Promotion.builder()
                .id(10L)
                .code("GIAM10")
                .discountType(DiscountType.PERCENT)
                .discountValue(new BigDecimal("10.00")) // 10%
                .isActive(true)
                .isDeleted(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));
        when(sessionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeSession));
        when(billRepository.findByServiceSessionId(100L)).thenReturn(Optional.empty());
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, completedBeef));
        when(restaurantSettingService.isMenuPriceIncludesVat()).thenReturn(true);
        when(promotionRepository.findByCodeAndIsDeletedFalse("GIAM10")).thenReturn(Optional.of(promo));
        when(billRepository.findMaxSequenceForDate(anyString())).thenReturn(0);

        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            b.setId(1001L);
            return b;
        });

        // 510,000 - 10% (51,000) = 459,000
        // Split: Cash 200,000 + Transfer 259,000 = 459,000
        CheckoutBillRequest request = CheckoutBillRequest.builder()
                .promotionCode("GIAM10")
                .payments(List.of(
                        PaymentItemDto.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(new BigDecimal("200000"))
                                .build(),
                        PaymentItemDto.builder()
                                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                                .amount(new BigDecimal("259000"))
                                .build()
                ))
                .build();

        BillResponse response = billService.checkout(100L, request);

        assertThat(response).isNotNull();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("51000.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("459000.00"));
        assertThat(response.getPayments()).hasSize(2);
        assertThat(activeSession.getStatus()).isEqualTo(ServiceSessionStatus.CLOSED);
    }

    @Test
    @DisplayName("checkout - từ chối khi tổng số tiền thanh toán không khớp với totalAmount")
    void checkout_ThrowsWhenPaymentMismatch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));
        when(sessionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeSession));
        when(billRepository.findByServiceSessionId(100L)).thenReturn(Optional.empty());
        when(orderItemRepository.findAllBySessionIdWithMenuItem(100L))
                .thenReturn(List.of(completedBeer, completedBeef));
        when(restaurantSettingService.isMenuPriceIncludesVat()).thenReturn(true);

        CheckoutBillRequest request = CheckoutBillRequest.builder()
                .payments(List.of(
                        PaymentItemDto.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(new BigDecimal("400000")) // cần 510,000 nhưng trả 400,000
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> billService.checkout(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không khớp với số tiền cần thanh toán");
    }
}

