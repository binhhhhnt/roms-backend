package com.roms.service;

import com.roms.dto.request.CreateIngredientRequest;
import com.roms.dto.request.UpdateIngredientRequest;
import com.roms.dto.response.IngredientResponse;
import com.roms.entity.Ingredient;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.StockStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.IngredientMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.impl.IngredientServiceImpl;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Spy
    private IngredientMapper ingredientMapper = new IngredientMapper();

    @InjectMocks
    private IngredientServiceImpl ingredientService;

    private Ingredient beef;

    @BeforeEach
    void setUp() {
        beef = Ingredient.builder()
                .id(1L)
                .name("Thịt bò")
                .unit("kg")
                .currentStock(new BigDecimal("10.000"))
                .minimumStock(new BigDecimal("5.000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Create ingredient successfully with trimmed name and default zero stock")
    void createIngredient_Success() {
        CreateIngredientRequest request = CreateIngredientRequest.builder()
                .name("  Thịt gà  ")
                .unit(" kg ")
                .minimumStock(new BigDecimal("2.000"))
                .build();

        when(ingredientRepository.existsByNameIgnoreCase("Thịt gà")).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> {
            Ingredient saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        IngredientResponse response = ingredientService.createIngredient(request);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getName()).isEqualTo("Thịt gà");
        assertThat(response.getUnit()).isEqualTo("kg");
        assertThat(response.getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
        verify(ingredientRepository).save(any(Ingredient.class));
    }

    @Test
    @DisplayName("Create ingredient with duplicate name throws BusinessRuleException")
    void createIngredient_DuplicateName_ThrowsException() {
        CreateIngredientRequest request = CreateIngredientRequest.builder()
                .name("Thịt bò")
                .unit("kg")
                .build();

        when(ingredientRepository.existsByNameIgnoreCase("Thịt bò")).thenReturn(true);

        assertThatThrownBy(() -> ingredientService.createIngredient(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update ingredient metadata preserves current stock balance")
    void updateIngredient_Success() {
        UpdateIngredientRequest request = UpdateIngredientRequest.builder()
                .name("Thịt bò tươi")
                .unit("kg")
                .minimumStock(new BigDecimal("8.000"))
                .build();

        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(beef));
        when(ingredientRepository.existsByNameIgnoreCaseAndIdNot("Thịt bò tươi", 1L)).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        IngredientResponse response = ingredientService.updateIngredient(1L, request);

        assertThat(response.getName()).isEqualTo("Thịt bò tươi");
        assertThat(response.getMinimumStock()).isEqualByComparingTo(new BigDecimal("8.000"));
        assertThat(response.getCurrentStock()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    @DisplayName("Deactivate ingredient fails when used in active menu items")
    void deactivateIngredient_FailsWhenInActiveMenuItems() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(beef));
        when(recipeItemRepository.existsByIngredientIdAndMenuItemStatusNot(1L, MenuItemStatus.DISCONTINUED))
                .thenReturn(true);

        assertThatThrownBy(() -> ingredientService.deactivateIngredient(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot deactivate ingredient");

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deactivate ingredient succeeds when not used in active menu items")
    void deactivateIngredient_Success() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(beef));
        when(recipeItemRepository.existsByIngredientIdAndMenuItemStatusNot(1L, MenuItemStatus.DISCONTINUED))
                .thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        IngredientResponse response = ingredientService.deactivateIngredient(1L);

        assertThat(response.getIsActive()).isFalse();
        verify(ingredientRepository).save(beef);
    }

    @Test
    @DisplayName("Derived stock status computes OUT_OF_STOCK, LOW, and NORMAL correctly")
    void stockStatus_DerivedComputation() {
        assertThat(ingredientMapper.computeStockStatus(BigDecimal.ZERO, new BigDecimal("5.000")))
                .isEqualTo(StockStatus.OUT_OF_STOCK);

        assertThat(ingredientMapper.computeStockStatus(new BigDecimal("3.000"), new BigDecimal("5.000")))
                .isEqualTo(StockStatus.LOW);

        assertThat(ingredientMapper.computeStockStatus(new BigDecimal("5.000"), new BigDecimal("5.000")))
                .isEqualTo(StockStatus.LOW);

        assertThat(ingredientMapper.computeStockStatus(new BigDecimal("10.000"), new BigDecimal("5.000")))
                .isEqualTo(StockStatus.NORMAL);

        assertThat(ingredientMapper.computeStockStatus(new BigDecimal("1.000"), null))
                .isEqualTo(StockStatus.NORMAL);
    }

    @Test
    @DisplayName("Get ingredient by ID throws ResourceNotFoundException when not found")
    void getIngredientById_NotFound() {
        when(ingredientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingredientService.getIngredientById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get ingredients with pagination and isActive filter")
    void getIngredients_Filtered() {
        Pageable pageable = PageRequest.of(0, 10);
        when(ingredientRepository.findByIsActive(eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(beef)));

        Page<IngredientResponse> result = ingredientService.getIngredients(true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Thịt bò");
    }
}
