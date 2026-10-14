package com.roms.service;

import com.roms.dto.request.CreatePurchaseRequest;
import com.roms.dto.request.PurchaseItemRequest;
import com.roms.dto.request.UpdatePurchaseItemRequest;
import com.roms.dto.response.PurchaseDetailResponse;
import com.roms.dto.response.PurchaseResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.Purchase;
import com.roms.entity.PurchaseItem;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.PurchaseMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.PurchaseItemRepository;
import com.roms.repository.PurchaseRepository;
import com.roms.repository.UserRepository;
import com.roms.service.impl.PurchaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PurchaseItemRepository purchaseItemRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementService stockMovementService;

    @Spy
    private PurchaseMapper purchaseMapper = new PurchaseMapper();

    @InjectMocks
    private PurchaseServiceImpl purchaseService;

    private User staff;
    private Ingredient beef;

    @BeforeEach
    void setUp() {
        staff = User.builder()
                .id(1L)
                .username("inventory_staff")
                .fullName("Staff A")
                .role(UserRole.INVENTORY_STAFF)
                .build();

        beef = Ingredient.builder()
                .id(10L)
                .name("Thịt bò")
                .unit("kg")
                .currentStock(new BigDecimal("20.000"))
                .build();
    }

    @Test
    @DisplayName("Create purchase atomically updates stock and creates items and movements")
    void createPurchase_Success() {
        CreatePurchaseRequest request = CreatePurchaseRequest.builder()
                .purchaseDate(LocalDate.of(2026, 10, 14))
                .note("Nhập thịt bò tươi")
                .items(List.of(
                        PurchaseItemRequest.builder()
                                .ingredientId(10L)
                                .quantity(new BigDecimal("15.000"))
                                .unitPrice(new BigDecimal("250000.00"))
                                .build()
                ))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> {
            Purchase p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(ingredientRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(beef));
        when(purchaseItemRepository.save(any(PurchaseItem.class))).thenAnswer(inv -> {
            PurchaseItem item = inv.getArgument(0);
            item.setId(200L);
            return item;
        });

        PurchaseDetailResponse response = purchaseService.createPurchase(request, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("3750000.00"));
        assertThat(response.getItems()).hasSize(1);

        var item = response.getItems().get(0);
        assertThat(item.getQuantity()).isEqualByComparingTo(new BigDecimal("15.000"));
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("3750000.00"));

        // Stock increased from 20 to 35
        assertThat(beef.getCurrentStock()).isEqualByComparingTo(new BigDecimal("35.000"));
        verify(ingredientRepository).save(beef);

        // StockMovement recorded
        verify(stockMovementService).recordPurchase(
                eq(beef),
                eq(new BigDecimal("15.000")),
                any(PurchaseItem.class),
                eq("Nhập hàng theo phiếu #100")
        );
    }

    @Test
    @DisplayName("Correct purchase item increasing quantity adjusts stock and totals correctly")
    void correctPurchaseItem_IncreaseQuantity_Success() {
        Purchase purchase = Purchase.builder()
                .id(100L)
                .purchaseDate(LocalDate.now())
                .totalAmount(new BigDecimal("5000000.00"))
                .build();

        PurchaseItem item = PurchaseItem.builder()
                .id(200L)
                .purchase(purchase)
                .ingredient(beef)
                .quantity(new BigDecimal("20.000"))
                .unitPrice(new BigDecimal("250000.00"))
                .subtotal(new BigDecimal("5000000.00"))
                .build();

        UpdatePurchaseItemRequest updateReq = UpdatePurchaseItemRequest.builder()
                .quantity(new BigDecimal("25.000"))
                .unitPrice(new BigDecimal("250000.00"))
                .reason("Giao thêm 5kg")
                .build();

        when(purchaseRepository.findById(100L)).thenReturn(Optional.of(purchase));
        when(purchaseItemRepository.findByIdAndPurchaseId(200L, 100L)).thenReturn(Optional.of(item));
        when(ingredientRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(beef));
        when(purchaseItemRepository.findByPurchaseIdOrderByIdAsc(100L)).thenReturn(List.of(item));

        PurchaseDetailResponse response = purchaseService.correctPurchaseItem(100L, 200L, updateReq);

        // Delta = +5, currentStock: 20 -> 25
        assertThat(beef.getCurrentStock()).isEqualByComparingTo(new BigDecimal("25.000"));
        verify(ingredientRepository).save(beef);

        // New total amount: 5,000,000 + 1,250,000 = 6,250,000
        assertThat(purchase.getTotalAmount()).isEqualByComparingTo(new BigDecimal("6250000.00"));

        // Compensating movement recorded
        verify(stockMovementService).recordPurchase(
                eq(beef),
                eq(new BigDecimal("5.000")),
                eq(item),
                contains("Giao thêm 5kg")
        );
    }

    @Test
    @DisplayName("Correct purchase item decreasing quantity fails when remaining stock is insufficient")
    void correctPurchaseItem_InsufficientStock_ThrowsException() {
        beef.setCurrentStock(new BigDecimal("2.000")); // Only 2kg remaining in stock

        Purchase purchase = Purchase.builder()
                .id(100L)
                .totalAmount(new BigDecimal("5000000.00"))
                .build();

        PurchaseItem item = PurchaseItem.builder()
                .id(200L)
                .purchase(purchase)
                .ingredient(beef)
                .quantity(new BigDecimal("20.000"))
                .unitPrice(new BigDecimal("250000.00"))
                .subtotal(new BigDecimal("5000000.00"))
                .build();

        // Want to decrease by 5kg (from 20kg to 15kg), but current stock is only 2kg!
        UpdatePurchaseItemRequest updateReq = UpdatePurchaseItemRequest.builder()
                .quantity(new BigDecimal("15.000"))
                .unitPrice(new BigDecimal("250000.00"))
                .reason("Nhập nhầm thừa")
                .build();

        when(purchaseRepository.findById(100L)).thenReturn(Optional.of(purchase));
        when(purchaseItemRepository.findByIdAndPurchaseId(200L, 100L)).thenReturn(Optional.of(item));
        when(ingredientRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(beef));

        assertThatThrownBy(() -> purchaseService.correctPurchaseItem(100L, 200L, updateReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot decrease purchase quantity");

        // Stock not updated, no movement recorded
        verify(stockMovementService, never()).recordPurchase(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Get purchases filtered by date range")
    void getPurchases_DateRangeFilter() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 15);
        Pageable pageable = PageRequest.of(0, 10);

        Purchase purchase = Purchase.builder()
                .id(1L)
                .purchaseDate(LocalDate.of(2026, 10, 10))
                .totalAmount(new BigDecimal("1000000.00"))
                .build();

        when(purchaseRepository.findByPurchaseDateBetween(from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(purchase)));

        Page<PurchaseResponse> page = purchaseService.getPurchases(from, to, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(1L);
    }
}
