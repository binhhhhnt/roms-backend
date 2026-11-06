package com.roms.service;

import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.request.PaymentItemDto;
import com.roms.dto.response.BillResponse;
import com.roms.entity.*;
import com.roms.enums.OrderItemStatus;
import com.roms.enums.OrderStatus;
import com.roms.enums.PaymentMethod;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BillConcurrencyIntegrationTest {

    @Autowired
    private BillService billService;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private UserRepository userRepository;

    private User ownerUser;
    private RestaurantTable testTable;
    private ServiceSession testSession;
    private Order testOrder;
    private OrderItem testOrderItem;
    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        ownerUser = userRepository.findByUsername("owner")
                .orElseThrow(() -> new IllegalStateException("Seed user owner not found"));

        UserPrincipal principal = UserPrincipal.create(ownerUser, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        testTable = tableRepository.findAll().stream()
                .filter(RestaurantTable::getIsActive)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active table found"));

        // Close any lingering active session on testTable
        sessionRepository.findByTableIdAndStatus(testTable.getId(), ServiceSessionStatus.ACTIVE)
                .ifPresent(s -> {
                    s.setStatus(ServiceSessionStatus.CLOSED);
                    sessionRepository.save(s);
                });

        testSession = sessionRepository.save(ServiceSession.builder()
                .table(testTable)
                .openedBy(ownerUser)
                .status(ServiceSessionStatus.ACTIVE)
                .build());

        testMenuItem = menuItemRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No menu item found"));

        testOrder = orderRepository.save(Order.builder()
                .serviceSession(testSession)
                .status(OrderStatus.COMPLETED)
                .createdBy(ownerUser)
                .build());

        testOrderItem = orderItemRepository.save(OrderItem.builder()
                .order(testOrder)
                .menuItem(testMenuItem)
                .quantity(2)
                .returnedQuantity(0)
                .unitPrice(new BigDecimal("50000.00"))
                .taxRate(new BigDecimal("8.00"))
                .isVatInclusive(true)
                .status(OrderItemStatus.COMPLETED)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (testSession != null && testSession.getId() != null) {
            billRepository.findByServiceSessionId(testSession.getId())
                    .ifPresent(billRepository::delete);
            orderItemRepository.deleteAll(orderItemRepository.findByOrderId(testOrder.getId()));
            orderRepository.delete(testOrder);
            sessionRepository.delete(testSession);
        }
    }

    @Test
    @DisplayName("Thanh toán đồng thời 2 luồng: Bảo đảm chống Race Condition (chỉ 1 Bill được tạo, không Deadlock)")
    void concurrentCheckout_EnsuresOnlyOneBillCreated_WithoutDeadlock() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        // 2 items * 50,000 = 100,000
        BigDecimal expectedTotal = new BigDecimal("100000.00");

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                // Set security context for thread
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                UserPrincipal principal = UserPrincipal.create(ownerUser, null);
                context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                SecurityContextHolder.setContext(context);

                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for simultaneous start trigger

                    CheckoutBillRequest request = CheckoutBillRequest.builder()
                            .payments(List.of(
                                    PaymentItemDto.builder()
                                            .paymentMethod(PaymentMethod.CASH)
                                            .amount(expectedTotal)
                                            .build()
                            ))
                            .build();

                    BillResponse response = billService.checkout(testSession.getId(), request);
                    if (response != null && response.getId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (BusinessRuleException e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Trigger all threads simultaneously

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Verification: Exactly 1 thread must succeed, and 1 must fail due to double-checkout prevention
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        // Verify DB state: Exactly 1 bill exists for this session
        var billOpt = billRepository.findByServiceSessionId(testSession.getId());
        assertThat(billOpt).isPresent();
        assertThat(billOpt.get().getTotalAmount()).isEqualByComparingTo(expectedTotal);

        // Verify session status is closed
        ServiceSession updatedSession = sessionRepository.findById(testSession.getId()).orElseThrow();
        assertThat(updatedSession.getStatus()).isEqualTo(ServiceSessionStatus.CLOSED);
    }
}

