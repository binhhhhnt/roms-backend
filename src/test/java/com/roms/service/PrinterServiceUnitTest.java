package com.roms.service;

import com.roms.dto.response.*;
import com.roms.repository.*;
import com.roms.service.impl.PrinterServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.print.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No system printer is contacted; print tasks run synchronously with mock dependencies. */
class PrinterServiceUnitTest {
    RestaurantSettingService settings;
    BillService bills;
    OrderRepository orders;
    OrderItemRepository items;
    PrinterServiceImpl service;
    ExecutorService originalExecutor;
    @BeforeEach void setUp() {
        settings = mock(RestaurantSettingService.class); bills = mock(BillService.class);
        orders = mock(OrderRepository.class); items = mock(OrderItemRepository.class);
        service = new PrinterServiceImpl(settings,bills,orders,items);
        originalExecutor = (ExecutorService) ReflectionTestUtils.getField(service,"printExecutor");
        var executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(i -> { ((Runnable)i.getArgument(0)).run(); return CompletableFuture.completedFuture(null); });
        ReflectionTestUtils.setField(service,"printExecutor",executor);
    }
    @AfterEach void shutdown() { originalExecutor.shutdownNow(); }
    @Test @DisplayName("Printer: enumerate mocked printer names without querying the operating system")
    void listMockPrinters() {
        var printer = mock(PrintService.class); when(printer.getName()).thenReturn("Test printer");
        try(var lookup = mockStatic(PrintServiceLookup.class)) {
            lookup.when(() -> PrintServiceLookup.lookupPrintServices(null,null)).thenReturn(new PrintService[]{printer});
            assertThat(service.getInstalledPrinters()).containsExactly("Test printer");
        }
    }
    @Test @DisplayName("Printer: no installed printers returns an empty list")
    void noPrinters() {
        try(var lookup = mockStatic(PrintServiceLookup.class)) {
            lookup.when(() -> PrintServiceLookup.lookupPrintServices(null,null)).thenReturn(new PrintService[0]);
            assertThat(service.getInstalledPrinters()).isEmpty();
        }
    }
    @Test @DisplayName("Printer: unconfigured bill printer does not fetch or print a bill")
    void unconfiguredBillPrinter() {
        when(settings.getSettings()).thenReturn(RestaurantSettingResponse.builder().billPrinterName(" ").build());
        service.printBill(1L);
        verifyNoInteractions(bills,orders,items);
    }
    @Test @DisplayName("Printer: unconfigured proforma printer does not fetch a preview")
    void unconfiguredProforma() {
        when(settings.getSettings()).thenReturn(RestaurantSettingResponse.builder().build());
        service.printProforma(9L);
        verifyNoInteractions(bills);
    }
    @Test @DisplayName("Printer: disabled ready tickets do not read orders")
    void disabledReadyTickets() {
        when(settings.getSettings()).thenReturn(RestaurantSettingResponse.builder().printReadyTicket(false).readyTicketPrinterName("Test").build());
        service.printKitchenReadyTicket(1L,List.of(2L));
        verifyNoInteractions(orders,items,bills);
    }
    @Test @DisplayName("Printer: missing kitchen order stops before looking up items")
    void missingKitchenOrder() {
        when(settings.getSettings()).thenReturn(RestaurantSettingResponse.builder().printReadyTicket(true).readyTicketPrinterName("Test").build());
        when(orders.findById(1L)).thenReturn(Optional.empty());
        service.printKitchenReadyTicket(1L,List.of(2L));
        verify(orders).findById(1L); verifyNoInteractions(items);
    }
    @Test @DisplayName("Printer: configured but unavailable device fetches bill and never submits a physical job")
    void unavailablePrinter() {
        when(settings.getSettings()).thenReturn(RestaurantSettingResponse.builder().billPrinterName("Missing-device").build());
        when(bills.getBillById(1L)).thenReturn(BillResponse.builder().billCode("HD001").build());
        try(var lookup = mockStatic(PrintServiceLookup.class)) {
            lookup.when(() -> PrintServiceLookup.lookupPrintServices(null,null)).thenReturn(new PrintService[0]);
            assertThatCode(() -> service.printBill(1L)).doesNotThrowAnyException();
            verify(bills).getBillById(1L);
        }
    }
}
