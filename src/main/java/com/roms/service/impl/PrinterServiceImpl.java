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
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
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
                        new BillReceiptPrintable(settings, bill, paperSize));
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
                        new ProformaReceiptPrintable(settings, preview, paperSize));
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

                Order order = orderRepository.findByIdWithSessionAndTable(orderId).orElse(null);
                if (order == null) {
                    log.warn("Order not found with id: {} for printing kitchen ready ticket", orderId);
                    return;
                }

                List<OrderItem> items;
                if (readyOrderItemIds != null && !readyOrderItemIds.isEmpty()) {
                    items = orderItemRepository.findAllByIdWithMenuItem(readyOrderItemIds);
                } else {
                    items = orderItemRepository.findByOrderIdWithMenuItem(orderId);
                }

                if (items.isEmpty()) {
                    return;
                }

                String tableCode = order.getServiceSession() != null && order.getServiceSession().getTable() != null
                        ? order.getServiceSession().getTable().getTableCode()
                        : "N/A";

                executePrintJob(printerName, paperSize, "Phiếu ra món bàn " + tableCode,
                        new KitchenReadyTicketPrintable(tableCode, order.getId(), order.getCreatedAt(), items, paperSize));
            } catch (Exception e) {
                log.error("Error occurred while printing kitchen ready ticket for order {}: {}", orderId,
                        e.getMessage(), e);
            }
        });
    }

    private void executePrintJob(String printerName, String paperSize, String jobName, Printable printable) {
        PrintService targetService = findPrintService(printerName);
        if (targetService == null) {
            log.warn("Printer '{}' was not found among installed system printers. Print job '{}' cancelled.",
                    printerName, jobName);
            return;
        }

        // Avoid popups/blocking during automated unit testing
        if (Boolean.getBoolean("app.printer.simulate")) {
            log.info("Simulation environment detected. Simulating successful print job '{}' on printer '{}'",
                    jobName, printerName);
            return;
        }

        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(targetService);
            job.setJobName(jobName);

            PageFormat pageFormat = createPageFormat(job, paperSize, targetService);
            PageFormat validatedPageFormat = job.validatePage(pageFormat);
            job.setPrintable(printable, validatedPageFormat);
            job.print();
            log.info("Successfully sent print job '{}' to printer '{}' (Paper: {})", jobName, printerName, paperSize);
        } catch (PrinterException e) {
            log.error("Failed to print job '{}' to printer '{}': {}", jobName, printerName, e.getMessage(), e);
        } catch (Throwable t) {
            log.error("Unexpected error during print job '{}': {}", jobName, t.getMessage(), t);
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

    private PageFormat createPageFormat(PrinterJob job, String paperSize, PrintService service) {
        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        boolean isSheet = isSheetPrinter(service, job);

        if (isSheet) {
            // Desktop/laser sheet printer (Pantum, HP, Canon...):
            // Pass the physical cut-sheet dimensions supported by the printer (A5: 148x210mm or printer default)
            // NEVER pass continuous roll height (2500 pt / 881 mm) because laser drivers cannot handle roll paper.
            double sheetWidth = 419.5;  // A5 width: 148mm
            double sheetHeight = 595.3; // A5 height: 210mm
            double margin = 20.0;

            PageFormat defPf = job.defaultPage();
            if (defPf.getWidth() > 500.0) {
                // If printer default is A4 or Letter, adapt to tray default
                sheetWidth = defPf.getWidth();
                sheetHeight = defPf.getHeight();
            }

            paper.setSize(sheetWidth, sheetHeight);
            paper.setImageableArea(margin, margin, sheetWidth - (2 * margin), sheetHeight - (2 * margin));
        } else {
            // POS thermal roll printers (Epson, Xprinter):
            double width;
            double height = 2500.0; // continuous thermal roll
            double margin;

            if ("K58".equalsIgnoreCase(paperSize)) {
                width = 164.4; // 58mm in points
                margin = 6.0;
            } else { // default K80
                width = 226.8; // 80mm in points
                margin = 10.0;
            }

            paper.setSize(width, height);
            paper.setImageableArea(margin, margin, width - (2 * margin), height - (2 * margin));
        }

        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    private boolean isSheetPrinter(PrintService printService, PrinterJob job) {
        if (printService == null) {
            return false;
        }
        // 1. Check defaultPage dimensions (Desktop/laser sheet printers have default width >= 300 pt)
        try {
            PageFormat defPf = job.defaultPage();
            if (defPf.getWidth() >= 300.0) {
                return true;
            }
        } catch (Exception ignored) {
        }

        // 2. Check supported media types for standard cut-sheet sizes
        try {
            Object values = printService.getSupportedAttributeValues(Media.class, null, null);
            if (values instanceof Media[]) {
                for (Media m : (Media[]) values) {
                    if (m instanceof MediaSizeName) {
                        MediaSizeName msn = (MediaSizeName) m;
                        if (msn.equals(MediaSizeName.ISO_A4) || msn.equals(MediaSizeName.ISO_A5)
                                || msn.equals(MediaSizeName.NA_LETTER) || msn.equals(MediaSizeName.ISO_B5)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Fallback check by brand/driver keywords
        String name = printService.getName().toLowerCase();
        return name.contains("laser") || name.contains("pantum") || name.contains("canon")
                || name.contains("hp ") || name.contains("brother") || name.contains("epson l")
                || name.contains("pdf") || name.contains("xps") || name.contains("onenote");
    }

    // Helper to calculate centered ticket width and starting X coordinate on any paper
    private static class TicketLayout {
        final int startX;
        final int width;

        TicketLayout(int startX, int width) {
            this.startX = startX;
            this.width = width;
        }

        static TicketLayout calculate(PageFormat pageFormat, String paperSize) {
            double pageWidth = pageFormat.getImageableWidth();
            double targetWidth;
            if ("K58".equalsIgnoreCase(paperSize)) {
                targetWidth = 144.0; // ~51mm printable
            } else if ("A5".equalsIgnoreCase(paperSize)) {
                targetWidth = pageWidth; // full page width
            } else { // default K80
                targetWidth = 206.8; // ~73mm printable (80mm roll)
            }

            int width = (int) Math.min(targetWidth, pageWidth);
            // Center horizontally on page (like CSS margin: 0 auto)
            int startX = (int) Math.max(0, (pageWidth - width) / 2);
            return new TicketLayout(startX, width);
        }
    }

    // =========================================================================
    // Printable Implementation for Official Bill Receipt
    // =========================================================================
    private static class BillReceiptPrintable implements Printable {
        private final RestaurantSettingResponse settings;
        private final BillResponse bill;
        private final String paperSize;

        public BillReceiptPrintable(RestaurantSettingResponse settings, BillResponse bill, String paperSize) {
            this.settings = settings;
            this.bill = bill;
            this.paperSize = paperSize;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            TicketLayout layout = TicketLayout.calculate(pageFormat, paperSize);
            int startX = layout.startX;
            int width = layout.width;

            int y = 14;
            // 1. Restaurant Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            y = drawCenter(g2d, settings.getRestaurantName() != null ? settings.getRestaurantName() : "Nhà hàng ROMS",
                    y, startX, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (settings.getAddress() != null && !settings.getAddress().isBlank()) {
                y = drawCenter(g2d, settings.getAddress(), y, startX, width);
            }
            if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
                y = drawCenter(g2d, "Hotline: " + settings.getPhone(), y, startX, width);
            }

            y += 4;
            // 2. Receipt Title
            g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
            y = drawCenter(g2d, "HÓA ĐƠN THANH TOÁN", y, startX, width);

            // 3. Metadata
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Số hóa đơn: " + bill.getBillCode(), "Bàn: " + bill.getTableCode(), y, startX, width);
            y = drawRow(g2d, "Thu ngân: " + (bill.getCreatedByName() != null ? bill.getCreatedByName() : "Thu ngân"),
                    "Giờ vào: " + (bill.getOpenedAt() != null ? bill.getOpenedAt().format(DATE_TIME_FORMATTER) : "-"),
                    y, startX, width);
            y = drawRow(g2d,
                    "Giờ ra: " + (bill.getClosedAt() != null ? bill.getClosedAt().format(DATE_TIME_FORMATTER)
                            : LocalDateTime.now().format(DATE_TIME_FORMATTER)),
                    "", y, startX, width);

            // 4. Items Table
            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
            y = drawTableHeaders(g2d, y, startX, width);
            y = drawDivider(g2d, y, startX, width, true);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (bill.getItems() != null) {
                int idx = 1;
                for (var item : bill.getItems()) {
                    y = drawItemRow(g2d, idx++, item.getItemName(), item.getQuantity(), item.getUnitPrice(),
                            item.getSubtotal(), y, startX, width);
                }
            }

            // 5. Totals & Discounts
            y = drawDivider(g2d, y, startX, width, true);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Tổng tiền hàng:", CURRENCY_FORMAT.format(bill.getAmountBeforeDiscount()), y, startX, width);

            if (bill.getDiscountAmount() != null && bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                String promoNote = bill.getPromotionCode() != null ? " (" + bill.getPromotionCode() + ")" : "";
                y = drawRow(g2d, "Khuyến mãi" + promoNote + ":", "-" + CURRENCY_FORMAT.format(bill.getDiscountAmount()),
                        y, startX, width);
            }

            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            y = drawRow(g2d, "TỔNG THANH TOÁN:", CURRENCY_FORMAT.format(bill.getTotalAmount()), y, startX, width);

            // Payment methods
            if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
                for (var p : bill.getPayments()) {
                    String methodText = com.roms.enums.PaymentMethod.CASH.equals(p.getPaymentMethod()) ? "Tiền mặt"
                            : "Chuyển khoản";
                    y = drawRow(g2d, "- " + methodText + ":", CURRENCY_FORMAT.format(p.getAmount()), y, startX, width);
                }
            }

            // 6. Footer
            if (settings.getReceiptFooter() != null && !settings.getReceiptFooter().isBlank()) {
                y = drawDivider(g2d, y, startX, width, true);
                g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
                y = drawCenter(g2d, settings.getReceiptFooter(), y, startX, width);
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
        private final String paperSize;

        public ProformaReceiptPrintable(RestaurantSettingResponse settings, BillPreviewResponse preview, String paperSize) {
            this.settings = settings;
            this.preview = preview;
            this.paperSize = paperSize;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            TicketLayout layout = TicketLayout.calculate(pageFormat, paperSize);
            int startX = layout.startX;
            int width = layout.width;

            int y = 14;
            // 1. Restaurant Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            y = drawCenter(g2d, settings.getRestaurantName() != null ? settings.getRestaurantName() : "Nhà hàng ROMS",
                    y, startX, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (settings.getAddress() != null && !settings.getAddress().isBlank()) {
                y = drawCenter(g2d, settings.getAddress(), y, startX, width);
            }
            if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
                y = drawCenter(g2d, "Hotline: " + settings.getPhone(), y, startX, width);
            }

            y += 4;
            // 2. Receipt Title
            g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
            y = drawCenter(g2d, "PHIẾU TẠM TÍNH", y, startX, width);

            // 3. Metadata
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Bàn: " + preview.getTableCode(),
                    "Giờ vào: "
                            + (preview.getOpenedAt() != null ? preview.getOpenedAt().format(DATE_TIME_FORMATTER) : "-"),
                    y, startX, width);
            y = drawRow(g2d, "Thời điểm in: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), "", y, startX, width);

            // 4. Items Table
            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
            y = drawTableHeaders(g2d, y, startX, width);
            y = drawDivider(g2d, y, startX, width, true);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            if (preview.getItems() != null) {
                int idx = 1;
                for (BillPreviewItemDto item : preview.getItems()) {
                    y = drawItemRow(g2d, idx++, item.getItemName(), item.getQuantity(), item.getUnitPrice(),
                            item.getSubtotal(), y, startX, width);
                }
            }

            // 5. Totals & Taxes
            y = drawDivider(g2d, y, startX, width, true);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
            y = drawRow(g2d, "Tổng tiền món:", CURRENCY_FORMAT.format(preview.getSubtotal()), y, startX, width);

            if (preview.getTaxBreakdowns() != null && !preview.getTaxBreakdowns().isEmpty()) {
                for (TaxBreakdownDto tax : preview.getTaxBreakdowns()) {
                    y = drawRow(g2d, "Thuế VAT (" + tax.getRate() + "%):", CURRENCY_FORMAT.format(tax.getTaxAmount()),
                            y, startX, width);
                }
            }

            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            y = drawRow(g2d, "TỔNG TẠM TÍNH:", CURRENCY_FORMAT.format(preview.getAmountBeforeDiscount()), y, startX, width);

            // 6. Footer
            y = drawDivider(g2d, y, startX, width, true);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
            y = drawCenter(g2d, "(Phiếu tạm tính dùng để kiểm tra món trước khi thanh toán)", y, startX, width);
            if (settings.getReceiptFooter() != null && !settings.getReceiptFooter().isBlank()) {
                y = drawCenter(g2d, settings.getReceiptFooter(), y, startX, width);
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
        private final String paperSize;

        public KitchenReadyTicketPrintable(String tableCode, Long orderId, LocalDateTime orderCreatedAt,
                List<OrderItem> items, String paperSize) {
            this.tableCode = tableCode;
            this.orderId = orderId;
            this.orderCreatedAt = orderCreatedAt;
            this.items = items;
            this.paperSize = paperSize;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            TicketLayout layout = TicketLayout.calculate(pageFormat, paperSize);
            int startX = layout.startX;
            int width = layout.width;

            int y = 16;
            // 1. Table Name Header
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            y = drawCenter(g2d, "BÀN: " + tableCode, y, startX, width);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
            y = drawCenter(g2d, "Mã order: #" + orderId, y, startX, width);
            if (orderCreatedAt != null) {
                y = drawCenter(g2d, "Đặt lúc: " + orderCreatedAt.format(DATE_TIME_FORMATTER), y, startX, width);
            }

            y += 4;
            // 2. Large "ĐÃ XONG" Announcement
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            y = drawCenter(g2d, "--- ĐÃ XONG ---", y, startX, width);
            y += 4;

            // 3. Two-Column Table: Tên món | Số lượng
            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            int colQtyX = startX + width - 35;
            g2d.drawString("Tên món", startX, y);
            g2d.drawString("SL", colQtyX, y);
            y += g2d.getFontMetrics().getHeight();
            y = drawDivider(g2d, y, startX, width, true);

            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            for (OrderItem item : items) {
                String itemName = item.getMenuItem() != null ? item.getMenuItem().getName() : "Món #" + item.getId();
                int qty = item.getQuantity();
                y = drawKitchenItem(g2d, itemName, qty, item.getNote(), y, startX, colQtyX);
            }

            // 4. Footer timestamp
            y = drawDivider(g2d, y, startX, width, false);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 8));
            y = drawCenter(g2d, "Giờ hoàn tất: " + LocalDateTime.now().format(DATE_TIME_FORMATTER), y, startX, width);

            return PAGE_EXISTS;
        }
    }

    private static int drawKitchenItem(Graphics2D g2d, String name, int qty, String note, int y, int startX, int colQtyX) {
        FontMetrics fm = g2d.getFontMetrics();
        int maxNameWidth = colQtyX - startX - 8;
        String qtyStr = String.valueOf(qty);

        // Draw quantity on the right column
        g2d.drawString(qtyStr, colQtyX, y);

        // Draw dish name with wrapping if it exceeds available width
        if (fm.stringWidth(name) <= maxNameWidth) {
            g2d.drawString(name, startX, y);
            y += fm.getHeight() + 2;
        } else {
            String[] words = name.split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (fm.stringWidth(testLine) <= maxNameWidth) {
                    currentLine = new StringBuilder(testLine);
                } else {
                    if (!currentLine.isEmpty()) {
                        g2d.drawString(currentLine.toString(), startX, y);
                        y += fm.getHeight() + 1;
                    }
                    currentLine = new StringBuilder(word);
                }
            }
            if (!currentLine.isEmpty()) {
                g2d.drawString(currentLine.toString(), startX, y);
                y += fm.getHeight() + 2;
            }
        }

        // Draw item note if present (e.g. "Ít cay", "Không đường")
        if (note != null && !note.isBlank()) {
            Font oldFont = g2d.getFont();
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 9));
            g2d.drawString("  * " + note.trim(), startX + 4, y);
            y += g2d.getFontMetrics().getHeight() + 2;
            g2d.setFont(oldFont);
        }

        return y;
    }

    // =========================================================================
    // Drawing Utility Helpers
    // =========================================================================
    private static int drawCenter(Graphics2D g2d, String text, int y, int startX, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = startX + Math.max(0, (width - fm.stringWidth(text)) / 2);
        g2d.drawString(text, x, y);
        return y + fm.getHeight();
    }

    private static int drawRow(Graphics2D g2d, String left, String right, int y, int startX, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(left, startX, y);
        int rightX = startX + width - fm.stringWidth(right);
        g2d.drawString(right, Math.max(startX, rightX), y);
        return y + fm.getHeight();
    }

    private static int drawDivider(Graphics2D g2d, int y, int startX, int width, boolean isDashed) {
        Stroke old = g2d.getStroke();
        if (isDashed) {
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0));
        } else {
            g2d.setStroke(new BasicStroke(1));
        }
        g2d.drawLine(startX, y, startX + width, y);
        g2d.setStroke(old);
        return y + 6;
    }

    private static int drawTableHeaders(Graphics2D g2d, int y, int startX, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int colIdx = startX;
        int colName = startX + 14;
        int colQty = startX + width - 90;
        int colPrice = startX + width - 65;
        int colTotal = startX + width - 35;

        g2d.drawString("#", colIdx, y);
        g2d.drawString("Tên món", colName, y);
        g2d.drawString("SL", colQty, y);
        g2d.drawString("Đ.Giá", colPrice, y);
        g2d.drawString("T.Tiền", colTotal, y);
        return y + fm.getHeight();
    }

    private static int drawItemRow(Graphics2D g2d, int idx, String name, int qty, BigDecimal price,
            BigDecimal lineTotal, int y, int startX, int width) {
        FontMetrics fm = g2d.getFontMetrics();
        int colIdx = startX;
        int colName = startX + 14;
        int colQty = startX + width - 90;
        int colPrice = startX + width - 65;
        int colTotal = startX + width - 35;

        String formattedPrice = price != null ? String.valueOf(price.longValue() / 1000) + "k" : "0";
        String formattedTotal = lineTotal != null ? String.valueOf(lineTotal.longValue() / 1000) + "k" : "0";

        // Truncate name if it exceeds column
        String displayName = name;
        int maxNameWidth = colQty - colName - 4;
        if (fm.stringWidth(displayName) > maxNameWidth) {
            while (displayName.length() > 3 && fm.stringWidth(displayName + "..") > maxNameWidth) {
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
