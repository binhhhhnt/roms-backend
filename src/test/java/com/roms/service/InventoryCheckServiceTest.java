package com.roms.service;

import com.roms.dto.request.CreateInventoryCheckRequest;
import com.roms.dto.request.InventoryCheckItemRequest;
import com.roms.dto.response.InventoryCheckResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.InventoryCheck;
import com.roms.entity.InventoryCheckItem;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.InventoryCheckMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.InventoryCheckItemRepository;
import com.roms.repository.InventoryCheckRepository;
import com.roms.repository.UserRepository;
import com.roms.service.impl.InventoryCheckServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCheckServiceTest {

    @Mock
    private InventoryCheckRepository inventoryCheckRepository;

    @Mock
    private InventoryCheckItemRepository inventoryCheckItemRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementService stockMovementService;

    @Spy
    private InventoryCheckMapper inventoryCheckMapper = new InventoryCheckMapper();

    @InjectMocks
    private InventoryCheckServiceImpl inventoryCheckService;

    private User staff;
    private Ingredient flour;

    @BeforeEach
    void setUp() {
        staff = User.builder()
                .id(10L)
                .username("inventory_staff")
                .fullName("Staff Nguyen")
                .role(UserRole.INVENTORY_STAFF)
                .build();

        flour = Ingredient.builder()
                .id(5L)
                .name("Bột mì")
                .unit("kg")
                .currentStock(new BigDecimal("20.000"))
                .minimumStock(new BigDecimal("5.000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Create inventory check with discrepancy updates stock and records stock movement")
    void createInventoryCheck_Discrepancy_UpdatesStockAndAppendsMovement() {
        CreateInventoryCheckRequest request = CreateInventoryCheckRequest.builder()
                .note("Kiểm kho định kỳ")
                .items(List.of(
                        InventoryCheckItemRequest.builder()
                                .ingredientId(5L)
                                .actualQuantity(new BigDecimal("18.000"))
                                .reason("Hao hụt tự nhiên")
                                .build()
                ))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(inventoryCheckRepository.save(any(InventoryCheck.class))).thenAnswer(inv -> {
            InventoryCheck c = inv.getArgument(0);
            c.setId(100L);
            c.setCheckedAt(LocalDateTime.now());
            return c;
        });
        when(ingredientRepository.findByIdWithPessimisticLock(5L)).thenReturn(Optional.of(flour));
        when(inventoryCheckItemRepository.save(any(InventoryCheckItem.class))).thenAnswer(inv -> {
            InventoryCheckItem item = inv.getArgument(0);
            item.setId(200L);
            return item;
        });

        InventoryCheckResponse response = inventoryCheckService.createInventoryCheck(request, 10L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCheckedByName()).isEqualTo("Staff Nguyen");
        assertThat(response.getItems()).hasSize(1);

        var itemResp = response.getItems().get(0);
        assertThat(itemResp.getSystemQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(itemResp.getActualQuantity()).isEqualByComparingTo(new BigDecimal("18.000"));
        assertThat(itemResp.getDifference()).isEqualByComparingTo(new BigDecimal("-2.000"));
        assertThat(itemResp.getReason()).isEqualTo("Hao hụt tự nhiên");

        // Verify ingredient stock updated to actual quantity
        assertThat(flour.getCurrentStock()).isEqualByComparingTo(new BigDecimal("18.000"));
        verify(ingredientRepository).save(flour);

        // Verify stock movement adjustment was recorded
        verify(stockMovementService).recordInventoryAdjustment(
                eq(flour),
                eq(new BigDecimal("-2.000")),
                any(InventoryCheckItem.class),
                eq("Hao hụt tự nhiên")
        );
    }

    @Test
    @DisplayName("Create confirmation check without discrepancy does not create stock movement")
    void createInventoryCheck_NoDiscrepancy_NoMovement() {
        CreateInventoryCheckRequest request = CreateInventoryCheckRequest.builder()
                .note("Xác nhận tồn kho đúng")
                .items(List.of(
                        InventoryCheckItemRequest.builder()
                                .ingredientId(5L)
                                .actualQuantity(new BigDecimal("20.000"))
                                .build()
                ))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(inventoryCheckRepository.save(any())).thenAnswer(inv -> {
            InventoryCheck c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });
        when(ingredientRepository.findByIdWithPessimisticLock(5L)).thenReturn(Optional.of(flour));
        when(inventoryCheckItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryCheckResponse response = inventoryCheckService.createInventoryCheck(request, 10L);

        assertThat(response.getItems().get(0).getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(stockMovementService, never()).recordInventoryAdjustment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Get inventory check by ID throws ResourceNotFoundException if not found")
    void getInventoryCheckById_NotFound() {
        when(inventoryCheckRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryCheckService.getInventoryCheckById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
