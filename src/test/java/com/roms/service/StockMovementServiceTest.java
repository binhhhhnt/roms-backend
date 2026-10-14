package com.roms.service;

import com.roms.dto.response.StockMovementResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.InventoryCheckItem;
import com.roms.entity.OrderItem;
import com.roms.entity.PurchaseItem;
import com.roms.entity.StockMovement;
import com.roms.enums.StockMovementType;
import com.roms.mapper.StockMovementMapper;
import com.roms.repository.StockMovementRepository;
import com.roms.service.impl.StockMovementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Spy
    private StockMovementMapper stockMovementMapper = new StockMovementMapper();

    @InjectMocks
    private StockMovementServiceImpl stockMovementService;

    private Ingredient rice;

    @BeforeEach
    void setUp() {
        rice = Ingredient.builder()
                .id(1L)
                .name("Gạo ST25")
                .unit("kg")
                .currentStock(new BigDecimal("50.000"))
                .build();
    }

    @Test
    @DisplayName("Record inventory adjustment appends movement with correct type and balanceAfter")
    void recordInventoryAdjustment_Success() {
        InventoryCheckItem checkItem = InventoryCheckItem.builder().id(11L).build();
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovement result = stockMovementService.recordInventoryAdjustment(
                rice, new BigDecimal("-5.000"), checkItem, "Hao hụt kho");

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement saved = captor.getValue();
        assertThat(saved.getMovementType()).isEqualTo(StockMovementType.INVENTORY_ADJUSTMENT);
        assertThat(saved.getQuantityChange()).isEqualByComparingTo(new BigDecimal("-5.000"));
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("50.000"));
        assertThat(saved.getInventoryCheckItem()).isEqualTo(checkItem);
        assertThat(saved.getNote()).isEqualTo("Hao hụt kho");
    }

    @Test
    @DisplayName("Record purchase appends movement with PURCHASE type")
    void recordPurchase_Success() {
        PurchaseItem purchaseItem = PurchaseItem.builder().id(22L).build();
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovement result = stockMovementService.recordPurchase(
                rice, new BigDecimal("30.000"), purchaseItem, "Nhập kho NCC");

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement saved = captor.getValue();
        assertThat(saved.getMovementType()).isEqualTo(StockMovementType.PURCHASE);
        assertThat(saved.getQuantityChange()).isEqualByComparingTo(new BigDecimal("30.000"));
        assertThat(saved.getPurchaseItem()).isEqualTo(purchaseItem);
    }

    @Test
    @DisplayName("Record order consumption appends movement with ORDER_CONSUMPTION type")
    void recordOrderConsumption_Success() {
        OrderItem orderItem = OrderItem.builder().id(33L).build();
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovement result = stockMovementService.recordOrderConsumption(
                rice, new BigDecimal("-2.500"), orderItem, "Khách gọi cơm tấm");

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement saved = captor.getValue();
        assertThat(saved.getMovementType()).isEqualTo(StockMovementType.ORDER_CONSUMPTION);
        assertThat(saved.getQuantityChange()).isEqualByComparingTo(new BigDecimal("-2.500"));
        assertThat(saved.getOrderItem()).isEqualTo(orderItem);
    }

    @Test
    @DisplayName("Query movements with ingredientId and movementType filters")
    void getMovements_WithBothFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        StockMovement movement = StockMovement.builder()
                .id(1L)
                .ingredient(rice)
                .movementType(StockMovementType.PURCHASE)
                .quantityChange(new BigDecimal("10.000"))
                .balanceAfter(new BigDecimal("50.000"))
                .build();

        when(stockMovementRepository.findByIngredientIdAndMovementType(1L, StockMovementType.PURCHASE, pageable))
                .thenReturn(new PageImpl<>(List.of(movement)));

        Page<StockMovementResponse> page = stockMovementService.getMovements(1L, StockMovementType.PURCHASE, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getMovementType()).isEqualTo(StockMovementType.PURCHASE);
    }
}
