package com.roms.service.impl;

import com.roms.dto.request.CancelQrOrderRequest;
import com.roms.dto.request.CreateOrderItemRequest;
import com.roms.dto.request.CreateOrderRequest;
import com.roms.dto.request.QrOrderRequestCreateRequest;
import com.roms.dto.response.*;
import com.roms.entity.*;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.QrOrderRequestStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.mapper.CategoryMapper;
import com.roms.mapper.MenuItemImageMapper;
import com.roms.mapper.MenuItemMapper;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.MenuAvailabilityService;
import com.roms.service.OrderService;
import com.roms.service.RestaurantSettingService;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrOrderingServiceImplTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private ServiceSessionRepository sessionRepository;
    @Mock private QrOrderRequestRepository qrOrderRequestRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private MenuItemImageRepository menuItemImageRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantSettingService restaurantSettingService;
    @Mock private MenuAvailabilityService menuAvailabilityService;
    @Mock private OrderService orderService;
    @Mock private MenuItemMapper menuItemMapper;
    @Mock private MenuItemImageMapper menuItemImageMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private StompEventPublisher stompEventPublisher;

    @InjectMocks
    private QrOrderingServiceImpl qrOrderingService;

    private RestaurantSettingResponse enabledSettings;
    private RestaurantTable table;
    private ServiceSession activeSession;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        enabledSettings = RestaurantSettingResponse.builder()
                .qrOrderingEnabled(true)
                .restaurantName("ROMS Test")
                .address("123 Test St")
                .build();

        table = RestaurantTable.builder()
                .id(10L)
                .tableCode("B01")
                .area("Tang 1")
                .capacity(4)
                .isActive(true)
                .qrToken("token-b01")
                .build();

        activeSession = ServiceSession.builder()
                .id(100L)
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedAt(LocalDateTime.now())
                .build();

        menuItem = MenuItem.builder()
                .id(50L)
                .name("Pho Bo")
                .price(new BigDecimal("65000"))
                .status(MenuItemStatus.AVAILABLE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedStaff(Long userId, String username, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .username(username)
                .fullName("Staff " + userId)
                .role(role)
                .active(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void resolveContext_WhenQrDisabled_ThrowsBusinessRuleException() {
        when(restaurantSettingService.getSettings()).thenReturn(
                RestaurantSettingResponse.builder().qrOrderingEnabled(false).build());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> qrOrderingService.resolveContext("token-b01"));
        assertEquals("QR_ORDERING_DISABLED", ex.getCode());
    }

    @Test
    void resolveContext_ConcurrentSessionOpen_FallsBackToExistingSession() {
        when(restaurantSettingService.getSettings()).thenReturn(enabledSettings);
        when(tableRepository.findByQrToken("token-b01")).thenReturn(Optional.of(table));
        when(sessionRepository.findByTableIdAndStatus(10L, ServiceSessionStatus.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(activeSession));
        when(sessionRepository.saveAndFlush(any(ServiceSession.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate active session"));

        QrContextResponse response = qrOrderingService.resolveContext("token-b01");

        assertTrue(response.isQrOrderingEnabled());
        assertEquals(100L, response.getSessionId());
        assertEquals("B01", response.getTableCode());
        assertEquals("ROMS Test", response.getRestaurantName());
    }

    @Test
    void submitQrOrder_WhenNotSellable_ThrowsBusinessRuleException() {
        when(restaurantSettingService.getSettings()).thenReturn(enabledSettings);
        when(tableRepository.findByQrToken("token-b01")).thenReturn(Optional.of(table));
        when(sessionRepository.findByTableIdAndStatus(10L, ServiceSessionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSession));
        when(menuItemRepository.findAllById(List.of(50L))).thenReturn(List.of(menuItem));
        when(menuAvailabilityService.checkSellability(50L, 2)).thenReturn(
                MenuItemSellabilityResponse.builder().menuItemId(50L).sellable(false).reason("OUT_OF_STOCK").build());

        QrOrderRequestCreateRequest request = QrOrderRequestCreateRequest.builder()
                .guestLabel("Khach A")
                .items(List.of(CreateOrderItemRequest.builder().menuItemId(50L).quantity(2).build()))
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> qrOrderingService.submitQrOrder("token-b01", request));
        assertEquals("MENU_ITEM_NOT_SELLABLE", ex.getCode());
        verify(qrOrderRequestRepository, never()).save(any());
    }

    @Test
    void submitQrOrder_Success_SavesPendingAndPublishesStomp() {
        when(restaurantSettingService.getSettings()).thenReturn(enabledSettings);
        when(tableRepository.findByQrToken("token-b01")).thenReturn(Optional.of(table));
        when(sessionRepository.findByTableIdAndStatus(10L, ServiceSessionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSession));
        when(menuItemRepository.findAllById(List.of(50L))).thenReturn(List.of(menuItem));
        when(menuAvailabilityService.checkSellability(50L, 2)).thenReturn(
                MenuItemSellabilityResponse.builder().menuItemId(50L).sellable(true).build());
        when(qrOrderRequestRepository.save(any(QrOrderRequest.class))).thenAnswer(inv -> {
            QrOrderRequest req = inv.getArgument(0);
            req.setId(77L);
            return req;
        });

        QrOrderRequestCreateRequest request = QrOrderRequestCreateRequest.builder()
                .guestLabel("Khach A")
                .items(List.of(CreateOrderItemRequest.builder().menuItemId(50L).quantity(2).note("Khong hanh").build()))
                .build();

        QrOrderRequestResponse response = qrOrderingService.submitQrOrder("token-b01", request);

        assertEquals(77L, response.getId());
        assertEquals(QrOrderRequestStatus.PENDING_CONFIRMATION, response.getStatus());
        assertEquals(1, response.getItems().size());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.staffQrOrders()), any());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.qrSession(100L)), any());
    }

    @Test
    void confirmQrOrder_ConcurrentTwoStaff_OneSucceedsOneGetsConflict() throws Exception {
        User staff1 = User.builder().id(1L).username("staff1").fullName("Staff 1").role(UserRole.SERVICE_STAFF).build();
        User staff2 = User.builder().id(2L).username("staff2").fullName("Staff 2").role(UserRole.SERVICE_STAFF).build();

        QrOrderRequest sharedRequest = QrOrderRequest.builder()
                .id(88L)
                .serviceSession(activeSession)
                .status(QrOrderRequestStatus.PENDING_CONFIRMATION)
                .build();
        sharedRequest.addItem(QrOrderRequestItem.builder().id(1L).menuItem(menuItem).quantity(1).build());

        ReentrantLock dbRowLock = new ReentrantLock();
        when(qrOrderRequestRepository.findByIdForUpdateWithDetails(88L)).thenAnswer(inv -> {
            dbRowLock.lock();
            return Optional.of(sharedRequest);
        });
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(staff1));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(staff2));
        lenient().when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenReturn(OrderResponse.builder().id(500L).build());
        lenient().when(orderRepository.getReferenceById(500L)).thenReturn(Order.builder().id(500L).build());
        lenient().when(qrOrderRequestRepository.save(any(QrOrderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            mockAuthenticatedStaff(1L, "staff1", UserRole.SERVICE_STAFF);
            ready.countDown();
            start.await();
            try {
                qrOrderingService.confirmQrOrder(88L);
                successCount.incrementAndGet();
            } catch (BusinessRuleException ex) {
                if ("QR_ORDER_ALREADY_PROCESSED".equals(ex.getCode())) {
                    conflictCount.incrementAndGet();
                }
            } finally {
                if (dbRowLock.isHeldByCurrentThread()) dbRowLock.unlock();
                SecurityContextHolder.clearContext();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            mockAuthenticatedStaff(2L, "staff2", UserRole.SERVICE_STAFF);
            ready.countDown();
            start.await();
            try {
                qrOrderingService.confirmQrOrder(88L);
                successCount.incrementAndGet();
            } catch (BusinessRuleException ex) {
                if ("QR_ORDER_ALREADY_PROCESSED".equals(ex.getCode())) {
                    conflictCount.incrementAndGet();
                }
            } finally {
                if (dbRowLock.isHeldByCurrentThread()) dbRowLock.unlock();
                SecurityContextHolder.clearContext();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(task1);
        Future<Void> f2 = pool.submit(task2);
        ready.await();
        start.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());
        assertEquals(QrOrderRequestStatus.CONFIRMED, sharedRequest.getStatus());
        verify(orderService, times(1)).createOrder(any());
    }

    @Test
    void cancelQrOrder_Success_MarksCancelledAndPublishesStomp() {
        mockAuthenticatedStaff(1L, "staff1", UserRole.SERVICE_STAFF);
        User staff1 = User.builder().id(1L).username("staff1").fullName("Staff 1").role(UserRole.SERVICE_STAFF).build();
        QrOrderRequest pendingRequest = QrOrderRequest.builder()
                .id(99L)
                .serviceSession(activeSession)
                .status(QrOrderRequestStatus.PENDING_CONFIRMATION)
                .build();
        pendingRequest.addItem(QrOrderRequestItem.builder().id(1L).menuItem(menuItem).quantity(1).build());

        when(qrOrderRequestRepository.findByIdForUpdateWithDetails(99L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff1));
        when(qrOrderRequestRepository.save(any(QrOrderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelQrOrderRequest cancelReq = CancelQrOrderRequest.builder().reason("Khach quet nham ban").build();
        QrOrderRequestResponse response = qrOrderingService.cancelQrOrder(99L, cancelReq);

        assertEquals(QrOrderRequestStatus.CANCELLED, response.getStatus());
        assertEquals("Khach quet nham ban", response.getCancellationReason());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.staffQrOrders()), any());
        verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.qrSession(100L)), any());
    }
}
