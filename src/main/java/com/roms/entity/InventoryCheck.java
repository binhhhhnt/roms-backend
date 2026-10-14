package com.roms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_checks")
@Getter
@Setter
@Builder
public class InventoryCheck {

    public InventoryCheck() {
    }

    public InventoryCheck(Long id, User checkedBy, LocalDateTime checkedAt, String note,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.checkedBy = checkedBy;
        this.checkedAt = checkedAt;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by", nullable = false)
    private User checkedBy;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "note", length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
