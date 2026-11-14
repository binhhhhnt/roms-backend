package com.roms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tables")
@Getter
@Setter
@Builder
public class RestaurantTable {

    public RestaurantTable() {
    }

    public RestaurantTable(Long id, String tableCode, Integer capacity, String area,
                           Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, tableCode, capacity, area, isActive, null, createdAt, updatedAt);
    }

    public RestaurantTable(Long id, String tableCode, Integer capacity, String area,
                           Boolean isActive, String qrToken, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.area = area;
        this.isActive = isActive != null ? isActive : true;
        this.qrToken = qrToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_code", nullable = false, unique = true, length = 20)
    private String tableCode;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "area", length = 100)
    private String area;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void ensureQrToken() {
        if (this.qrToken == null || this.qrToken.isBlank()) {
            this.qrToken = java.util.UUID.randomUUID().toString();
        }
    }
}
