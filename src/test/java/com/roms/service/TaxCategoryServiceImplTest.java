package com.roms.service;

import com.roms.dto.request.TaxCategoryRequest;
import com.roms.dto.response.TaxCategoryResponse;
import com.roms.entity.TaxCategory;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TaxCategoryMapper;
import com.roms.repository.TaxCategoryRepository;
import com.roms.service.impl.TaxCategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxCategoryServiceImplTest {

    @Mock
    private TaxCategoryRepository taxCategoryRepository;

    private TaxCategoryService taxCategoryService;

    @BeforeEach
    void setUp() {
        taxCategoryService = new TaxCategoryServiceImpl(taxCategoryRepository, new TaxCategoryMapper());
    }

    @Test
    void getAllTaxCategories_returnsOrderedList() {
        TaxCategory t1 = TaxCategory.builder().id(1L).rate(BigDecimal.valueOf(5.00)).build();
        TaxCategory t2 = TaxCategory.builder().id(2L).rate(BigDecimal.valueOf(8.00)).build();
        when(taxCategoryRepository.findAllByOrderByRateAsc()).thenReturn(List.of(t1, t2));

        List<TaxCategoryResponse> list = taxCategoryService.getAllTaxCategories();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getRate()).isEqualByComparingTo("5.00");
        assertThat(list.get(1).getRate()).isEqualByComparingTo("8.00");
    }

    @Test
    void getTaxCategory_whenExists_returnsResponse() {
        TaxCategory t = TaxCategory.builder().id(1L).rate(BigDecimal.valueOf(8.00)).createdAt(LocalDateTime.now()).build();
        when(taxCategoryRepository.findById(1L)).thenReturn(Optional.of(t));

        TaxCategoryResponse response = taxCategoryService.getTaxCategory(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getRate()).isEqualByComparingTo("8.00");
    }

    @Test
    void getTaxCategory_whenNotFound_throwsException() {
        when(taxCategoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxCategoryService.getTaxCategory(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax category not found with id: 99");
    }

    @Test
    void createTaxCategory_whenNewRate_savesAndReturns() {
        BigDecimal rate = BigDecimal.valueOf(10.00);
        TaxCategoryRequest request = TaxCategoryRequest.builder().rate(rate).build();

        when(taxCategoryRepository.existsByRate(rate.setScale(2))).thenReturn(false);
        when(taxCategoryRepository.save(any(TaxCategory.class))).thenAnswer(invocation -> {
            TaxCategory tc = invocation.getArgument(0);
            tc.setId(3L);
            return tc;
        });

        TaxCategoryResponse response = taxCategoryService.createTaxCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getRate()).isEqualByComparingTo("10.00");
        verify(taxCategoryRepository).save(any(TaxCategory.class));
    }

    @Test
    void createTaxCategory_whenDuplicateRate_throwsBusinessRuleException() {
        BigDecimal rate = BigDecimal.valueOf(8.00);
        TaxCategoryRequest request = TaxCategoryRequest.builder().rate(rate).build();

        when(taxCategoryRepository.existsByRate(rate.setScale(2))).thenReturn(true);

        assertThatThrownBy(() -> taxCategoryService.createTaxCategory(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
        verify(taxCategoryRepository, never()).save(any(TaxCategory.class));
    }

    @Test
    void createTaxCategory_whenNegativeRate_throwsException() {
        TaxCategoryRequest request = TaxCategoryRequest.builder().rate(BigDecimal.valueOf(-1.00)).build();

        assertThatThrownBy(() -> taxCategoryService.createTaxCategory(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void findOrCreateByRate_whenAlreadyExists_returnsExisting() {
        BigDecimal rate = BigDecimal.valueOf(8.00);
        TaxCategory existing = TaxCategory.builder().id(1L).rate(rate).build();
        when(taxCategoryRepository.findByRate(rate.setScale(2))).thenReturn(Optional.of(existing));

        TaxCategory result = taxCategoryService.findOrCreateByRate(rate);

        assertThat(result).isSameAs(existing);
        verify(taxCategoryRepository, never()).saveAndFlush(any(TaxCategory.class));
    }

    @Test
    void findOrCreateByRate_whenNotExists_createsAndReturns() {
        BigDecimal rate = BigDecimal.valueOf(7.00);
        when(taxCategoryRepository.findByRate(rate.setScale(2))).thenReturn(Optional.empty());
        when(taxCategoryRepository.saveAndFlush(any(TaxCategory.class))).thenAnswer(invocation -> {
            TaxCategory tc = invocation.getArgument(0);
            tc.setId(10L);
            return tc;
        });

        TaxCategory result = taxCategoryService.findOrCreateByRate(rate);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getRate()).isEqualByComparingTo("7.00");
    }

    @Test
    void findOrCreateByRate_whenConcurrentConflict_recoversGracefully() {
        BigDecimal rate = BigDecimal.valueOf(7.00);
        TaxCategory savedByOtherThread = TaxCategory.builder().id(11L).rate(rate.setScale(2)).build();

        when(taxCategoryRepository.findByRate(rate.setScale(2)))
                .thenReturn(Optional.empty()) // First check before creation
                .thenReturn(Optional.of(savedByOtherThread)); // Recovery check after collision

        when(taxCategoryRepository.saveAndFlush(any(TaxCategory.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        TaxCategory result = taxCategoryService.findOrCreateByRate(rate);

        assertThat(result).isSameAs(savedByOtherThread);
        assertThat(result.getId()).isEqualTo(11L);
    }
}

