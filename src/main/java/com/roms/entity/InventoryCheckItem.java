package com.roms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_check_items")
@Getter
@Setter
@Builder
public class InventoryCheckItem {

    public InventoryCheckItem() {
    }

    public InventoryCheckItem(Long id, InventoryCheck inventoryCheck, Ingredient ingredient,
                              BigDecimal systemQuantity, BigDecimal actualQuantity, BigDecimal difference,
                              String reason, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.inventoryCheck = inventoryCheck;
        this.ingredient = ingredient;
        this.systemQuantity = systemQuantity;
        this.actualQuantity = actualQuantity;
        this.difference = difference;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_check_id", nullable = false)
    private InventoryCheck inventoryCheck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "system_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "difference", nullable = false, precision = 12, scale = 3)
    private BigDecimal difference;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
