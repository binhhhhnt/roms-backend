package com.roms.service.impl;

import com.roms.dto.request.CreateAreaRequest;
import com.roms.dto.request.UpdateAreaRequest;
import com.roms.dto.request.UpdateAreaStatusRequest;
import com.roms.dto.response.AreaResponse;
import com.roms.entity.Area;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.AreaRepository;
import com.roms.repository.RestaurantTableRepository;
import com.roms.service.AreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaServiceImpl implements AreaService {

    private final AreaRepository areaRepository;
    private final RestaurantTableRepository tableRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponse> getAreas(Boolean isActive) {
        List<Area> areas;
        if (isActive == null) {
            areas = areaRepository.findAllByOrderByNameAsc();
        } else if (Boolean.TRUE.equals(isActive)) {
            areas = areaRepository.findByIsActiveTrueOrderByNameAsc();
        } else {
            areas = areaRepository.findByIsActiveOrderByNameAsc(false);
        }

        // Single GROUP BY query to avoid N+1 when computing activeTableCount per area.
        Map<Long, Long> activeCounts = tableRepository.countActiveTablesGroupByArea().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return areas.stream()
                .map(area -> toResponse(area, activeCounts.getOrDefault(area.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public AreaResponse createArea(CreateAreaRequest request) {
        String name = request.getName().trim();
        if (areaRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Area name already exists: " + name, "DUPLICATE_AREA_NAME");
        }

        Area area = Area.builder()
                .name(name)
                .isActive(true)
                .build();

        try {
            Area saved = areaRepository.saveAndFlush(area);
            log.info("Created area id={}, name={}", saved.getId(), saved.getName());
            return toResponse(saved, 0L);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Area name already exists: " + name, "DUPLICATE_AREA_NAME");
        }
    }

    @Override
    @Transactional
    public AreaResponse updateArea(Long id, UpdateAreaRequest request) {
        Area area = areaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found with id: " + id));

        String name = request.getName().trim();
        if (areaRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessRuleException("Area name already exists: " + name, "DUPLICATE_AREA_NAME");
        }

        area.setName(name);
        try {
            Area saved = areaRepository.saveAndFlush(area);
            long activeCount = tableRepository.countByAreaIdAndIsActiveTrue(id);
            log.info("Updated area id={}, name={}", saved.getId(), saved.getName());
            return toResponse(saved, activeCount);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Area name already exists: " + name, "DUPLICATE_AREA_NAME");
        }
    }

    @Override
    @Transactional
    public AreaResponse updateAreaStatus(Long id, UpdateAreaStatusRequest request) {
        // Lock Area before checking active tables to prevent concurrent table assignment race condition.
        Area area = areaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found with id: " + id));

        long activeCount = tableRepository.countByAreaIdAndIsActiveTrue(id);
        if (Boolean.FALSE.equals(request.getIsActive()) && activeCount > 0) {
            throw new BusinessRuleException(
                    "Cannot deactivate area " + area.getName() + " while it still has " + activeCount + " active table(s)",
                    "AREA_HAS_ACTIVE_TABLES"
            );
        }

        area.setIsActive(request.getIsActive());
        Area saved = areaRepository.save(area);
        log.info("Updated area status id={}, isActive={}", saved.getId(), saved.getIsActive());
        return toResponse(saved, activeCount);
    }

    private AreaResponse toResponse(Area area, long activeTableCount) {
        return AreaResponse.builder()
                .id(area.getId())
                .name(area.getName())
                .isActive(area.getIsActive())
                .activeTableCount(activeTableCount)
                .build();
    }
}
