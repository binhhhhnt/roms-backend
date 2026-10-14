package com.roms.entity;

import com.roms.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
public class Order {

    public Order() {
    }

    public Order(Long id, ServiceSession serviceSession, User createdBy, OrderStatus status,
                 String terminationReason, User terminatedBy, LocalDateTime terminatedAt,
                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.serviceSession = serviceSession;
        this.createdBy = createdBy;
        this.status = status != null ? status : OrderStatus.PENDING;
        this.terminationReason = terminationReason;
        this.terminatedBy = terminatedBy;
        this.terminatedAt = terminatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_session_id", nullable = false)
    private ServiceSession serviceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "termination_reason", length = 255)
    private String terminationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminated_by")
    private User terminatedBy;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
