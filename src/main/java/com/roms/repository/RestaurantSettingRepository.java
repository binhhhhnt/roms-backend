package com.roms.repository;

import com.roms.entity.RestaurantSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantSettingRepository extends JpaRepository<RestaurantSetting, Long> {

    Optional<RestaurantSetting> findFirstByOrderByIdAsc();
}

