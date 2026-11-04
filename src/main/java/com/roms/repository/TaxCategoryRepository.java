package com.roms.repository;

import com.roms.entity.TaxCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCategoryRepository extends JpaRepository<TaxCategory, Long> {

    Optional<TaxCategory> findByRate(BigDecimal rate);

    boolean existsByRate(BigDecimal rate);

    List<TaxCategory> findAllByOrderByRateAsc();
}

