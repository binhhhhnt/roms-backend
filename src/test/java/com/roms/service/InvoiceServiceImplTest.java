package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.request.CheckoutPreviewRequest;
import com.roms.dto.request.CheckoutRequest;
import com.roms.dto.response.CheckoutItemResponse;
import com.roms.dto.response.CheckoutPreviewResponse;
import com.roms.dto.response.InvoiceResponse;
import com.roms.entity.Invoice;
import com.roms.entity.MenuItem;
import com.roms.entity.OrderItem;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import com.roms.enums.DiscountType;
import com.roms.enums.OrderItemStatus;
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
import com.roms.security.UserPrincipal;
import com.roms.service.impl.InvoiceServiceImpl;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ServiceSessionRepository sessionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private StompEventPublisher stompEventPublisher;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private RestaurantTable table;
    private ServiceSession activeSession;
    private User staffUser;
    private MenuItem menuItem1;
    private MenuItem menuItem2;
    private OrderItem item1;
    private OrderItem item2;

    @BeforeEach
    void setUp() {
        table = RestaurantTable.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .isActive(true)
                .build();

        staffUser = User.builder()
                .id(10L)
                .username("staff1")
                .fullName("Staff Member")
                .role(UserRole.SERVICE_STAFF)
                .isActive(true)
                .build();

        activeSession = ServiceSession.builder()
                .id(100L)
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedAt(LocalDateTime.now().minusHours(1))
                .openedBy(staffUser)
                .build();

        menuItem1 = MenuItem.builder()
                .id(1L)
                .name("Phở Bò")
                .price(BigDecimal.valueOf(50000))
                .build();

        menuItem2 = MenuItem.builder()
                .id(2L)
                .name("Trà Đá")
                .price(BigDecimal.valueOf(5000))
                .build();

        item1 = OrderItem.builder()
                .id(101L)
                .menuItem(menuItem1)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(50000))
                .status(OrderItemStatus.COMPLETED)
                .build();

        item2 = OrderItem.builder()
                .id(102L)
                .menuItem(menuItem2)
                .quantity(3)
                .unitPrice(BigDecimal.valueOf(5000))
                .status(OrderItemStatus.COMPLETED)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(Long userId, String username, UserRole role, Long tableId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .username(username)
                .password("hashed_pw")
                .fullName("Test User")
                .role(role)
                .tableId(tableId)
                .sessionId(1L)
                .active(true)
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Nested
    @DisplayName("previewCheckout tests")
    class PreviewCheckoutTests {

        @Test
        @DisplayName("Should preview checkout with percentage discount successfully")
        void previewCheckout_percentageDiscount_success() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(false);
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item1, item2));

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10)) // 10%
                    .build();

            CheckoutPreviewResponse response = invoiceService.previewCheckout(request);

            // Subtotal = 2 * 50,000 + 3 * 5,000 = 115,000
            // Discount = 115,000 * 10% = 11,500
            // Final = 115,000 - 11,500 = 103,500
            assertThat(response).isNotNull();
            assertThat(response.getSessionId()).isEqualTo(100L);
            assertThat(response.getSubtotal()).isEqualByComparingTo("115000.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("11500.00");
            assertThat(response.getFinalAmount()).isEqualByComparingTo("103500.00");
            assertThat(response.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("Should preview checkout with fixed amount discount successfully")
        void previewCheckout_fixedAmountDiscount_success() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(false);
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item1));

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(20000))
                    .build();

            CheckoutPreviewResponse response = invoiceService.previewCheckout(request);

            // Subtotal = 100,000, discount = 20,000, final = 80,000
            assertThat(response.getSubtotal()).isEqualByComparingTo("100000.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("20000.00");
            assertThat(response.getFinalAmount()).isEqualByComparingTo("80000.00");
        }

        @Test
        @DisplayName("Should cap fixed amount discount at subtotal when discount exceeds subtotal")
        void previewCheckout_fixedAmountExceedsSubtotal_cappedAtSubtotal() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(false);
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item2));

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(50000)) // exceeds subtotal of 15,000
                    .build();

            CheckoutPreviewResponse response = invoiceService.previewCheckout(request);

            assertThat(response.getSubtotal()).isEqualByComparingTo("15000.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("15000.00");
            assertThat(response.getFinalAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when percentage discount exceeds 100%")
        void previewCheckout_percentageExceeds100_throwsException() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(false);
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item1));

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(110))
                    .build();

            assertThatThrownBy(() -> invoiceService.previewCheckout(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Percentage discount cannot exceed 100%");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when session already has an invoice")
        void previewCheckout_invoiceAlreadyExists_throwsException() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(true);

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .build();

            assertThatThrownBy(() -> invoiceService.previewCheckout(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invoice already created for this session");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when session has unfinished orders")
        void previewCheckout_hasUnfinishedOrders_throwsException() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(true);

            CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                    .sessionId(100L)
                    .build();

            assertThatThrownBy(() -> invoiceService.previewCheckout(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot preview checkout while orders are still pending or cooking");
        }
    }

    @Nested
    @DisplayName("checkoutAndCloseSession tests")
    class CheckoutAndCloseSessionTests {

        @Test
        @DisplayName("Should checkout, persist invoice and close session successfully")
        void checkoutAndCloseSession_success() {
            authenticateUser(10L, "staff1", UserRole.SERVICE_STAFF, null);

            when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(invoiceRepository.existsByServiceSessionId(100L)).thenReturn(false);
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), any())).thenReturn(false);
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item1));
            when(invoiceRepository.findByInvoiceCode(any())).thenReturn(Optional.empty());

            Invoice savedInvoice = Invoice.builder()
                    .id(1L)
                    .invoiceCode("INV-20261103-000100")
                    .serviceSession(activeSession)
                    .subtotal(BigDecimal.valueOf(100000))
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .discountAmount(BigDecimal.valueOf(10000))
                    .finalAmount(BigDecimal.valueOf(90000))
                    .build();
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

            InvoiceResponse mappedResponse = InvoiceResponse.builder()
                    .id(1L)
                    .invoiceCode("INV-20261103-000100")
                    .sessionId(100L)
                    .subtotal(BigDecimal.valueOf(100000))
                    .discountAmount(BigDecimal.valueOf(10000))
                    .finalAmount(BigDecimal.valueOf(90000))
                    .build();
            when(invoiceMapper.toResponse(eq(savedInvoice), any())).thenReturn(mappedResponse);

            CheckoutRequest request = CheckoutRequest.builder()
                    .sessionId(100L)
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .discountReason("Khách quen")
                    .build();

            InvoiceResponse response = invoiceService.checkoutAndCloseSession(request);

            assertThat(response).isNotNull();
            assertThat(response.getFinalAmount()).isEqualByComparingTo("90000.00");

            // Verify session was updated to CLOSED
            assertThat(activeSession.getStatus()).isEqualTo(ServiceSessionStatus.CLOSED);
            assertThat(activeSession.getClosedBy()).isEqualTo(staffUser);
            assertThat(activeSession.getClosedAt()).isNotNull();
            verify(sessionRepository).save(activeSession);

            // Verify STOMP event was published
            verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.tables()), any(SessionStatusChangedEvent.class));
        }

        @Test
        @DisplayName("Should throw UnauthorizedException when unauthenticated")
        void checkoutAndCloseSession_unauthenticated_throwsException() {
            CheckoutRequest request = CheckoutRequest.builder()
                    .sessionId(100L)
                    .build();

            assertThatThrownBy(() -> invoiceService.checkoutAndCloseSession(request))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when TABLE_ACCOUNT attempts checkout")
        void checkoutAndCloseSession_tableAccount_throwsException() {
            authenticateUser(20L, "table_user", UserRole.TABLE_ACCOUNT, 1L);

            CheckoutRequest request = CheckoutRequest.builder()
                    .sessionId(100L)
                    .build();

            assertThatThrownBy(() -> invoiceService.checkoutAndCloseSession(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TABLE_ACCOUNT is not permitted to checkout");
        }
    }

    @Nested
    @DisplayName("getInvoice query tests")
    class QueryInvoiceTests {

        @Test
        @DisplayName("Should get invoice by ID successfully")
        void getInvoiceById_success() {
            Invoice invoice = Invoice.builder()
                    .id(1L)
                    .invoiceCode("INV-20261103-000100")
                    .serviceSession(activeSession)
                    .subtotal(BigDecimal.valueOf(100000))
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(BigDecimal.valueOf(100000))
                    .build();

            when(invoiceRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(invoice));
            when(orderItemRepository.findCompletedItemsBySessionId(100L)).thenReturn(List.of(item1));
            when(invoiceMapper.toResponse(eq(invoice), any())).thenReturn(InvoiceResponse.builder().id(1L).build());

            InvoiceResponse response = invoiceService.getInvoiceById(1L);
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when invoice not found by ID")
        void getInvoiceById_notFound_throwsException() {
            when(invoiceRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invoiceService.getInvoiceById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should get invoices with pagination and date filter")
        void getInvoices_filtered_success() {
            Invoice invoice = Invoice.builder()
                    .id(1L)
                    .invoiceCode("INV-20261103-000100")
                    .serviceSession(activeSession)
                    .finalAmount(BigDecimal.valueOf(100000))
                    .build();

            Page<Invoice> page = new PageImpl<>(List.of(invoice));
            when(invoiceRepository.findWithFilters(any(), any(), any(), any())).thenReturn(page);
            when(invoiceMapper.toResponse(eq(invoice), isNull())).thenReturn(InvoiceResponse.builder().id(1L).build());

            PageResponse<InvoiceResponse> result = invoiceService.getInvoices(1L, LocalDate.now().minusDays(1), LocalDate.now(), PageRequest.of(0, 10));

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when startDate > endDate")
        void getInvoices_invalidDateRange_throwsException() {
            LocalDate start = LocalDate.now();
            LocalDate end = LocalDate.now().minusDays(1);

            assertThatThrownBy(() -> invoiceService.getInvoices(null, start, end, PageRequest.of(0, 10)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Start date cannot be after end date");
        }
    }
}

