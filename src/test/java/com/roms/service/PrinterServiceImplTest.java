package com.roms.service;

import com.roms.dto.response.*;
import com.roms.entity.MenuItem;
import com.roms.entity.Order;
import com.roms.entity.OrderItem;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.enums.OrderItemStatus;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.service.impl.PrinterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrinterServiceImplTest {

    @Mock
    private RestaurantSettingService settingService;

    @Mock
    private BillService billService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private PrinterService printerService;

    @BeforeEach
    void setUp() {
        System.setProperty("app.printer.simulate", "true");
        printerService = new PrinterServiceImpl(settingService, billService, orderRepository, orderItemRepository);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        System.clearProperty("app.printer.simulate");
    }

    @Test
    void getInstalledPrinters_returnsNonEmptyList_onWindows() {
        List<String> printers = printerService.getInstalledPrinters();
        assertThat(printers).isNotNull();
        // The host OS has at least Microsoft Print to PDF or similar
        assertThat(printers).isNotEmpty();
    }

    @Test
    void printBill_whenPrinterNotConfigured_skipsWithoutError() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .billPrinterName(null)
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        printerService.printBill(100L);

        verify(settingService, timeout(1000)).getSettings();
        verify(billService, never()).getBillById(anyLong());
    }

    @Test
    void printBill_whenPrinterConfigured_fetchesBill() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .restaurantName("ROMS Test")
                .billPrinterName("Microsoft Print to PDF")
                .billPaperSize("K80")
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        BillResponse bill = BillResponse.builder()
                .id(100L)
                .billCode("BILL-20261106-0001")
                .tableCode("Bàn 01")
                .amountBeforeDiscount(BigDecimal.valueOf(100000))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100000))
                .items(List.of(
                        BillItemResponse.builder()
                                .itemName("Phở Bò")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(50000))
                                .subtotal(BigDecimal.valueOf(100000))
                                .build()
                ))
                .build();
        when(billService.getBillById(100L)).thenReturn(bill);

        printerService.printBill(100L);

        verify(billService, timeout(1000)).getBillById(100L);
    }

    @Test
    void printProforma_whenPrinterConfigured_fetchesPreview() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .restaurantName("ROMS Test")
                .billPrinterName("Microsoft Print to PDF")
                .billPaperSize("K80")
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        BillPreviewResponse preview = BillPreviewResponse.builder()
                .sessionId(200L)
                .tableCode("Bàn 02")
                .openedAt(LocalDateTime.now())
                .subtotal(BigDecimal.valueOf(150000))
                .amountBeforeDiscount(BigDecimal.valueOf(150000))
                .items(List.of())
                .build();
        when(billService.getBillPreview(200L)).thenReturn(preview);

        printerService.printProforma(200L);

        verify(billService, timeout(1000)).getBillPreview(200L);
    }

    @Test
    void printKitchenReadyTicket_whenPrintingDisabled_skips() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .printReadyTicket(false)
                .readyTicketPrinterName("POS-80")
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        printerService.printKitchenReadyTicket(10L, List.of(1L, 2L));

        verify(settingService, timeout(1000)).getSettings();
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void printKitchenReadyTicket_whenEnabledAndConfigured_fetchesOrderAndItems() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .printReadyTicket(true)
                .readyTicketPrinterName("Microsoft Print to PDF")
                .readyTicketPaperSize("K80")
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        RestaurantTable table = RestaurantTable.builder().id(1L).tableCode("Bàn 05").build();
        ServiceSession session = ServiceSession.builder().id(50L).table(table).build();
        Order order = Order.builder().id(10L).serviceSession(session).createdAt(LocalDateTime.now()).build();
        when(orderRepository.findByIdWithSessionAndTable(10L)).thenReturn(Optional.of(order));

        MenuItem item = MenuItem.builder().id(1L).name("Cơm Chiên").build();
        OrderItem orderItem = OrderItem.builder().id(101L).menuItem(item).quantity(2).status(OrderItemStatus.READY).build();
        when(orderItemRepository.findAllByIdWithMenuItem(List.of(101L))).thenReturn(List.of(orderItem));

        printerService.printKitchenReadyTicket(10L, List.of(101L));

        verify(orderRepository, timeout(1000)).findByIdWithSessionAndTable(10L);
        verify(orderItemRepository, timeout(1000)).findAllByIdWithMenuItem(List.of(101L));
    }

    @Test
    void printKitchenReadyTicket_withA5PaperAndLongNameWithNote_executesSuccessfully() {
        RestaurantSettingResponse settings = RestaurantSettingResponse.builder()
                .printReadyTicket(true)
                .readyTicketPrinterName("Microsoft Print to PDF")
                .readyTicketPaperSize("A5")
                .build();
        when(settingService.getSettings()).thenReturn(settings);

        RestaurantTable table = RestaurantTable.builder().id(2L).tableCode("Bàn VIP 01").build();
        ServiceSession session = ServiceSession.builder().id(51L).table(table).build();
        Order order = Order.builder().id(11L).serviceSession(session).createdAt(LocalDateTime.now()).build();
        when(orderRepository.findByIdWithSessionAndTable(11L)).thenReturn(Optional.of(order));

        MenuItem item = MenuItem.builder().id(2L).name("Lẩu thái tomyum hải sản chua cay đặc biệt nồi lớn").build();
        OrderItem orderItem = OrderItem.builder()
                .id(102L)
                .menuItem(item)
                .quantity(1)
                .note("Ít cay, không bỏ ngò rí")
                .status(OrderItemStatus.READY)
                .build();
        when(orderItemRepository.findAllByIdWithMenuItem(List.of(102L))).thenReturn(List.of(orderItem));

        printerService.printKitchenReadyTicket(11L, List.of(102L));

        verify(orderRepository, timeout(1000)).findByIdWithSessionAndTable(11L);
        verify(orderItemRepository, timeout(1000)).findAllByIdWithMenuItem(List.of(102L));
    }
}
