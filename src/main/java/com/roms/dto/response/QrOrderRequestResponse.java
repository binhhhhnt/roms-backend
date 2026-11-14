package com.roms.dto.response;

import com.roms.enums.QrOrderRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrOrderRequestResponse {

    private Long id;
    private Long serviceSessionId;
    private Long tableId;
    private String tableCode;
    private String area;
    private QrOrderRequestStatus status;
    private Long confirmedOrderId;
    private Long confirmedById;
    private String confirmedByName;
    private Long cancelledById;
    private String cancelledByName;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QrOrderRequestItemResponse> items;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QrOrderRequestItemResponse {
        private Long id;
        private Long menuItemId;
        private String menuItemName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private String note;
    }
}
