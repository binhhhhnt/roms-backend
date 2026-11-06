package com.roms.service;

import com.roms.dto.request.PromotionWriteRequest;
import com.roms.dto.response.PromotionDto;
import com.roms.dto.response.PromotionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PromotionService {

    Page<PromotionResponse> getPromotions(String search, Boolean isActive, Pageable pageable);

    List<PromotionDto> getValidPromotions();

    PromotionResponse getPromotionById(Long id);

    PromotionResponse createPromotion(PromotionWriteRequest request);

    PromotionResponse updatePromotion(Long id, PromotionWriteRequest request);

    void deletePromotion(Long id);

    PromotionResponse toggleActive(Long id);
}

