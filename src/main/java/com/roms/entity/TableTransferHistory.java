package com.roms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "table_transfer_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableTransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_session_id", nullable = false)
    private ServiceSession serviceSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_table_id", nullable = false)
    private RestaurantTable fromTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_table_id", nullable = false)
    private RestaurantTable toTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_by", nullable = false)
    private User transferredBy;

    @CreationTimestamp
    @Column(name = "transferred_at", nullable = false, updatable = false)
    private LocalDateTime transferredAt;
}
