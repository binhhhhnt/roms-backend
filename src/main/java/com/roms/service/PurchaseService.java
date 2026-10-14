package com.roms.service;

import com.roms.dto.request.CreatePurchaseRequest;
import com.roms.dto.request.UpdatePurchaseItemRequest;
import com.roms.dto.response.PurchaseDetailResponse;
import com.roms.dto.response.PurchaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface PurchaseService {

    Page<PurchaseResponse> getPurchases(LocalDate fromDate, LocalDate toDate, Pageable pageable);

    PurchaseDetailResponse getPurchaseById(Long id);

    PurchaseDetailResponse createPurchase(CreatePurchaseRequest request, Long userId);

    PurchaseDetailResponse correctPurchaseItem(Long purchaseId, Long itemId, UpdatePurchaseItemRequest request);
}
