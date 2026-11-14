package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CancelQrOrderRequest;
import com.roms.dto.request.QrOrderRequestCreateRequest;
import com.roms.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface QrOrderingService {

    QrContextResponse resolveContext(String qrToken);

    PageResponse<CategoryResponse> getCategories(String qrToken, Pageable pageable);

    PageResponse<MenuItemReadResponse> getMenu(String qrToken, Long categoryId, Pageable pageable);

    QrOrderRequestResponse submitQrOrder(String qrToken, QrOrderRequestCreateRequest request);

    QrOrdersTrackingResponse getSessionOrders(String qrToken);

    void callStaff(String qrToken);

    List<QrOrderRequestResponse> getPendingRequests();

    QrOrderRequestResponse confirmQrOrder(Long requestId);

    QrOrderRequestResponse cancelQrOrder(Long requestId, CancelQrOrderRequest request);
}
