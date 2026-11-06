package com.roms.service;

import java.util.List;

public interface PrinterService {

    /**
     * Enumerates all printer devices installed on the host operating system.
     */
    List<String> getInstalledPrinters();

    /**
     * Prints an official payment receipt for a completed bill using the configured bill printer.
     */
    void printBill(Long billId);

    /**
     * Prints a proforma receipt (phiếu tạm tính) for an active dining session.
     */
    void printProforma(Long sessionId);

    /**
     * Prints a kitchen ready ticket when items in an order are ready for pickup.
     *
     * @param orderId ID of the parent order
     * @param readyOrderItemIds IDs of items marked READY
     */
    void printKitchenReadyTicket(Long orderId, List<Long> readyOrderItemIds);
}

