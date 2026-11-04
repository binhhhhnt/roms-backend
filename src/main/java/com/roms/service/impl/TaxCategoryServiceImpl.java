package com.roms.service.impl;

import com.roms.dto.request.TaxCategoryRequest;
import com.roms.dto.response.TaxCategoryResponse;
import com.roms.entity.TaxCategory;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TaxCategoryMapper;
import com.roms.repository.TaxCategoryRepository;
import com.roms.service.TaxCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCategoryServiceImpl implements TaxCategoryService {

    private final TaxCategoryRepository taxCategoryRepository;
    private final TaxCategoryMapper taxCategoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TaxCategoryResponse> getAllTaxCategories() {
        return taxCategoryRepository.findAllByOrderByRateAsc()
                .stream()
                .map(taxCategoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCategoryResponse getTaxCategory(Long id) {
        TaxCategory taxCategory = taxCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TAX_CATEGORY_NOT_FOUND", "Tax category not found with id: " + id));
        return taxCategoryMapper.toResponse(taxCategory);
    }

    @Override
    @Transactional
    public TaxCategoryResponse createTaxCategory(TaxCategoryRequest request) {
        BigDecimal normalizedRate = normalizeRate(request.getRate());
        if (taxCategoryRepository.existsByRate(normalizedRate)) {
            throw new BusinessRuleException("TAX_RATE_ALREADY_EXISTS", "Tax rate " + normalizedRate + "% already exists");
        }

        TaxCategory taxCategory = TaxCategory.builder()
                .rate(normalizedRate)
                .build();
        TaxCategory saved = taxCategoryRepository.save(taxCategory);
        log.info("Created tax category with rate: {}%", saved.getRate());
        return taxCategoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaxCategory findOrCreateByRate(BigDecimal rate) {
        BigDecimal normalizedRate = normalizeRate(rate);
        return taxCategoryRepository.findByRate(normalizedRate)
                .orElseGet(() -> {
                    try {
                        TaxCategory newTax = TaxCategory.builder()
                                .rate(normalizedRate)
                                .build();
                        TaxCategory saved = taxCategoryRepository.saveAndFlush(newTax);
                        log.info("Auto-created tax category with rate: {}%", saved.getRate());
                        return saved;
                    } catch (DataIntegrityViolationException ex) {
                        log.warn("Concurrent tax category creation detected for rate: {}%, re-fetching", normalizedRate);
                        return taxCategoryRepository.findByRate(normalizedRate)
                                .orElseThrow(() -> new BusinessRuleException("TAX_CATEGORY_CREATION_FAILED", "Failed to retrieve concurrently created tax category"));
                    }
                });
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            throw new BusinessRuleException("INVALID_TAX_RATE", "Tax rate cannot be null");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("INVALID_TAX_RATE", "Tax rate must not be negative");
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }
}

