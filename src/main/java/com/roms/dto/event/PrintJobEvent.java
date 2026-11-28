package com.roms.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintJobEvent {

    public enum JobType {
        BILL,
        PROFORMA,
        KITCHEN_READY
    }

    private String jobId;
    private JobType type;
    private String printerName;
    private String paperSize;

    private String restaurantName;
    private String address;
    private String phone;
    private String receiptFooter;

    private String tableCode;
    private String billCode;
    private String cashierName;
    private String openedAt;
    private String closedAt;

    private Long orderId;
    private String orderCreatedAt;

    private List<PrintJobItemDto> items;
    private BigDecimal amountBeforeDiscount;
    private BigDecimal discountAmount;
    private String promotionCode;
    private List<PrintJobTaxDto> taxRows;
    private BigDecimal totalAmount;
    private List<PrintJobPaymentDto> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintJobItemDto {
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintJobTaxDto {
        private BigDecimal rate;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintJobPaymentDto {
        private String method;
        private BigDecimal amount;
    }
}
