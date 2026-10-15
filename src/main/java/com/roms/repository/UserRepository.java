package com.roms.repository;

import com.roms.entity.User;
import com.roms.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByTableId(Long tableId);

    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:isActive IS NULL OR u.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<User> findWithFilters(
            @Param("role") UserRole role,
            @Param("isActive") Boolean isActive,
            @Param("search") String search,
            Pageable pageable
    );
}
