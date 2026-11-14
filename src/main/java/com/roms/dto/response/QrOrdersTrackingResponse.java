package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrOrdersTrackingResponse {
    private Long sessionId;
    private Long tableId;
    private String tableCode;
    private List<QrOrderRequestResponse> pendingRequests;
    private List<OrderResponse> confirmedOrders;
}
