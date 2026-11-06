package com.roms.service;

import com.roms.dto.request.CreateIngredientRequest;
import com.roms.dto.request.UpdateIngredientRequest;
import com.roms.dto.response.IngredientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IngredientService {

    Page<IngredientResponse> getIngredients(Boolean isActive, Pageable pageable);

    IngredientResponse getIngredientById(Long id);

    IngredientResponse createIngredient(CreateIngredientRequest request);

    List<IngredientResponse> importIngredients(List<CreateIngredientRequest> requests);

    IngredientResponse updateIngredient(Long id, UpdateIngredientRequest request);

    IngredientResponse deactivateIngredient(Long id);
}
