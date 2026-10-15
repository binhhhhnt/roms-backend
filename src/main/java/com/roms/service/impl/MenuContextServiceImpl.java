package com.roms.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.MenuContextCacheService;
import com.roms.service.MenuContextService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service triển khai nạp, trích xuất và đồng bộ ngữ cảnh thực đơn (Menu Context)
 * từ cơ sở dữ liệu PostgreSQL vào bộ nhớ đệm Redis phục vụ cho Trợ lý AI Gemini.
 *
 * Tuân thủ nghiêm ngặt nguyên tắc kiến trúc:
 * - Chỉ lưu các thông tin tương đối ổn định (ID món, tên món, danh mục, giá, mô tả, danh sách tên nguyên liệu).
 * - KHÔNG lưu trạng thái tồn kho (current_stock) hoặc trạng thái tạm ngừng (sellability) trong Redis cache.
 * - Khả năng bán (Sellability) luôn được kiểm tra trực tiếp từ PostgreSQL tại thời điểm gợi ý hoặc đặt món.
 */
@Slf4j
@Service
public class MenuContextServiceImpl implements MenuContextService {

    private final MenuContextCacheService menuContextCacheService;
    private final MenuItemRepository menuItemRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final ObjectMapper objectMapper;

    /**
     * Khởi tạo MenuContextServiceImpl với các thành phần phụ thuộc.
     *
     * @param menuContextCacheService Dịch vụ cache Redis cho menu context
     * @param menuItemRepository      Repository truy xuất món ăn
     * @param recipeItemRepository    Repository truy xuất công thức món
     * @param objectMapper            Bộ chuyển đổi đối tượng sang chuỗi JSON
     */
    public MenuContextServiceImpl(MenuContextCacheService menuContextCacheService,
                                  MenuItemRepository menuItemRepository,
                                  RecipeItemRepository recipeItemRepository,
                                  ObjectMapper objectMapper) {
        this.menuContextCacheService = menuContextCacheService;
        this.menuItemRepository = menuItemRepository;
        this.recipeItemRepository = recipeItemRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lấy chuỗi ngữ cảnh thực đơn từ Redis Cache.
     * Nếu không có trong cache hoặc Redis gặp sự cố, tự động truy vấn PostgreSQL để tạo mới,
     * lưu vào Redis và trả về kết quả.
     *
     * @return Chuỗi JSON ngữ cảnh thực đơn
     */
    @Override
    @Transactional(readOnly = true)
    public String getOrBuildContext() {
        Optional<String> cached = menuContextCacheService.getMenuContext();
        if (cached.isPresent() && !cached.get().trim().isEmpty()) {
            log.debug("Retrieved menu context from Redis cache");
            return cached.get();
        }

        log.info("Menu context cache miss. Building from PostgreSQL database...");
        return rebuildContext();
    }

    /**
     * Làm mới (xóa) bộ nhớ đệm ngữ cảnh thực đơn trong Redis.
     */
    @Override
    public void invalidateContext() {
        log.info("Invalidating menu context cache in Redis");
        menuContextCacheService.invalidateMenuContext();
    }

    /**
     * Buộc truy vấn cơ sở dữ liệu để tạo lại chuỗi ngữ cảnh thực đơn mới nhất,
     * sau đó lưu vào Redis Cache.
     *
     * @return Chuỗi JSON ngữ cảnh thực đơn mới
     */
    @Override
    @Transactional(readOnly = true)
    public String rebuildContext() {
        // 1. Lấy tất cả các món ăn đang được phép kinh doanh (loại trừ các món đã ngừng vĩnh viễn - DISCONTINUED)
        List<MenuItem> menuItems = menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED);

        if (menuItems.isEmpty()) {
            String emptyJson = "[]";
            menuContextCacheService.saveMenuContext(emptyJson);
            return emptyJson;
        }

        // 2. Thu thập danh sách ID món ăn để truy vấn công thức (tránh N+1 query)
        List<Long> itemIds = menuItems.stream().map(MenuItem::getId).toList();
        List<RecipeItem> recipeItems = recipeItemRepository.findByMenuItemIdInWithIngredient(itemIds);

        // 3. Gom nhóm tên các nguyên liệu theo ID món ăn
        Map<Long, List<String>> ingredientsMap = new HashMap<>();
        for (RecipeItem recipeItem : recipeItems) {
            if (recipeItem.getIngredient() != null && recipeItem.getIngredient().getName() != null) {
                ingredientsMap.computeIfAbsent(recipeItem.getMenuItem().getId(), k -> new ArrayList<>())
                        .add(recipeItem.getIngredient().getName());
            }
        }

        // 4. Ánh xạ các món ăn sang cấu trúc dữ liệu tối giản phục vụ cho AI
        List<MenuItemContextDto> contextItems = menuItems.stream().map(item -> {
            List<String> ingredients = ingredientsMap.getOrDefault(item.getId(), Collections.emptyList());
            return MenuItemContextDto.builder()
                    .id(item.getId())
                    .name(item.getName())
                    .category(item.getCategory() != null ? item.getCategory().getName() : "Khác")
                    .price(item.getPrice())
                    .description(item.getDescription())
                    .ingredients(ingredients)
                    .build();
        }).collect(Collectors.toList());

        // 5. Chuyển đổi sang JSON và lưu vào Redis Cache
        try {
            String jsonContext = objectMapper.writeValueAsString(contextItems);
            menuContextCacheService.saveMenuContext(jsonContext);
            log.info("Successfully built and cached menu context for {} items", contextItems.size());
            return jsonContext;
        } catch (Exception e) {
            log.error("Failed to serialize menu context to JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize menu context to JSON", e);
        }
    }

    /**
     * DTO nội bộ biểu diễn cấu trúc dữ liệu một món ăn trong ngữ cảnh gửi cho AI.
     */
    @Getter
    @Setter
    public static class MenuItemContextDto {
        private Long id;
        private String name;
        private String category;
        private BigDecimal price;
        private String description;
        private List<String> ingredients;

        public MenuItemContextDto() {
        }

        @Builder
        public MenuItemContextDto(Long id, String name, String category, BigDecimal price,
                                  String description, List<String> ingredients) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.description = description;
            this.ingredients = ingredients;
        }
    }
}
