package com.roms.service;

import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.response.BillPreviewResponse;
import com.roms.dto.response.BillResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillService {
    BillPreviewResponse getBillPreview(Long sessionId);
    BillResponse checkout(Long sessionId, CheckoutBillRequest request);
    BillResponse getBillById(Long id);
    BillResponse getBillBySessionId(Long sessionId);
    Page<BillResponse> getBills(Pageable pageable);
    Page<BillResponse> getBills(String search, Pageable pageable);
}
