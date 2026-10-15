package com.roms.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.roms.dto.request.AiRecommendationRequest;
import com.roms.dto.response.AiRecommendationItem;
import com.roms.dto.response.AiRecommendationResponse;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.integration.GeminiClientAdapter;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.AiAssistantService;
import com.roms.service.MenuContextService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service triển khai nghiệp vụ Trợ lý AI Gemini gợi ý món ăn.
 * 
 * Các quy tắc cốt lõi:
 * 1. Sử dụng ngữ cảnh thực đơn được lưu trong Redis (thông qua MenuContextService).
 * 2. Xác thực ID món ăn: Chỉ chấp nhận các món ăn thực sự tồn tại trong PostgreSQL và chưa bị ngừng kinh doanh (DISCONTINUED).
 * 3. Kiểm tra khả năng bán thực tế (Live Recipe/Stock Sellability):
 *    - Món ăn phải có trạng thái AVAILABLE (không phải TEMPORARILY_UNAVAILABLE).
 *    - Toàn bộ nguyên liệu theo công thức (recipe_items) phải đang hoạt động (isActive = true) và có tồn kho hiện tại
 *      (current_stock) lớn hơn hoặc bằng định lượng yêu cầu (quantity_required).
 * 4. Cơ chế dự phòng an toàn (Graceful Fallback): Nếu dịch vụ AI chưa cấu hình API key hoặc gặp sự cố mạng,
 *    hệ thống tự động đề xuất các món ăn nổi bật (featured items) đang sẵn sàng phục vụ để khách hàng không bị gián đoạn.
 */
@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final GeminiClientAdapter geminiClientAdapter;
    private final MenuContextService menuContextService;
    private final MenuItemRepository menuItemRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final ObjectMapper objectMapper;

    public AiAssistantServiceImpl(GeminiClientAdapter geminiClientAdapter,
                                  MenuContextService menuContextService,
                                  MenuItemRepository menuItemRepository,
                                  RecipeItemRepository recipeItemRepository,
                                  ObjectMapper objectMapper) {
        this.geminiClientAdapter = geminiClientAdapter;
        this.menuContextService = menuContextService;
        this.menuItemRepository = menuItemRepository;
        this.recipeItemRepository = recipeItemRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Gợi ý món ăn thông minh theo yêu cầu của khách hàng.
     *
     * @param request DTO chứa sở thích, dị ứng, số lượng người, ngân sách
     * @return AiRecommendationResponse Danh sách món ăn phù hợp và còn hàng kèm lời giải thích
     */
    @Override
    @Transactional(readOnly = true)
    public AiRecommendationResponse getRecommendations(AiRecommendationRequest request) {
        // 1. Kiểm tra nếu Gemini client chưa được cấu hình API key, kích hoạt fallback ngay
        if (!geminiClientAdapter.isConfigured()) {
            log.warn("Gemini API key is not configured. Falling back to featured available menu items.");
            return createFallbackResponse("Hiện tại dịch vụ AI đang bảo trì. Nhà hàng xin gợi ý một số món ăn đặc sắc nhất đang sẵn sàng phục vụ quý khách:");
        }

        try {
            // 2. Lấy ngữ cảnh thực đơn (Menu Context) từ Redis Cache (hoặc tự build từ DB)
            String menuContextJson = menuContextService.getOrBuildContext();

            // 3. Chuẩn bị System Instruction và User Prompt
            String systemInstruction = buildSystemInstruction();
            String userPrompt = buildUserPrompt(request, menuContextJson);

            // 4. Gọi Gemini AI để sinh gợi ý
            log.info("Sending recommendation request to Gemini AI...");
            String rawAiResponse = geminiClientAdapter.generateText(systemInstruction, userPrompt);
            log.debug("Received raw response from Gemini: {}", rawAiResponse);

            // 5. Trích xuất và phân tích cú pháp JSON phản hồi từ Gemini
            AiRawResponse parsedResponse = parseAiResponse(rawAiResponse);
            if (parsedResponse == null || parsedResponse.getRecommendations() == null || parsedResponse.getRecommendations().isEmpty()) {
                log.warn("Gemini returned empty or unparseable recommendations. Falling back to featured items.");
                return createFallbackResponse("Nhà hàng xin gợi ý các món ăn nổi bật nhất hiện có phù hợp cho quý khách:");
            }

            // 6. RÀNG BUỘC AN TOÀN SỐ 1: Xác thực ID món ăn (ID Validation)
            Map<Long, String> aiReasonsMap = new LinkedHashMap<>();
            for (AiRawItem item : parsedResponse.getRecommendations()) {
                if (item.getMenuItemId() != null && item.getMenuItemId() > 0) {
                    aiReasonsMap.put(item.getMenuItemId(), item.getReason() != null ? item.getReason().trim() : "");
                }
            }

            if (aiReasonsMap.isEmpty()) {
                return createFallbackResponse("Nhà hàng xin gợi ý các món ăn nổi bật đang sẵn sàng phục vụ:");
            }

            // Truy vấn các món ăn hợp lệ từ cơ sở dữ liệu
            List<MenuItem> existingMenuItems = menuItemRepository.findByIdInWithCategory(aiReasonsMap.keySet());
            Map<Long, MenuItem> menuItemMap = existingMenuItems.stream()
                    .filter(item -> item.getStatus() != MenuItemStatus.DISCONTINUED)
                    .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

            // 7. RÀNG BUỘC AN TOÀN SỐ 2: Kiểm tra khả năng bán thực tế (Live Recipe/Stock Sellability Check)
            List<AiRecommendationItem> finalRecommendations = new ArrayList<>();

            for (Map.Entry<Long, String> entry : aiReasonsMap.entrySet()) {
                Long menuItemId = entry.getKey();
                String reason = entry.getValue();

                MenuItem menuItem = menuItemMap.get(menuItemId);
                if (menuItem == null) {
                    log.warn("Gemini recommended non-existing or discontinued menuItemId: {}. Discarding.", menuItemId);
                    continue;
                }

                // Kiểm tra xem món ăn có thực sự bán được trong kho không
                boolean isSellable = checkLiveSellability(menuItem);
                if (!isSellable) {
                    log.info("MenuItem id: {} ('{}') is out of stock or unavailable. Discarding from recommendations.",
                            menuItem.getId(), menuItem.getName());
                    continue;
                }

                // Bổ sung món ăn hợp lệ vào danh sách trả về
                finalRecommendations.add(AiRecommendationItem.builder()
                        .menuItemId(menuItem.getId())
                        .name(menuItem.getName())
                        .price(menuItem.getPrice())
                        .categoryName(menuItem.getCategory() != null ? menuItem.getCategory().getName() : "Món ăn")
                        .description(menuItem.getDescription())
                        .reason(!reason.isEmpty() ? reason : "Món ăn thơm ngon, phù hợp với sở thích của quý khách.")
                        .isSellable(true)
                        .build());
            }

            // Nếu sau khi kiểm tra tồn kho không còn món nào khả dụng, trả về fallback
            if (finalRecommendations.isEmpty()) {
                log.warn("All AI recommended items are currently out of stock. Returning fallback featured items.");
                return createFallbackResponse("Các món được đề xuất ban đầu hiện đã tạm hết nguyên liệu tươi ngon nhất. Nhà hàng xin gợi ý các món ăn sẵn sàng phục vụ sau:");
            }

            String aiMessage = (parsedResponse.getMessage() != null && !parsedResponse.getMessage().trim().isEmpty())
                    ? parsedResponse.getMessage().trim()
                    : "Dưới đây là các món ăn được lựa chọn riêng theo khẩu vị và yêu cầu của quý khách:";

            return AiRecommendationResponse.builder()
                    .message(aiMessage)
                    .recommendations(finalRecommendations)
                    .build();

        } catch (Exception e) {
            log.error("Error occurred while generating recommendations with Gemini: {}", e.getMessage(), e);
            return createFallbackResponse("Nhà hàng xin trân trọng gợi ý các món ăn nổi bật đang sẵn sàng phục vụ hôm nay:");
        }
    }

    /**
     * Kiểm tra khả năng bán thực tế của món ăn dựa trên trạng thái và tồn kho nguyên liệu công thức.
     * 
     * Quy tắc:
     * - Trạng thái món phải là AVAILABLE.
     * - Toàn bộ nguyên liệu theo công thức phải isActive = true và currentStock >= quantityRequired.
     *
     * @param menuItem Thực thể món ăn cần kiểm tra
     * @return true nếu món sẵn sàng phục vụ, false nếu hết hàng hoặc tạm ngừng
     */
    private boolean checkLiveSellability(MenuItem menuItem) {
        if (menuItem.getStatus() != MenuItemStatus.AVAILABLE) {
            return false;
        }

        // Lấy danh sách nguyên liệu theo công thức
        List<RecipeItem> recipeItems = recipeItemRepository.findByMenuItemIdWithIngredient(menuItem.getId());
        if (recipeItems.isEmpty()) {
            // Món ăn không có công thức (ví dụ: đồ uống đóng lon) -> Khả dụng nếu status AVAILABLE
            return true;
        }

        for (RecipeItem recipeItem : recipeItems) {
            if (recipeItem.getIngredient() == null) {
                return false;
            }
            // Nguyên liệu phải đang kích hoạt
            if (!Boolean.TRUE.equals(recipeItem.getIngredient().getIsActive())) {
                return false;
            }
            // Tồn kho nguyên liệu phải đủ cho định lượng yêu cầu
            if (recipeItem.getIngredient().getCurrentStock() == null ||
                    recipeItem.getIngredient().getCurrentStock().compareTo(recipeItem.getQuantityRequired()) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Xây dựng chỉ thị hệ thống (System Instruction) cho Gemini.
     */
    private String buildSystemInstruction() {
        return "Bạn là Trợ lý Ẩm thực thông minh của nhà hàng ROMS (Restaurant Operations Management System).\n" +
                "Nhiệm vụ của bạn là tư vấn và gợi ý các món ăn ngon, hài hòa nhất cho khách hàng dựa trên thực đơn của nhà hàng.\n\n" +
                "QUY TẮC BẮT BUỘC:\n" +
                "1. BẠN CHỈ ĐƯỢC GỢI Ý CÁC MÓN ĂN CÓ TRONG DANH SÁCH THỰC ĐƠN ĐƯỢC CUNG CẤP. TUYỆT ĐỐI KHÔNG TỰ BỊA RA TÊN MÓN HOẶC ID KHÔNG CÓ TRONG THỰC ĐƠN.\n" +
                "2. Chú ý các thông tin dị ứng/kiêng kị của khách hàng để loại bỏ các món chứa nguyên liệu đó.\n" +
                "3. Kết quả trả về BẮT BUỘC PHẢI LÀ MỘT ĐỐI TƯỢNG JSON HỢP LỆ VỚI CẤU TRÚC SAU (KHÔNG KÈM THEO BẤT KỲ VĂN BẢN NGOÀI NÀO):\n" +
                "{\n" +
                "  \"message\": \"Lời nhắn chào và giải thích khái quát sự kết hợp giữa các món ăn\",\n" +
                "  \"recommendations\": [\n" +
                "    {\n" +
                "      \"menuItemId\": 1,\n" +
                "      \"reason\": \"Giải thích ngắn gọn, cuốn hút tại sao món này phù hợp\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    /**
     * Xây dựng User Prompt kết hợp thông tin khách hàng và dữ liệu Menu Context.
     */
    private String buildUserPrompt(AiRecommendationRequest request, String menuContextJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("DANH SÁCH THỰC ĐƠN NHÀ HÀNG (JSON):\n");
        sb.append(menuContextJson).append("\n\n");
        sb.append("THÔNG TIN YÊU CẦU CỦA KHÁCH HÀNG:\n");

        if (request.getPreferences() != null && !request.getPreferences().trim().isEmpty()) {
            sb.append("- Khẩu vị/Sở thích: ").append(request.getPreferences().trim()).append("\n");
        }
        if (request.getPartySize() != null && request.getPartySize() > 0) {
            sb.append("- Số lượng người: ").append(request.getPartySize()).append(" người\n");
        }
        if (request.getAllergies() != null && !request.getAllergies().trim().isEmpty()) {
            sb.append("- Dị ứng / Kiêng cử: ").append(request.getAllergies().trim()).append("\n");
        }
        if (request.getBudgetRange() != null && !request.getBudgetRange().trim().isEmpty()) {
            sb.append("- Ngân sách dự kiến: ").append(request.getBudgetRange().trim()).append("\n");
        }
        if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
            sb.append("- Yêu cầu thêm: ").append(request.getMessage().trim()).append("\n");
        }

        sb.append("\nHãy chọn lọc và gợi ý từ 2 đến 5 món ăn phù hợp nhất. Trả về đúng định dạng JSON đã quy định.");
        return sb.toString();
    }

    /**
     * Trích xuất và phân tích cú pháp JSON từ phản hồi thô của Gemini.
     */
    private AiRawResponse parseAiResponse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return null;
        }

        String cleaned = rawText.trim();
        // Xóa các block markdown ```json ... ``` nếu có
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            return objectMapper.readValue(cleaned, AiRawResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON response: {}. Error: {}", cleaned, e.getMessage());
            return null;
        }
    }

    /**
     * Tạo phản hồi dự phòng lấy các món ăn nổi bật (Featured Items) đang có sẵn.
     */
    private AiRecommendationResponse createFallbackResponse(String defaultMessage) {
        List<MenuItem> featuredItems = menuItemRepository.findFeaturedAvailableWithCategory();
        List<AiRecommendationItem> items = new ArrayList<>();

        for (MenuItem item : featuredItems) {
            if (checkLiveSellability(item)) {
                items.add(AiRecommendationItem.builder()
                        .menuItemId(item.getId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "Món đặc sắc")
                        .description(item.getDescription())
                        .reason("Món ăn đặc sản nổi bật được thực khách yêu thích nhất tại nhà hàng.")
                        .isSellable(true)
                        .build());
            }
        }

        return AiRecommendationResponse.builder()
                .message(defaultMessage)
                .recommendations(items)
                .build();
    }

    /**
     * Lớp DTO nội bộ ánh xạ phản hồi JSON thô từ Gemini.
     */
    @Getter
    @Setter
    public static class AiRawResponse {
        private String message;
        private List<AiRawItem> recommendations;

        public AiRawResponse() {
        }

        public AiRawResponse(String message, List<AiRawItem> recommendations) {
            this.message = message;
            this.recommendations = recommendations;
        }
    }

    /**
     * Lớp DTO nội bộ ánh xạ một món ăn thô từ Gemini.
     */
    @Getter
    @Setter
    public static class AiRawItem {
        private Long menuItemId;
        private String reason;

        public AiRawItem() {
        }

        public AiRawItem(Long menuItemId, String reason) {
            this.menuItemId = menuItemId;
            this.reason = reason;
        }
    }
}
