package com.roms.entity;

import com.roms.enums.ServiceSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_sessions")
@Getter
@Setter
@Builder
public class ServiceSession {

    public ServiceSession() {
    }

    public ServiceSession(Long id, RestaurantTable table, ServiceSessionStatus status,
                          LocalDateTime openedAt, User openedBy, LocalDateTime closedAt, User closedBy) {
        this.id = id;
        this.table = table;
        this.status = status != null ? status : ServiceSessionStatus.ACTIVE;
        this.openedAt = openedAt;
        this.openedBy = openedBy;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ServiceSessionStatus status = ServiceSessionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;
}
