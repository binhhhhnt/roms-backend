package com.roms.service;

import com.roms.dto.request.CreateInventoryCheckRequest;
import com.roms.dto.response.InventoryCheckResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryCheckService {

    Page<InventoryCheckResponse> getInventoryChecks(Pageable pageable);

    InventoryCheckResponse getInventoryCheckById(Long id);

    InventoryCheckResponse createInventoryCheck(CreateInventoryCheckRequest request, Long userId);
}
