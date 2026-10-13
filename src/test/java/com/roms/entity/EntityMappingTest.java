package com.roms.entity;

import com.roms.enums.*;
import com.roms.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EntityMappingTest {

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    @DisplayName("Should query seeded RestaurantTable by tableCode")
    void shouldFindSeededTable() {
        Optional<RestaurantTable> table = tableRepository.findByTableCode("T01");
        assertThat(table).isPresent();
        assertThat(table.get().getCapacity()).isEqualTo(4);
        assertThat(table.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should query seeded User by username and verify role")
    void shouldFindSeededUser() {
        Optional<User> owner = userRepository.findByUsername("owner");
        assertThat(owner).isPresent();
        assertThat(owner.get().getRole()).isEqualTo(UserRole.OWNER);
        assertThat(owner.get().getFullName()).isEqualTo("Nguyễn Văn Chủ");
    }

    @Test
    @DisplayName("Should verify MenuItem mapping without availableQuantity")
    void shouldVerifyMenuItemMapping() {
        var items = menuItemRepository.findByStatus(MenuItemStatus.AVAILABLE);
        assertThat(items).isNotEmpty();
        MenuItem firstItem = items.getFirst();
        assertThat(firstItem.getPrice()).isGreaterThan(BigDecimal.ZERO);
        assertThat(firstItem.getCategory()).isNotNull();
    }

    @Test
    @DisplayName("Enforce unique constraint uq_service_sessions_active_table: max 1 ACTIVE session per table")
    void shouldFailWhenCreatingDuplicateActiveSessionForSameTable() {
        RestaurantTable table = tableRepository.findByTableCode("T01").orElseThrow();
        User staff = userRepository.findByUsername("service01").orElseThrow();

        // 1st ACTIVE session
        ServiceSession session1 = ServiceSession.builder()
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedBy(staff)
                .build();
        sessionRepository.saveAndFlush(session1);

        // 2nd ACTIVE session for the same table -> should violate partial unique index
        ServiceSession session2 = ServiceSession.builder()
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedBy(staff)
                .build();

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Enforce StockMovement single-source constraint: fail when all 3 source FKs are null")
    void shouldFailWhenStockMovementHasZeroSources() {
        Ingredient ingredient = ingredientRepository.findByName("Thịt bò phi lê").orElseThrow();

        StockMovement invalidMovement = StockMovement.builder()
                .ingredient(ingredient)
                .movementType(StockMovementType.PURCHASE)
                .quantityChange(BigDecimal.TEN)
                .balanceAfter(BigDecimal.TEN)
                .purchaseItem(null)
                .orderItem(null)
                .inventoryCheckItem(null)
                .note("Invalid - no source FK")
                .occurredAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> stockMovementRepository.saveAndFlush(invalidMovement))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should persist Order and OrderItem in transaction with snapshot unitPrice")
    void shouldPersistOrderAndOrderItem() {
        RestaurantTable table = tableRepository.findByTableCode("T02").orElseThrow();
        User tableUser = userRepository.findByUsername("table_t02").orElseThrow();
        MenuItem menuItem = menuItemRepository.findByStatus(MenuItemStatus.AVAILABLE).getFirst();

        ServiceSession session = sessionRepository.saveAndFlush(ServiceSession.builder()
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedBy(tableUser)
                .build());

        Order order = orderRepository.saveAndFlush(Order.builder()
                .serviceSession(session)
                .createdBy(tableUser)
                .status(OrderStatus.PENDING)
                .build());

        OrderItem orderItem = orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(order)
                .menuItem(menuItem)
                .quantity(2)
                .unitPrice(menuItem.getPrice())
                .status(OrderItemStatus.PENDING)
                .note("Ít cay")
                .build());

        assertThat(order.getId()).isNotNull();
        assertThat(orderItem.getId()).isNotNull();
        assertThat(orderItem.getUnitPrice()).isEqualTo(menuItem.getPrice());
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.PENDING);
    }
}
