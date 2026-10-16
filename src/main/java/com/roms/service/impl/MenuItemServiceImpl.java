package com.roms.service.impl;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.dto.response.MenuItemReadResponse;
import com.roms.dto.response.MenuItemImageResponse;
import com.roms.common.PageResponse;
import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.UserRole;
import com.roms.exception.UnauthorizedException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.ValidationException;
import com.roms.mapper.MenuItemMapper;
import com.roms.mapper.CategoryMapper;
import com.roms.mapper.MenuItemImageMapper;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.MenuItemImageRepository;
import com.roms.service.MenuItemService;
import com.roms.service.MenuAvailabilityService;
import com.roms.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
    private final MenuItemRepository repository;
    private final CategoryRepository categories;
    private final MenuItemMapper mapper;
    private final MenuContextInvalidator invalidator;
    private final MenuItemImageRepository images;
    private final MenuItemImageMapper imageMapper;
    private final CategoryMapper categoryMapper;
    private final MenuAvailabilityService availability;

    @Override @Transactional(readOnly = true)
    public PageResponse<MenuItemReadResponse> getMenuItems(Long categoryId, MenuItemStatus status, int quantity, Pageable pageable) {
        UserRole role = currentRole();
        requireQuantity(quantity);
        if (categoryId != null && categoryId <= 0) throw new ValidationException("Category id must be positive");
        Set<MenuItemStatus> statuses = visibleStatuses(role);
        if (status != null) {
            if (!statuses.contains(status)) throw new ForbiddenException("Role cannot view the requested menu status");
            statuses = EnumSet.of(status);
        }
        var page = repository.findCatalog(categoryId, statuses, role == UserRole.TABLE_ACCOUNT, normalizePageable(pageable));
        if (page.isEmpty()) return PageResponse.from(page, item -> readResponse(item, List.of(), quantity));
        List<Long> ids = page.getContent().stream().map(MenuItem::getId).toList();
        Map<Long, List<MenuItemImageResponse>> groupedImages = images.findCatalogImages(ids).stream()
                .collect(Collectors.groupingBy(image -> image.getMenuItem().getId(),
                        Collectors.mapping(imageMapper::toResponse, Collectors.toList())));
        return PageResponse.from(page, item -> readResponse(item, groupedImages.getOrDefault(item.getId(), List.of()), quantity));
    }

    @Override @Transactional(readOnly = true)
    public MenuItemReadResponse getMenuItem(Long id, int quantity) {
        UserRole role = currentRole();
        requireQuantity(quantity);
        if (id == null || id <= 0) throw new ValidationException("Menu item id must be positive");
        MenuItem item = repository.findCatalogById(id)
                .filter(value -> visibleStatuses(role).contains(value.getStatus()))
                .filter(value -> role != UserRole.TABLE_ACCOUNT || Boolean.TRUE.equals(value.getCategory().getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        List<MenuItemImageResponse> imageResponses = images.findCatalogImages(List.of(id)).stream().map(imageMapper::toResponse).toList();
        return readResponse(item, imageResponses, quantity);
    }

    private MenuItemReadResponse readResponse(MenuItem item, List<MenuItemImageResponse> imageResponses, int quantity) {
        return mapper.toReadResponse(item, categoryMapper.toResponse(item.getCategory()), imageResponses,
                availability.checkSellability(item.getId(), quantity));
    }
    private UserRole currentRole() {
        return SecurityUtils.getCurrentUserRole().orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
    private Set<MenuItemStatus> visibleStatuses(UserRole role) {
        return switch (role) {
            case OWNER, INVENTORY_STAFF -> EnumSet.allOf(MenuItemStatus.class);
            case KITCHEN_STAFF, SERVICE_STAFF -> EnumSet.of(MenuItemStatus.AVAILABLE, MenuItemStatus.TEMPORARILY_UNAVAILABLE);
            case TABLE_ACCOUNT -> EnumSet.of(MenuItemStatus.AVAILABLE);
        };
    }
    private void requireQuantity(int quantity) {
        if (quantity < 1) throw new ValidationException("Quantity must be positive");
    }
    private Pageable normalizePageable(Pageable pageable) {
        Set<String> allowed = Set.of("id", "name", "price", "status", "isFeatured");
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) throw new ValidationException("Unsupported menu sort field: " + order.getProperty());
        }
        if (sort.isUnsorted()) sort = Sort.by("name");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), sort);
    }

    @Override @Transactional
    public MenuItemResponse createMenuItem(MenuItemWriteRequest request) {
        CatalogAccess.requireOwner();
        MenuItem item = new MenuItem();
        item.setStatus(MenuItemStatus.AVAILABLE);
        return write(request, item);
    }

    @Override @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemWriteRequest request) {
        CatalogAccess.requireOwner();
        if (id == null || id <= 0) throw new ValidationException("Menu item id must be positive");
        MenuItem item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        return write(request, item);
    }

    private MenuItemResponse write(MenuItemWriteRequest request, MenuItem item) {
        item.setCategory(categories.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found")));
        mapper.updateEntity(request, item);
        MenuItem saved = repository.save(item);
        invalidator.afterCommit();
        return mapper.toResponse(saved);
    }
}
