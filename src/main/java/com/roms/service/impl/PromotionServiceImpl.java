package com.roms.service.impl;

import com.roms.dto.request.PromotionWriteRequest;
import com.roms.dto.response.PromotionDto;
import com.roms.dto.response.PromotionResponse;
import com.roms.entity.Promotion;
import com.roms.entity.User;
import com.roms.enums.DiscountType;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.PromotionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<PromotionResponse> getPromotions(String search, Boolean isActive, Pageable pageable) {
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Page<Promotion> promoPage = promotionRepository.findAllFiltered(normalizedSearch, isActive, pageable);

        if (promoPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> promoIds = promoPage.getContent().stream()
                .map(Promotion::getId)
                .toList();

        // Batch fetch usage count to prevent N+1 query problem completely
        Map<Long, Long> usedCountsMap = fetchUsedCounts(promoIds);

        List<PromotionResponse> responses = promoPage.getContent().stream()
                .map(p -> mapToResponse(p, usedCountsMap.getOrDefault(p.getId(), 0L)))
                .toList();

        return new PageImpl<>(responses, pageable, promoPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDto> getValidPromotions() {
        LocalDate today = LocalDate.now();
        List<Promotion> promos = promotionRepository.findValidPromotions(today);
        if (promos.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> promoIds = promos.stream().map(Promotion::getId).toList();
        Map<Long, Long> usedCountsMap = fetchUsedCounts(promoIds);

        return promos.stream()
                .filter(p -> {
                    if (p.getMaxUses() == null) {
                        return true;
                    }
                    long used = usedCountsMap.getOrDefault(p.getId(), 0L);
                    return used < p.getMaxUses();
                })
                .map(p -> PromotionDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .code(p.getCode())
                        .discountType(p.getDiscountType())
                        .discountValue(p.getDiscountValue())
                        .maxUses(p.getMaxUses())
                        .usedCount(usedCountsMap.getOrDefault(p.getId(), 0L))
                        .startDate(p.getStartDate())
                        .endDate(p.getEndDate())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(Long id) {
        Promotion promo = promotionRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with id: " + id));

        long usedCount = promotionRepository.countUsedBills(id);
        return mapToResponse(promo, usedCount);
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionWriteRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        LocalDate effectiveStartDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        request.setStartDate(effectiveStartDate);
        validatePromotionRequest(request, normalizedCode, null);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User currentUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        Promotion promo = Promotion.builder()
                .name(request.getName().trim())
                .code(normalizedCode)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxUses(request.getMaxUses())
                .startDate(effectiveStartDate)
                .endDate(request.getEndDate())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isDeleted(false)
                .createdBy(currentUser)
                .build();

        try {
            Promotion saved = promotionRepository.saveAndFlush(promo);
            log.info("Promotion created: id={}, code={}", saved.getId(), saved.getCode());
            return mapToResponse(saved, 0L);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Data integrity violation while creating promotion code: {}", normalizedCode, ex);
            if (ex.getMessage() != null && ex.getMessage().contains("chk_promotions_period")) {
                throw new BusinessRuleException("Thời gian hiệu lực của khuyến mãi không hợp lệ", "INVALID_DATE_RANGE");
            }
            throw new BusinessRuleException("Mã khuyến mãi '" + normalizedCode + "' đã tồn tại", "PROMOTION_CODE_ALREADY_EXISTS");
        }
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionWriteRequest request) {
        Promotion promo = promotionRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with id: " + id));

        String normalizedCode = request.getCode().trim().toUpperCase();
        LocalDate fallbackStart = promo.getStartDate() != null
                ? promo.getStartDate()
                : (promo.getCreatedAt() != null ? promo.getCreatedAt().toLocalDate() : LocalDate.now());
        LocalDate effectiveStartDate = request.getStartDate() != null ? request.getStartDate() : fallbackStart;
        request.setStartDate(effectiveStartDate);
        validatePromotionRequest(request, normalizedCode, id);

        promo.setName(request.getName().trim());
        promo.setCode(normalizedCode);
        promo.setDiscountType(request.getDiscountType());
        promo.setDiscountValue(request.getDiscountValue());
        promo.setMaxUses(request.getMaxUses());
        promo.setStartDate(effectiveStartDate);
        promo.setEndDate(request.getEndDate());
        if (request.getIsActive() != null) {
            promo.setIsActive(request.getIsActive());
        }

        try {
            Promotion saved = promotionRepository.saveAndFlush(promo);
            long usedCount = promotionRepository.countUsedBills(saved.getId());
            log.info("Promotion updated: id={}, code={}", saved.getId(), saved.getCode());
            return mapToResponse(saved, usedCount);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Data integrity violation while updating promotion code: {}", normalizedCode, ex);
            if (ex.getMessage() != null && ex.getMessage().contains("chk_promotions_period")) {
                throw new BusinessRuleException("Thời gian hiệu lực của khuyến mãi không hợp lệ", "INVALID_DATE_RANGE");
            }
            throw new BusinessRuleException("Mã khuyến mãi '" + normalizedCode + "' đã tồn tại", "PROMOTION_CODE_ALREADY_EXISTS");
        }
    }

    @Override
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promo = promotionRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with id: " + id));

        promo.setIsDeleted(true);
        promotionRepository.save(promo);
        log.info("Promotion soft-deleted: id={}, code={}", promo.getId(), promo.getCode());
    }

    @Override
    @Transactional
    public PromotionResponse toggleActive(Long id) {
        Promotion promo = promotionRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with id: " + id));

        promo.setIsActive(!Boolean.TRUE.equals(promo.getIsActive()));
        Promotion saved = promotionRepository.save(promo);
        long usedCount = promotionRepository.countUsedBills(saved.getId());
        log.info("Promotion status toggled: id={}, isActive={}", saved.getId(), saved.getIsActive());
        return mapToResponse(saved, usedCount);
    }

    private void validatePromotionRequest(PromotionWriteRequest request, String normalizedCode, Long excludeId) {
        if (request.getDiscountType() == DiscountType.PERCENT) {
            if (request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessRuleException("Phần trăm giảm giá không được vượt quá 100%", "INVALID_DISCOUNT_PERCENT");
            }
        }

        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new BusinessRuleException("Ngày kết thúc không được trước ngày bắt đầu", "INVALID_DATE_RANGE");
            }
        }

        boolean exists = (excludeId == null)
                ? promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(normalizedCode)
                : promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(normalizedCode, excludeId);

        if (exists) {
            throw new BusinessRuleException("Mã khuyến mãi '" + normalizedCode + "' đã tồn tại", "PROMOTION_CODE_ALREADY_EXISTS");
        }
    }

    private Map<Long, Long> fetchUsedCounts(Collection<Long> promoIds) {
        if (promoIds == null || promoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rawCounts = promotionRepository.countUsedBillsByPromotionIds(promoIds);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long promoId = (Long) row[0];
            Long count = ((Number) row[1]).longValue();
            map.put(promoId, count);
        }
        return map;
    }

    private PromotionResponse mapToResponse(Promotion p, long usedCount) {
        LocalDate effectiveStartDate = p.getStartDate() != null
                ? p.getStartDate()
                : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : LocalDate.now());

        return PromotionResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .code(p.getCode())
                .discountType(p.getDiscountType())
                .discountValue(p.getDiscountValue())
                .maxUses(p.getMaxUses())
                .usedCount(usedCount)
                .startDate(effectiveStartDate)
                .endDate(p.getEndDate())
                .isActive(p.getIsActive())
                .statusBadge(computeStatusBadge(p, usedCount))
                .createdById(p.getCreatedBy() != null ? p.getCreatedBy().getId() : null)
                .createdByName(p.getCreatedBy() != null ? (p.getCreatedBy().getFullName() != null ? p.getCreatedBy().getFullName() : p.getCreatedBy().getUsername()) : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String computeStatusBadge(Promotion promo, long usedCount) {
        if (!Boolean.TRUE.equals(promo.getIsActive())) {
            return "INACTIVE";
        }
        LocalDate today = LocalDate.now();
        if (promo.getStartDate() != null && today.isBefore(promo.getStartDate())) {
            return "NOT_STARTED";
        }
        if (promo.getEndDate() != null && today.isAfter(promo.getEndDate())) {
            return "EXPIRED";
        }
        if (promo.getMaxUses() != null && usedCount >= promo.getMaxUses()) {
            return "OUT_OF_USES";
        }
        return "ACTIVE";
    }
}

