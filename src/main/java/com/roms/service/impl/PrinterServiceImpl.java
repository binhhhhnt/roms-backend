package com.roms.service.impl;

import com.roms.dto.response.BillPreviewItemDto;
import com.roms.dto.response.BillPreviewResponse;
import com.roms.dto.response.BillResponse;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.dto.response.TaxBreakdownDto;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.service.BillService;
import com.roms.service.PrinterService;
import com.roms.service.RestaurantSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterServiceImpl implements PrinterService {

    private final RestaurantSettingService settingService;
    private final BillService billService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,### đ");

    @Override
    public List<String> getInstalledPrinters() {
        try {
            PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
            if (printServices == null || printServices.length == 0) {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (PrintService service : printServices) {
                names.add(service.getName());
            }
            return names;
        } catch (Exception e) {
            log.error("Failed to lookup system print services: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void printBill(Long billId) {
        printExecutor.submit(() -> {
            try {
                RestaurantSettingResponse settings = settingService.getSettings();
                String printerName = settings.getBillPrinterName();
                String paperSize = settings.getBillPaperSize() != null ? settings.getBillPaperSize() : "K80";

                if (printerName == null || printerName.isBlank()) {
                    log.info("Bill printer is not configured. Skipping physical print for bill id: {}", billId);
                    return;
                }

                BillResponse bill = billService.getBillById(billId);
                executePrintJob(printerName, paperSize, "Hóa đơn thanh toán #" + bill.getBillCode(),
                        new BillReceiptPrintable(settings, bill));
            } catch (Exception e) {
                log.error("Error occurred while printing bill {}: {}", billId, e.getMessage(), e);
            }
        });
    }

    @Override
    public void printProforma(Long sessionId) {
        printExecutor.submit(() -> {
            try {
                RestaurantSettingResponse settings = settingService.getSettings();
                String printerName = settings.getBillPrinterName();
                String paperSize = settings.getBillPaperSize() != null ? settings.getBillPaperSize() : "K80";

                if (printerName == null || printerName.isBlank()) {
                    log.info("Bill printer is not configured. Skipping physical print for proforma session id: {}",
                            sessionId);
                    return;
                }

                BillPreviewResponse preview = billService.getBillPreview(sessionId);
                executePrintJob(printerName, paperSize, "Phiếu tạm tính bàn " + preview.getTableCode(),
                        new ProformaReceiptPrintable(settings, preview));
            } catch (Exception e) {
                log.error("Error occurred while printing proforma for session {}: {}", sessionId, e.getMessage(), e);
            }
        });
    }

    @Override
    public void printKitchenReadyTicket(Long orderId, List<Long> readyOrderItemIds) {
        printExecutor.submit(() -> {
            try {
                RestaurantSettingResponse settings = settingService.getSettings();
                Boolean isEnabled = settings.getPrintReadyTicket();
                if (isEnabled != null && !isEnabled) {
                    log.debug("Kitchen ready ticket printing is disabled by configuration.");
                    return;
                }

                String printerName = settings.getReadyTicketPrinterName();
                String paperSize = settings.getReadyTicketPaperSize() != null ? settings.getReadyTicketPaperSize()
                        : "K80";

                if (printerName == null || printerName.isBlank()) {
                    log.info("Kitchen ready ticket printer is not configured. Skipping ticket for order id: {}",
                            orderId);
                    return;
                }

                Order order = orderRepository.findById(orderId).orElse(null);
                if (order == null) {
                    log.warn("Order not found with id: {} for printing kitchen ready ticket", orderId);
                    return;
                }

                List<OrderItem> items;
                if (readyOrderItemIds != null && !readyOrderItemIds.isEmpty()) {
                    items = orderItemRepository.findAllById(readyOrderItemIds);
                } else {
                    items = orderItemRepository.findByOrderId(orderId);
                }

                if (items.isEmpty()) {
                    return;
                }

                String tableCode = order.getServiceSession() != null && order.getServiceSession().getTable() != null
                        ? order.getServiceSession().getTable().getTableCode()
                        : "N/A";

                executePrintJob(printerName, paperSize, "Phiếu ra món bàn " + tableCode,
                        new KitchenReadyTicketPrintable(tableCode, order.getId(), order.getCreatedAt(), items));
            } catch (Exception e) {
                log.error("Error occurred while printing kitchen ready ticket for order {}: {}", orderId,
                        e.getMessage(), e);
            }
        });
    }

    private void executePrintJob(String printerName, String paperSize, String jobName, Printable printable) {
        if (GraphicsEnvironment.isHeadless()) {
            log.info("GraphicsEnvironment is headless. Simulating successful print job '{}' on printer '{}'", jobName,
                    printerName);
            return;
        }

        PrintService targetService = findPrintService(printerName);
        if (targetService == null) {
            log.warn("Printer '{}' was not found among installed system printers. Print job '{}' cancelled.",
                    printerName, jobName);
            return;
        }

        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(targetService);
            job.setJobName(jobName);

            PageFormat pageFormat = createPageFormat(job, paperSize);
            job.setPrintable(printable, pageFormat);
            job.print();
            log.info("Successfully sent print job '{}' to printer '{}' (Paper: {})", jobName, printerName, paperSize);
        } catch (PrinterException e) {
            log.error("Failed to print job '{}' to printer '{}': {}", jobName, printerName, e.getMessage(), e);
        }
    }

    private PrintService findPrintService(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return null;
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null) {
            return null;
        }
        for (PrintService service : services) {
            if (service.getName().trim().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        return null;
    }

    private PageFormat createPageFormat(PrinterJob job, String paperSize) {
        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        double width;
        double height;
        double margin;

        if ("K58".equalsIgnoreCase(paperSize)) {
            width = 164.4; // 58mm in points
            height = 2500; // Continuous thermal roll
            margin = 6;
        } else if ("A5".equalsIgnoreCase(paperSize)) {
            width = 419.5; // 148mm
            height = 595.3; // 210mm
            margin = 20;
        } else { // default K80
            width = 226.8; // 80mm
            height = 2500; // Continuous roll
            margin = 10;
        }

        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - (2 * margin), height - (2 * margin));
        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    // =========================================================================
    // Printable Implementation for Official Bill Receipt
    // =========================================================================
    private static class BillReceiptPrintable implements Printable {
        private final RestaurantSettingResponse settings;
        private final BillResponse bill;

        public BillReceiptPrintable(RestaurantSettingResponse settings, BillResponse bill) {
            this.settings = settings;
            this.bill = bill;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            int width = (int) pageFormat.getImageableWidth();

            int y = 14;
            // 1. Restaurant Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            y = drawCenter(g2d, settings.getRestaurantName() != null ? settings.getRestaurantName() : "Nhà hàng ROMS",
                    y, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (settings.getAddress() != null && !settings.getAddress().isBlank()) {
                y = drawCenter(g2d, settings.getAddress(), y, width);
            }
            if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
                y = drawCenter(g2d, "Hotline: " + settings.getPhone(), y, width);
            }

            y += 4;
            // 2. Receipt Title
            g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
            y = drawCenter(g2d, "HÓA ĐƠN THANH TOÁN", y, width);

            // 3. Metadata
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Số hóa đơn: " + bill.getBillCode(), "Bàn: " + bill.getTableCode(), y, width);
            y = drawRow(g2d, "Thu ngân: " + (bill.getCreatedByName() != null ? bill.getCreatedByName() : "Thu ngân"),
                    "Giờ vào: " + (bill.getOpenedAt() != null ? bill.getOpenedAt().format(DATE_TIME_FORMATTER) : "-"),
                    y, width);
            y = drawRow(g2d,
                    "Giờ ra: " + (bill.getClosedAt() != null ? bill.getClosedAt().format(DATE_TIME_FORMATTER)
                            : LocalDateTime.now().format(DATE_TIME_FORMATTER)),
                    "", y, width);

            // 4. Items Table
            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
            y = drawTableHeaders(g2d, y, width);
            y = drawDivider(g2d, y, width, true);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (bill.getItems() != null) {
                int idx = 1;
                for (var item : bill.getItems()) {
                    y = drawItemRow(g2d, idx++, item.getItemName(), item.getQuantity(), item.getUnitPrice(),
                            item.getSubtotal(), y, width);
                }
            }

            // 5. Totals & Discounts
            y = drawDivider(g2d, y, width, true);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Tổng tiền hàng:", CURRENCY_FORMAT.format(bill.getAmountBeforeDiscount()), y, width);

            if (bill.getDiscountAmount() != null && bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                String promoNote = bill.getPromotionCode() != null ? " (" + bill.getPromotionCode() + ")" : "";
                y = drawRow(g2d, "Khuyến mãi" + promoNote + ":", "-" + CURRENCY_FORMAT.format(bill.getDiscountAmount()),
                        y, width);
            }

            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            y = drawRow(g2d, "TỔNG THANH TOÁN:", CURRENCY_FORMAT.format(bill.getTotalAmount()), y, width);

            // Payment methods
            if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
                for (var p : bill.getPayments()) {
                    String methodText = com.roms.enums.PaymentMethod.CASH.equals(p.getPaymentMethod()) ? "Tiền mặt"
                            : "Chuyển khoản";
                    y = drawRow(g2d, "- " + methodText + ":", CURRENCY_FORMAT.format(p.getAmount()), y, width);
                }
            }

            // 6. Footer
            if (settings.getReceiptFooter() != null && !settings.getReceiptFooter().isBlank()) {
                y = drawDivider(g2d, y, width, true);
                g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
                y = drawCenter(g2d, settings.getReceiptFooter(), y, width);
            }

            return PAGE_EXISTS;
        }
    }

    // =========================================================================
    // Printable Implementation for Proforma Receipt (Phiếu tạm tính)
    // =========================================================================
    private static class ProformaReceiptPrintable implements Printable {
        private final RestaurantSettingResponse settings;
        private final BillPreviewResponse preview;

        public ProformaReceiptPrintable(RestaurantSettingResponse settings, BillPreviewResponse preview) {
            this.settings = settings;
            this.preview = preview;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            int width = (int) pageFormat.getImageableWidth();

            int y = 14;
            // 1. Restaurant Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            y = drawCenter(g2d, settings.getRestaurantName() != null ? settings.getRestaurantName() : "Nhà hàng ROMS",
                    y, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (settings.getAddress() != null && !settings.getAddress().isBlank()) {
                y = drawCenter(g2d, settings.getAddress(), y, width);
            }
            if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
                y = drawCenter(g2d, "Hotline: " + settings.getPhone(), y, width);
            }

            y += 4;
            // 2. Receipt Title
            g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
            y = drawCenter(g2d, "PHIẾU TẠM TÍNH", y, width);

            // 3. Metadata
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Bàn: " + preview.getTableCode(),
                    "Giờ vào: "
                            + (preview.getOpenedAt() != null ? preview.getOpenedAt().format(DATE_TIME_FORMATTER) : "-"),
                    y, width);
            y = drawRow(g2d, "Thời điểm in: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), "", y, width);

            // 4. Items Table
            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
            y = drawTableHeaders(g2d, y, width);
            y = drawDivider(g2d, y, width, true);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (preview.getItems() != null) {
                int idx = 1;
                for (BillPreviewItemDto item : preview.getItems()) {
                    y = drawItemRow(g2d, idx++, item.getItemName(), item.getQuantity(), item.getUnitPrice(),
                            item.getSubtotal(), y, width);
                }
            }

            // 5. Totals & Taxes
            y = drawDivider(g2d, y, width, true);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Tổng tiền món:", CURRENCY_FORMAT.format(preview.getSubtotal()), y, width);

            if (preview.getTaxBreakdowns() != null && !preview.getTaxBreakdowns().isEmpty()) {
                for (TaxBreakdownDto tax : preview.getTaxBreakdowns()) {
                    y = drawRow(g2d, "Thuế VAT (" + tax.getRate() + "%):", CURRENCY_FORMAT.format(tax.getTaxAmount()),
                            y, width);
                }
            }

            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            y = drawRow(g2d, "TỔNG TẠM TÍNH:", CURRENCY_FORMAT.format(preview.getAmountBeforeDiscount()), y, width);

            // 6. Footer
            y = drawDivider(g2d, y, width, true);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
            y = drawCenter(g2d, "(Phiếu tạm tính dùng để kiểm tra món trước khi thanh toán)", y, width);
            if (settings.getReceiptFooter() != null && !settings.getReceiptFooter().isBlank()) {
                y = drawCenter(g2d, settings.getReceiptFooter(), y, width);
            }

            return PAGE_EXISTS;
        }
    }

    // =========================================================================
    // Printable Implementation for Kitchen Ready Ticket
    // =========================================================================
    private static class KitchenReadyTicketPrintable implements Printable {
        private final String tableCode;
        private final Long orderId;
        private final LocalDateTime orderCreatedAt;
        private final List<OrderItem> items;

        public KitchenReadyTicketPrintable(String tableCode, Long orderId, LocalDateTime orderCreatedAt,
                List<OrderItem> items) {
            this.tableCode = tableCode;
            this.orderId = orderId;
            this.orderCreatedAt = orderCreatedAt;
            this.items = items;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            int width = (int) pageFormat.getImageableWidth();

            int y = 16;
            // 1. Table Name Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            y = drawCenter(g2d, "BÀN: " + tableCode, y, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
            y = drawCenter(g2d, "Mã order: #" + orderId, y, width);
            if (orderCreatedAt != null) {
                y = drawCenter(g2d, "Đặt lúc: " + orderCreatedAt.format(DATE_TIME_FORMATTER), y, width);
            }

            y += 4;
            // 2. Large "ĐÃ XONG" Announcement
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            y = drawCenter(g2d, "--- ĐÃ XONG ---", y, width);
            y += 4;

            // 3. Two-Column Table: Tên món | Số lượng
            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            int colQtyX = width - 40;
            g2d.drawString("Tên món", 0, y);
            g2d.drawString("SL", colQtyX, y);
            y += g2d.getFontMetrics().getHeight();
            y = drawDivider(g2d, y, width, true);

            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            for (OrderItem item : items) {
                String itemName = item.getMenuItem() != null ? item.getMenuItem().getName() : "Món #" + item.getId();
                int qty = item.getQuantity();
                g2d.drawString(itemName, 0, y);
                g2d.drawString(String.valueOf(qty), colQtyX, y);
                y += g2d.getFontMetrics().getHeight() + 2;
            }

            // 4. Footer timestamp
            y = drawDivider(g2d, y, width, false);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
            y = drawCenter(g2d, "Giờ hoàn tất: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), y, width);

            return PAGE_EXISTS;
        }
    }

    // =========================================================================
    // Drawing Utility Helpers
    // =========================================================================
    private static int drawCenter(Graphics2D g2d, String text, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g2d.drawString(text, Math.max(0, x), y);
        return y + fm.getHeight();
    }

    private static int drawRow(Graphics2D g2d, String left, String right, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(left, 0, y);
        int rightX = width - fm.stringWidth(right);
        g2d.drawString(right, Math.max(0, rightX), y);
        return y + fm.getHeight();
    }

    private static int drawDivider(Graphics2D g2d, int y, int width, boolean isDashed) {
        Stroke old = g2d.getStroke();
        if (isDashed) {
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0));
        } else {
            g2d.setStroke(new BasicStroke(1));
        }
        g2d.drawLine(0, y, width, y);
        g2d.setStroke(old);
        return y + 6;
    }

    private static int drawTableHeaders(Graphics2D g2d, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int colIdx = 0;
        int colName = 14;
        int colQty = width - 90;
        int colPrice = width - 65;
        int colTotal = width - 35;

        g2d.drawString("#", colIdx, y);
        g2d.drawString("Tên món", colName, y);
        g2d.drawString("SL", colQty, y);
        g2d.drawString("Đ.Giá", colPrice, y);
        g2d.drawString("T.Tiền", colTotal, y);
        return y + fm.getHeight();
    }

    private static int drawItemRow(Graphics2D g2d, int idx, String name, int qty, BigDecimal price,
            BigDecimal lineTotal, int y, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int colIdx = 0;
        int colName = 14;
        int colQty = width - 90;
        int colPrice = width - 65;
        int colTotal = width - 35;

        String formattedPrice = price != null ? String.valueOf(price.longValue() / 1000) + "k" : "0";
        String formattedTotal = lineTotal != null ? String.valueOf(lineTotal.longValue() / 1000) + "k" : "0";

        // Truncate name if it exceeds column
        String displayName = name;
        if (fm.stringWidth(displayName) > (colQty - colName - 4)) {
            while (displayName.length() > 3 && fm.stringWidth(displayName + "..") > (colQty - colName - 4)) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "..";
        }

        g2d.drawString(String.valueOf(idx), colIdx, y);
        g2d.drawString(displayName, colName, y);
        g2d.drawString(String.valueOf(qty), colQty, y);
        g2d.drawString(formattedPrice, colPrice, y);
        g2d.drawString(formattedTotal, colTotal, y);
        return y + fm.getHeight();
    }
}
