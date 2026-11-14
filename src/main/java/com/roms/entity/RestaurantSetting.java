package com.roms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_settings")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestaurantSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_price_includes_vat", nullable = false)
    @Builder.Default
    private Boolean menuPriceIncludesVat = true;

    @Column(name = "qr_ordering_enabled", nullable = false)
    @Builder.Default
    private Boolean qrOrderingEnabled = true;

    @Column(name = "restaurant_name", length = 255)
    @Builder.Default
    private String restaurantName = "Nhà hàng ROMS";

    @Column(name = "address", length = 500)
    @Builder.Default
    private String address = "123 Đường Ẩm Thực, TP. Hồ Chí Minh";

    @Column(name = "phone", length = 30)
    @Builder.Default
    private String phone = "0901 234 567";

    @Column(name = "receipt_footer", length = 500)
    @Builder.Default
    private String receiptFooter = "Cảm ơn quý khách và hẹn gặp lại! Wifi: ROMS_VIP / Pass: 88888888";

    @Column(name = "print_ready_ticket", nullable = false)
    @Builder.Default
    private Boolean printReadyTicket = true;

    @Column(name = "bill_printer_name", length = 255)
    private String billPrinterName;

    @Column(name = "bill_paper_size", nullable = false, length = 20)
    @Builder.Default
    private String billPaperSize = "K80";

    @Column(name = "ready_ticket_printer_name", length = 255)
    private String readyTicketPrinterName;

    @Column(name = "ready_ticket_paper_size", nullable = false, length = 20)
    @Builder.Default
    private String readyTicketPaperSize = "K80";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
