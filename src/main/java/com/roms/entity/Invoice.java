package com.roms.entity;

import com.roms.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Invoice() {
    }

    @Builder
    public Invoice(Long id, String invoiceCode, ServiceSession serviceSession, BigDecimal subtotal,
                   DiscountType discountType, BigDecimal discountValue, BigDecimal discountAmount,
                   String discountReason, BigDecimal finalAmount, String note, LocalDateTime createdAt) {
        this.id = id;
        this.invoiceCode = invoiceCode;
        this.serviceSession = serviceSession;
        this.subtotal = subtotal;
        this.discountType = discountType != null ? discountType : DiscountType.NONE;
        this.discountValue = discountValue != null ? discountValue : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.discountReason = discountReason;
        this.finalAmount = finalAmount;
        this.note = note;
        this.createdAt = createdAt;
    }

    @Column(name = "invoice_code", nullable = false, unique = true, length = 50)
    private String invoiceCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_session_id", nullable = false, unique = true)
    private ServiceSession serviceSession;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.NONE;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "note", length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

