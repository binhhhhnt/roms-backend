package com.roms.repository;

import com.roms.entity.ServiceSession;
import com.roms.enums.ServiceSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceSessionRepository extends JpaRepository<ServiceSession, Long> {
    Optional<ServiceSession> findByTableIdAndStatus(Long tableId, ServiceSessionStatus status);
    long countByStatus(ServiceSessionStatus status);
}
