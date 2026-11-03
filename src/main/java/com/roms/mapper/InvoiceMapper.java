package com.roms.mapper;

import com.roms.dto.response.CheckoutItemResponse;
import com.roms.dto.response.InvoiceResponse;
import com.roms.entity.Invoice;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper chuyển đổi giữa Invoice entity và InvoiceResponse
 * DTO.http://localhost:5173/
 */
@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice, List<CheckoutItemResponse> items) {
        if (invoice == null) {
            return null;
        }

        Long sessionId = null;
        Long tableId = null;
        String tableCode = null;
        Long closedById = null;
        String closedByName = null;

        ServiceSession session = invoice.getServiceSession();
        if (session != null) {
            sessionId = session.getId();
            RestaurantTable table = session.getTable();
            if (table != null) {
                tableId = table.getId();
                tableCode = table.getTableCode();
            }
            User closedBy = session.getClosedBy();
            if (closedBy != null) {
                closedById = closedBy.getId();
                closedByName = closedBy.getFullName();
            }
        }

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .sessionId(sessionId)
                .tableId(tableId)
                .tableCode(tableCode)
                .subtotal(invoice.getSubtotal())
                .discountType(invoice.getDiscountType())
                .discountValue(invoice.getDiscountValue())
                .discountAmount(invoice.getDiscountAmount())
                .discountReason(invoice.getDiscountReason())
                .finalAmount(invoice.getFinalAmount())
                .note(invoice.getNote())
                .createdAt(invoice.getCreatedAt())
                .closedById(closedById)
                .closedByName(closedByName)
                .items(items)
                .build();
    }
}
