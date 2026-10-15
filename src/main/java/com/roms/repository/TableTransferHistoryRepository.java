package com.roms.repository;

import com.roms.entity.TableTransferHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TableTransferHistoryRepository extends JpaRepository<TableTransferHistory, Long> {

    @Query("SELECT h FROM TableTransferHistory h " +
           "JOIN FETCH h.fromTable " +
           "JOIN FETCH h.toTable " +
           "JOIN FETCH h.transferredBy " +
           "WHERE h.serviceSession.id = :sessionId")
    Page<TableTransferHistory> findByServiceSessionId(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );
}
