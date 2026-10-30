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

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service triển khai nghiệp vụ Trợ lý AI Gemini gợi ý món ăn và trò chuyện với khách hàng.
 * 
 * Các quy tắc cốt lõi:
 * 1. Trả lời chuẩn xác các câu hỏi đề ra; chỉ đề xuất món ăn khi khách thực sự có nhu cầu.
 * 2. Khi khách hỏi món theo nguyên liệu: Tra cứu công thức (recipe_items/ingredients) của món đó.
 *    Chỉ gợi ý các món có nguyên liệu trùng khớp và nêu rõ nguyên liệu trong lý do.
 * 3. Nếu khách hỏi nguyên liệu hoặc món mà nhà hàng không có: Báo rõ ràng là không có, tuyệt đối không đề xuất linh tinh.
 * 4. Khả năng bán thực tế (Live Recipe/Stock Sellability): Món phải có trạng thái AVAILABLE
 *    và toàn bộ nguyên liệu công thức phải đủ tồn kho.
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
     * Gợi ý món ăn thông minh theo yêu cầu của khách hàng hoặc giải đáp thông tin.
     *
     * @param request DTO chứa sở thích, dị ứng, số lượng người, ngân sách, câu hỏi
     * @return AiRecommendationResponse Danh sách món ăn phù hợp và còn hàng kèm lời giải thích
     */
    @Override
    @Transactional(readOnly = true)
    public AiRecommendationResponse getRecommendations(AiRecommendationRequest request) {
        AiRecommendationRequest safeReq = request != null ? request : new AiRecommendationRequest();

        // 1. Thử gọi Gemini AI nếu adapter đã cấu hình
        if (geminiClientAdapter.isConfigured()) {
            try {
                String menuContextJson = menuContextService.getOrBuildContext();
                String systemInstruction = buildSystemInstruction();
                String userPrompt = buildUserPrompt(safeReq, menuContextJson);

                log.info("Sending recommendation request to Gemini AI...");
                String rawAiResponse = geminiClientAdapter.generateText(systemInstruction, userPrompt);
                log.debug("Received raw response from Gemini: {}", rawAiResponse);

                AiRawResponse parsedResponse = parseAiResponse(rawAiResponse);
                if (parsedResponse != null) {
                    List<AiRecommendationItem> finalRecommendations = new ArrayList<>();
                    if (parsedResponse.getRecommendations() != null && !parsedResponse.getRecommendations().isEmpty()) {
                        Map<Long, String> aiReasonsMap = new LinkedHashMap<>();
                        for (AiRawItem item : parsedResponse.getRecommendations()) {
                            if (item.getMenuItemId() != null && item.getMenuItemId() > 0) {
                                aiReasonsMap.put(item.getMenuItemId(), item.getReason() != null ? item.getReason().trim() : "");
                            }
                        }

                        if (!aiReasonsMap.isEmpty()) {
                            List<MenuItem> existingMenuItems = menuItemRepository.findByIdInWithCategory(aiReasonsMap.keySet());
                            Map<Long, MenuItem> menuItemMap = existingMenuItems.stream()
                                    .filter(item -> item.getStatus() != MenuItemStatus.DISCONTINUED)
                                    .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

                            for (Map.Entry<Long, String> entry : aiReasonsMap.entrySet()) {
                                Long menuItemId = entry.getKey();
                                String reason = entry.getValue();
                                MenuItem menuItem = menuItemMap.get(menuItemId);
                                if (menuItem != null && checkLiveSellability(menuItem)) {
                                    finalRecommendations.add(AiRecommendationItem.builder()
                                            .menuItemId(menuItem.getId())
                                            .name(menuItem.getName())
                                            .price(menuItem.getPrice())
                                            .categoryName(menuItem.getCategory() != null ? menuItem.getCategory().getName() : "Món ăn")
                                            .description(menuItem.getDescription())
                                            .reason(!reason.isEmpty() ? reason : generateDishReason(menuItem, normalize(safeReq.getEffectiveMessage()), null))
                                            .isSellable(true)
                                            .build());
                                }
                            }
                        }
                    }

                    // Phản hồi từ Gemini: Nếu Gemini trả lời thành công (kể cả khi không có món do hỏi thông tin chung hoặc món không có)
                    String aiMessage = (parsedResponse.getMessage() != null && !parsedResponse.getMessage().trim().isEmpty())
                            ? parsedResponse.getMessage().trim()
                            : (finalRecommendations.isEmpty()
                                    ? "Dạ, em có thể giúp gì thêm cho quý khách ạ?"
                                    : "Dưới đây là các món ăn được lựa chọn riêng theo khẩu vị và yêu cầu của quý khách:");

                    return AiRecommendationResponse.builder()
                            .message(aiMessage)
                            .recommendations(finalRecommendations)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed or timed out ({}). Activating smart database-driven menu recommender.", e.getMessage());
            }
        }

        // 2. Kích hoạt Động cơ Phân tích Thực đơn Thông minh (Truy vấn trực tiếp từ menu_items & recipe_items)
        return recommendFromMenuItems(safeReq);
    }

    /**
     * Phân tích câu hỏi của khách hàng, lọc và xếp hạng các món ăn trong menu_items
     * dựa trên công thức nguyên liệu và ngữ cảnh câu hỏi.
     */
    private AiRecommendationResponse recommendFromMenuItems(AiRecommendationRequest request) {
        // Lấy toàn bộ món ăn đang kinh doanh kèm Category
        List<MenuItem> allActiveItems = menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED);

        // Lọc các món còn hàng và đủ nguyên liệu chế biến (Live Sellability)
        List<MenuItem> availableItems = allActiveItems.stream()
                .filter(this::checkLiveSellability)
                .toList();

        if (availableItems.isEmpty()) {
            return AiRecommendationResponse.builder()
                    .message("Hiện tại các món ăn đang tạm thời cập nhật tồn kho. Quý khách vui lòng gọi nhân viên để được hỗ trợ trực tiếp.")
                    .recommendations(Collections.emptyList())
                    .build();
        }

        // Chuẩn hóa chuỗi truy vấn và các tiêu chí
        String effectiveMsg = request.getEffectiveMessage() != null ? request.getEffectiveMessage().trim() : "";
        String rawQuery = String.join(" ",
                effectiveMsg,
                request.getPreferences() != null && !request.getPreferences().trim().equalsIgnoreCase(effectiveMsg)
                        ? request.getPreferences()
                        : ""
        ).trim();
        String queryNorm = normalize(rawQuery);
        String allergyNorm = normalize(request.getAllergies());
        int partySize = request.getPartySize() != null && request.getPartySize() > 0 ? request.getPartySize() : 0;

        // 1. Kiểm tra câu hỏi chào hỏi / giao tiếp thông thường -> Trả lời lịch sự, KHÔNG gửi món ăn
        if (isGreetingOrChitChat(queryNorm)) {
            return AiRecommendationResponse.builder()
                    .message("Xin chào quý khách! Em là Trợ lý Ẩm thực của nhà hàng ROMS. Quý khách cần em tư vấn món ăn ngon, tìm món theo nguyên liệu hay giải đáp thông tin gì về nhà hàng ạ?")
                    .recommendations(Collections.emptyList())
                    .build();
        }

        // 2. Kiểm tra câu hỏi thông tin nhà hàng (giờ mở cửa, wifi, bãi đỗ xe, địa chỉ...) -> Trả lời đúng trọng tâm, KHÔNG gửi món ăn
        if (isRestaurantInfoQuery(queryNorm)) {
            return AiRecommendationResponse.builder()
                    .message(getRestaurantInfoReply(queryNorm))
                    .recommendations(Collections.emptyList())
                    .build();
        }

        // Lấy danh sách nguyên liệu công thức cho từng món ăn
        List<Long> itemIds = availableItems.stream().map(MenuItem::getId).toList();
        List<RecipeItem> recipeItems = recipeItemRepository.findByMenuItemIdInWithIngredient(itemIds);
        Map<Long, List<String>> ingredientsMap = new HashMap<>();
        for (RecipeItem recipeItem : recipeItems) {
            if (recipeItem.getIngredient() != null && recipeItem.getIngredient().getName() != null) {
                ingredientsMap.computeIfAbsent(recipeItem.getMenuItem().getId(), k -> new ArrayList<>())
                        .add(recipeItem.getIngredient().getName());
            }
        }
        for (MenuItem item : availableItems) {
            List<String> list = ingredientsMap.computeIfAbsent(item.getId(), k -> new ArrayList<>());
            if (list.isEmpty()) {
                list.addAll(inferIngredientsFromNameAndDesc(item.getName(), item.getDescription()));
            }
        }

        // 3. Lọc loại trừ dị ứng
        List<MenuItem> nonAllergicItems = availableItems.stream()
                .filter(item -> !containsAllergy(item, allergyNorm, ingredientsMap.getOrDefault(item.getId(), Collections.emptyList())))
                .toList();

        if (nonAllergicItems.isEmpty()) {
            nonAllergicItems = availableItems;
        }

        // 4. Kiểm tra xem khách có đang hỏi tìm món theo NGUYÊN LIỆU hoặc MÓN CỤ THỂ không
        boolean isSpecificSearch = isSpecificIngredientOrDishQuery(queryNorm);
        if (isSpecificSearch) {
            Map<MenuItem, String> matchedItemsWithIngredient = new LinkedHashMap<>();
            for (MenuItem item : nonAllergicItems) {
                String matchedIng = findMatchingIngredient(item, ingredientsMap.getOrDefault(item.getId(), Collections.emptyList()), queryNorm);
                if (matchedIng != null) {
                    matchedItemsWithIngredient.put(item, matchedIng);
                }
            }

            // Nếu khách tìm nguyên liệu/món cụ thể mà KHÔNG CÓ món nào trùng khớp:
            if (matchedItemsWithIngredient.isEmpty()) {
                return AiRecommendationResponse.builder()
                        .message("Dạ rất tiếc, hiện tại thực đơn nhà hàng chưa có món ăn nào chế biến từ nguyên liệu này theo yêu cầu của quý khách. Quý khách vui lòng tham khảo các món ăn khác trên thực đơn hoặc hỏi em để được tư vấn thêm ạ!")
                        .recommendations(Collections.emptyList())
                        .build();
            }

            // Nếu CÓ món trùng khớp nguyên liệu: Chỉ gợi ý đúng các món đó kèm lý do rõ ràng
            List<AiRecommendationItem> recommendations = new ArrayList<>();
            for (Map.Entry<MenuItem, String> entry : matchedItemsWithIngredient.entrySet()) {
                if (recommendations.size() >= 4) break;
                MenuItem item = entry.getKey();
                String matchedIng = entry.getValue();
                recommendations.add(AiRecommendationItem.builder()
                        .menuItemId(item.getId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "Món ăn")
                        .description(item.getDescription())
                        .reason(generateDishReason(item, queryNorm, matchedIng))
                        .isSellable(true)
                        .build());
            }

            String firstMatchedIng = matchedItemsWithIngredient.values().iterator().next();
            String intro = String.format("Dựa trên công thức chế biến của nhà hàng, dưới đây là các món ăn có thành phần %s đúng theo yêu cầu của quý khách:", firstMatchedIng);

            return AiRecommendationResponse.builder()
                    .message(intro)
                    .recommendations(recommendations)
                    .build();
        }

        // 5. Yêu cầu gợi ý thực đơn / combo theo khẩu vị chung
        boolean wantsVegetarian = queryNorm.contains("chay") || queryNorm.contains("thanh dam") || queryNorm.contains("it dau");
        boolean wantsNoSpicy = queryNorm.contains("khong cay") || queryNorm.contains("it cay") || allergyNorm.contains("cay") || allergyNorm.contains("ot");
        boolean wantsSpicy = (queryNorm.contains("cay") || queryNorm.contains("dam da") || queryNorm.contains("nhau")) && !wantsNoSpicy;
        boolean wantsSeafood = queryNorm.contains("hai san") || queryNorm.contains("tom") || queryNorm.contains("cua") || queryNorm.contains("muc") || queryNorm.contains("ca");
        boolean wantsDrinks = queryNorm.contains("do uong") || queryNorm.contains("nuoc") || queryNorm.contains("tra") || queryNorm.contains("trang mieng") || queryNorm.contains("ca phe");
        boolean wantsFamilyCombo = queryNorm.contains("gia dinh") || queryNorm.contains("combo") || queryNorm.contains("nhom") || partySize >= 3;

        Map<MenuItem, Integer> scoredItems = new HashMap<>();
        for (MenuItem item : nonAllergicItems) {
            int score = calculateScore(item, queryNorm, wantsVegetarian, wantsNoSpicy, wantsSpicy, wantsSeafood, wantsDrinks, wantsFamilyCombo);
            scoredItems.put(item, score);
        }

        List<MenuItem> sortedItems = nonAllergicItems.stream()
                .sorted((a, b) -> Integer.compare(scoredItems.getOrDefault(b, 0), scoredItems.getOrDefault(a, 0)))
                .toList();

        List<MenuItem> selectedItems = new ArrayList<>();
        if (wantsFamilyCombo && sortedItems.size() >= 3) {
            sortedItems.stream()
                    .filter(m -> m.getCategory() != null && normalize(m.getCategory().getName()).contains("khai vi"))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            sortedItems.stream()
                    .filter(m -> m.getCategory() != null && normalize(m.getCategory().getName()).contains("chinh") && !selectedItems.contains(m))
                    .limit(partySize >= 4 ? 2 : 1)
                    .forEach(selectedItems::add);

            sortedItems.stream()
                    .filter(m -> !selectedItems.contains(m) && m.getCategory() != null && (normalize(m.getCategory().getName()).contains("kem") || normalize(m.getName()).contains("khoai")))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            sortedItems.stream()
                    .filter(m -> !selectedItems.contains(m) && m.getCategory() != null && normalize(m.getCategory().getName()).contains("do uong"))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            for (MenuItem item : sortedItems) {
                if (selectedItems.size() >= (partySize >= 4 ? 4 : 3)) break;
                if (!selectedItems.contains(item)) {
                    selectedItems.add(item);
                }
            }
        } else {
            int targetCount = Math.min(3, Math.max(1, sortedItems.size()));
            sortedItems.stream().limit(targetCount).forEach(selectedItems::add);
        }

        String introMessage = generateIntroMessage(queryNorm, partySize, wantsVegetarian, wantsNoSpicy, wantsSpicy, wantsSeafood, wantsDrinks, wantsFamilyCombo);

        List<AiRecommendationItem> recommendations = selectedItems.stream()
                .map(item -> AiRecommendationItem.builder()
                        .menuItemId(item.getId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "Món ăn")
                        .description(item.getDescription())
                        .reason(generateDishReason(item, queryNorm, null))
                        .isSellable(true)
                        .build())
                .toList();

        return AiRecommendationResponse.builder()
                .message(introMessage)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Nhận diện xem câu hỏi có phải là lời chào hỏi hoặc giao tiếp xã giao hay không.
     */
    private boolean isGreetingOrChitChat(String queryNorm) {
        if (queryNorm == null || queryNorm.isBlank()) return true;
        return queryNorm.equals("chao") || queryNorm.startsWith("chao ") || queryNorm.endsWith(" chao") || queryNorm.contains("xin chao")
                || queryNorm.equals("hello") || queryNorm.equals("hi") || queryNorm.contains("cam on") || queryNorm.contains("thank")
                || queryNorm.equals("alo") || queryNorm.equals("tro ly") || queryNorm.equals("bot");
    }

    /**
     * Nhận diện câu hỏi liên quan đến thông tin dịch vụ nhà hàng (giờ mở cửa, wifi, bãi đỗ xe...).
     */
    private boolean isRestaurantInfoQuery(String queryNorm) {
        if (queryNorm == null) return false;
        return queryNorm.contains("mo cua") || queryNorm.contains("dong cua") || queryNorm.contains("may gio")
                || queryNorm.contains("gio mo") || queryNorm.contains("gio hoat dong")
                || queryNorm.contains("wifi") || queryNorm.contains("mat khau") || queryNorm.contains("pass")
                || queryNorm.contains("do xe") || queryNorm.contains("gui xe") || queryNorm.contains("cho de xe") || queryNorm.contains("o to")
                || queryNorm.contains("dia chi") || queryNorm.contains("o dau") || queryNorm.contains("vi tri")
                || queryNorm.contains("so dien thoai") || queryNorm.contains("hotline") || queryNorm.contains("lien he");
    }

    /**
     * Sinh phản hồi chuẩn xác cho các câu hỏi thông tin nhà hàng.
     */
    private String getRestaurantInfoReply(String queryNorm) {
        if (queryNorm.contains("mo cua") || queryNorm.contains("dong cua") || queryNorm.contains("may gio") || queryNorm.contains("gio")) {
            return "Dạ nhà hàng mở cửa phục vụ từ 10:00 sáng đến 22:00 tất cả các ngày trong tuần. Quầy bếp nhận gọi món cuối lúc 21:30 ạ!";
        }
        if (queryNorm.contains("wifi") || queryNorm.contains("mat khau") || queryNorm.contains("pass")) {
            return "Dạ mạng Wifi miễn phí của nhà hàng là 'ROMS_Guest' (Mật khẩu: 'roms8888'). Quý khách có thể kết nối và sử dụng ạ!";
        }
        if (queryNorm.contains("do xe") || queryNorm.contains("gui xe") || queryNorm.contains("cho de xe") || queryNorm.contains("o to")) {
            return "Dạ nhà hàng có bãi đỗ xe máy và xe ô tô rộng rãi ngay phía trước, hoàn toàn miễn phí và có nhân viên bảo vệ trông giữ an toàn cho quý khách ạ!";
        }
        if (queryNorm.contains("dia chi") || queryNorm.contains("o dau") || queryNorm.contains("vi tri")) {
            return "Dạ nhà hàng ROMS tọa lạc tại vị trí trung tâm thuận tiện, không gian ấm cúng sang trọng, rất hân hạnh được đón tiếp quý khách ạ!";
        }
        if (queryNorm.contains("so dien thoai") || queryNorm.contains("hotline") || queryNorm.contains("lien he")) {
            return "Dạ số hotline hỗ trợ và đặt bàn của nhà hàng là: 1900 8888 (hoặc 0988 888 888) luôn sẵn sàng hỗ trợ quý khách ạ!";
        }
        return "Dạ em là Trợ lý Ẩm thực nhà hàng ROMS. Quý khách cần hỗ trợ thêm thông tin gì về thực đơn hoặc dịch vụ của nhà hàng không ạ?";
    }

    /**
     * Kiểm tra xem câu hỏi có đề cập cụ thể đến tên món hoặc nguyên liệu hay không.
     */
    private boolean isSpecificIngredientOrDishQuery(String queryNorm) {
        if (queryNorm == null) return false;
        return queryNorm.contains("mon ve") || queryNorm.contains("mon nao co") || queryNorm.contains("mon co")
                || queryNorm.contains("mon gi co") || queryNorm.contains("nau tu") || queryNorm.contains("nau voi")
                || queryNorm.contains("nguyen lieu") || queryNorm.contains("co mon") || queryNorm.contains("co khong")
                || queryNorm.contains("che bien tu") || queryNorm.contains("lam tu") || queryNorm.contains("chua ")
                || matchesWord(queryNorm, "bo") || queryNorm.contains("thit bo")
                || matchesWord(queryNorm, "ga") || queryNorm.contains("thit ga")
                || matchesWord(queryNorm, "tom") || queryNorm.contains("hai san")
                || matchesWord(queryNorm, "muc") || matchesWord(queryNorm, "ca")
                || queryNorm.contains("ca hoi") || queryNorm.contains("thit heo") || queryNorm.contains("thit lon")
                || matchesWord(queryNorm, "heo") || matchesWord(queryNorm, "lon") || matchesWord(queryNorm, "suon")
                || matchesWord(queryNorm, "ech") || matchesWord(queryNorm, "nam")
                || queryNorm.contains("khoai tay") || matchesWord(queryNorm, "trung")
                || queryNorm.contains("pho mai") || queryNorm.contains("pizza") || queryNorm.contains("sushi")
                || queryNorm.contains("thit cuu") || queryNorm.contains("thit cho")
                || queryNorm.contains("lau") || matchesWord(queryNorm, "pho") || matchesWord(queryNorm, "bun");
    }

    /**
     * Tìm nguyên liệu công thức của món ăn trùng khớp với câu hỏi của khách hàng.
     */
    private String findMatchingIngredient(MenuItem item, List<String> ingredients, String queryNorm) {
        // 1. Kiểm tra đối chiếu với danh sách nguyên liệu công thức
        if (ingredients != null) {
            for (String ing : ingredients) {
                String ingNorm = normalize(ing);
                if (matchesIngredient(queryNorm, ingNorm)) {
                    return ing;
                }
            }
        }
        // 2. Kiểm tra tên món
        String nameNorm = normalize(item.getName());
        if (matchesIngredient(queryNorm, nameNorm)) {
            return item.getName();
        }
        // 3. Kiểm tra mô tả
        String descNorm = normalize(item.getDescription());
        if (matchesIngredient(queryNorm, descNorm)) {
            return item.getName();
        }
        return null;
    }

    /**
     * Đối chiếu hai chuỗi xem có chứa nguyên liệu trùng khớp hay không.
     */
    private boolean matchesIngredient(String queryNorm, String targetNorm) {
        if (queryNorm == null || targetNorm == null) return false;
        // Bò
        if ((matchesWord(queryNorm, "bo") || queryNorm.contains("thit bo")) && (matchesWord(targetNorm, "bo") || targetNorm.contains("thit bo"))) return true;
        // Gà
        if ((matchesWord(queryNorm, "ga") || queryNorm.contains("thit ga")) && (matchesWord(targetNorm, "ga") || targetNorm.contains("thit ga"))) return true;
        // Tôm
        if (matchesWord(queryNorm, "tom") && matchesWord(targetNorm, "tom")) return true;
        // Heo / Lợn / Sườn / Ba chỉ
        if ((matchesWord(queryNorm, "heo") || matchesWord(queryNorm, "lon") || matchesWord(queryNorm, "suon") || queryNorm.contains("ba chi"))
                && (matchesWord(targetNorm, "heo") || matchesWord(targetNorm, "lon") || matchesWord(targetNorm, "suon") || targetNorm.contains("ba chi"))) return true;
        // Khoai tây
        if (queryNorm.contains("khoai") && targetNorm.contains("khoai")) return true;
        // Nấm
        if (queryNorm.contains("nam") && targetNorm.contains("nam")) return true;
        // Cà phê
        if (queryNorm.contains("ca phe") && targetNorm.contains("ca phe")) return true;
        // Trà
        if (matchesWord(queryNorm, "tra") && matchesWord(targetNorm, "tra")) return true;
        // Gạo / Cơm
        if ((matchesWord(queryNorm, "gao") || matchesWord(queryNorm, "com")) && (matchesWord(targetNorm, "gao") || matchesWord(targetNorm, "com"))) return true;
        // Phở
        if (matchesWord(queryNorm, "pho") && matchesWord(targetNorm, "pho")) return true;
        // Bún
        if (matchesWord(queryNorm, "bun") && matchesWord(targetNorm, "bun")) return true;
        // Hải sản
        if (queryNorm.contains("hai san") && (targetNorm.contains("tom") || targetNorm.contains("muc") || targetNorm.contains("ca") || targetNorm.contains("hai san"))) return true;
        // Cá hồi
        if (queryNorm.contains("ca hoi") && targetNorm.contains("ca hoi")) return true;
        // Mực
        if (matchesWord(queryNorm, "muc") && matchesWord(targetNorm, "muc")) return true;
        // Cá
        if (matchesWord(queryNorm, "ca") && matchesWord(targetNorm, "ca") && !targetNorm.contains("ca phe")) return true;
        // Trứng
        if (queryNorm.contains("trung") && targetNorm.contains("trung")) return true;
        // Sả
        if (queryNorm.contains("sa") && targetNorm.contains("sa")) return true;
        // Ớt
        if (queryNorm.contains("ot") && targetNorm.contains("ot")) return true;
        // Dừa
        if (queryNorm.contains("dua") && targetNorm.contains("dua")) return true;
        // Bơ
        if (queryNorm.contains("butter_toi") || queryNorm.contains("b_butter")) {
            if (targetNorm.contains("butter_toi") || targetNorm.contains("b_butter")) return true;
        }
        return false;
    }

    /**
     * Tính điểm độ phù hợp của món ăn với các yêu cầu tổng quát.
     */
    private int calculateScore(MenuItem item, String queryNorm, boolean wantsVegetarian, boolean wantsNoSpicy,
                               boolean wantsSpicy, boolean wantsSeafood, boolean wantsDrinks, boolean wantsFamilyCombo) {
        int score = 0;
        String nameNorm = normalize(item.getName());
        String catNorm = item.getCategory() != null ? normalize(item.getCategory().getName()) : "";
        String descNorm = normalize(item.getDescription());

        if (Boolean.TRUE.equals(item.getIsFeatured())) {
            score += 5;
        }

        if (wantsDrinks) {
            if (catNorm.contains("do uong") || catNorm.contains("trang mieng") || nameNorm.contains("tra") || nameNorm.contains("ca phe")) {
                score += 30;
            } else {
                score -= 15;
            }
        }

        if (wantsSeafood) {
            if (nameNorm.contains("tom") || nameNorm.contains("muc") || nameNorm.contains("ca") || nameNorm.contains("hai san") || descNorm.contains("tom")) {
                score += 35;
            }
        }

        if (wantsVegetarian) {
            if (catNorm.contains("chay") || nameNorm.contains("goi cuon") || nameNorm.contains("pho") || descNorm.contains("thanh mat")) {
                score += 30;
            }
            if (nameNorm.contains("muoi ot") || nameNorm.contains("chien") || nameNorm.contains("ran")) {
                score -= 25;
            }
        }

        if (wantsNoSpicy) {
            if (nameNorm.contains("ot") || nameNorm.contains("cay") || descNorm.contains("cay")) {
                score -= 30;
            } else {
                score += 8;
            }
        } else if (wantsSpicy) {
            if (nameNorm.contains("ot") || nameNorm.contains("muoi ot") || descNorm.contains("cay") || nameNorm.contains("hue")) {
                score += 30;
            }
        }

        String[] tokens = queryNorm.split("\\s+");
        for (String t : tokens) {
            if (t.length() >= 2) {
                if (nameNorm.contains(t)) score += 12;
                if (descNorm.contains(t)) score += 6;
                if (catNorm.contains(t)) score += 8;
            }
        }

        return score;
    }

    /**
     * Kiểm tra món ăn có dính thành phần dị ứng không.
     */
    private boolean containsAllergy(MenuItem item, String allergyNorm, List<String> ingredients) {
        if (allergyNorm == null || allergyNorm.isEmpty()) return false;
        String nameNorm = normalize(item.getName());
        String descNorm = normalize(item.getDescription());

        for (String ing : ingredients) {
            String ingNorm = normalize(ing);
            if (allergyNorm.contains("tom") && ingNorm.contains("tom")) return true;
            if (allergyNorm.contains("hai san") && (ingNorm.contains("tom") || ingNorm.contains("muc") || ingNorm.contains("ca"))) return true;
            if (allergyNorm.contains("bo") && ingNorm.contains("bo")) return true;
            if (allergyNorm.contains("ga") && ingNorm.contains("ga")) return true;
            if ((allergyNorm.contains("cay") || allergyNorm.contains("ot")) && ingNorm.contains("ot")) return true;
        }

        if (allergyNorm.contains("tom") && (nameNorm.contains("tom") || descNorm.contains("tom"))) return true;
        if (allergyNorm.contains("hai san") && (nameNorm.contains("tom") || nameNorm.contains("muc") || nameNorm.contains("ca") || nameNorm.contains("hai san"))) return true;
        if (allergyNorm.contains("bo") && (nameNorm.contains("bo") || descNorm.contains("bo"))) return true;
        if (allergyNorm.contains("ga") && (nameNorm.contains("ga") || descNorm.contains("ga"))) return true;
        if ((allergyNorm.contains("cay") || allergyNorm.contains("ot")) && (nameNorm.contains("ot") || descNorm.contains("cay"))) return true;
        return false;
    }

    /**
     * Kiểm tra khớp nguyên từ (word boundary).
     */
    private boolean matchesWord(String text, String word) {
        if (text == null || word == null) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    /**
     * Sinh lời dẫn mở đầu cho các gợi ý tổng quát.
     */
    private String generateIntroMessage(String queryNorm, int partySize, boolean wantsVegetarian, boolean wantsNoSpicy,
                                        boolean wantsSpicy, boolean wantsSeafood, boolean wantsDrinks, boolean wantsFamilyCombo) {
        if (wantsDrinks) {
            return "Nhà hàng xin gửi tới quý khách các thức uống thanh mát và món tráng miệng sảng khoái sau bữa ăn:";
        } else if (wantsSeafood) {
            return "Dành riêng cho thực khách yêu thích hải sản tươi ngon, nhà hàng xin giới thiệu các món hải sản đặc sắc sau:";
        } else if (wantsVegetarian || queryNorm.contains("thanh dam")) {
            return "Để đáp ứng khẩu vị thanh đạm, ít dầu mỡ, nhà hàng đã chọn lọc những món ăn tươi mát, nhẹ nhàng phục vụ quý khách:";
        } else if (wantsNoSpicy) {
            return "Dưới đây là các món ăn thơm ngon, thanh ngọt tự nhiên nhưng hoàn toàn không cay, rất phù hợp cho khẩu vị của bạn:";
        } else if (wantsSpicy || queryNorm.contains("nhau")) {
            return "Thực đơn lai rai đậm vị cùng các món nướng giòn rụm và đồ nhắm thơm nức mũi:";
        } else if (wantsFamilyCombo || partySize >= 3) {
            int count = partySize > 0 ? partySize : 4;
            return String.format("Để quý khách có bữa ăn gia đình ấm cúng và trọn vẹn cho nhóm %d người, nhà hàng xin gợi ý set thực đơn cân đối sau:", count);
        } else if (queryNorm.contains("duoi 500") || queryNorm.contains("500k") || queryNorm.contains("tiet kiem")) {
            return "Nhà hàng xin gợi ý set ăn hấp dẫn, đầy đặn và tiết kiệm (ngân sách dưới 500.000đ) phù hợp cho 2 người:";
        }
        return "Dựa trên yêu cầu của quý khách, nhà hàng đã chọn lọc những món ăn tươi ngon, phù hợp nhất đang sẵn sàng phục vụ:";
    }

    /**
     * Sinh lý do hấp dẫn cho từng món ăn tương ứng dựa trên công thức hoặc đặc trưng.
     */
    private String generateDishReason(MenuItem item, String queryNorm, String matchedIngredient) {
        if (matchedIngredient != null && !matchedIngredient.isBlank()) {
            return String.format("Món ăn có thành phần %s trong công thức chế biến chuẩn vị thơm ngon, thịt mềm ngọt tự nhiên.", matchedIngredient);
        }
        String name = item.getName();
        if (name.contains("Phở Bò")) {
            return "Nước dùng thanh ngọt hầm xương 24h, thịt bò phi lê tươi mềm, hương vị truyền thống chuẩn vị Bắc.";
        } else if (name.contains("Bún Bò Huế")) {
            return "Nước dùng đậm đà thơm mùi sả và mắm ruốc Huế đặc trưng, thích hợp cho thực khách thích vị đậm đà cay nồng.";
        } else if (name.contains("Gà Nướng")) {
            return "Gà ta thịt chắc ngọt, da nướng giòn rụm cay thơm nức mũi, là món khoái khẩu trong các bữa tiệc.";
        } else if (name.contains("Tôm Hấp")) {
            return "Tôm tươi sống chắc ngọt quyện cùng vị thanh dịu tự nhiên của nước dừa xiêm, ngọt nước và bổ dưỡng.";
        } else if (name.contains("Gỏi Cuốn")) {
            return "Món khai vị thanh mát, nhiều rau xanh tươi, không dầu mỡ, ăn kèm nước chấm chua ngọt đậm vị.";
        } else if (name.contains("Nem Rán")) {
            return "Vỏ nem giòn rụm vàng óng, nhân thịt mộc nhĩ nấm hương thơm bùi, ăn kèm rau sống chuẩn phong vị Hà Nội.";
        } else if (name.contains("Khoai Tây Chiên")) {
            return "Món ăn kèm giòn tan, thơm lừng bơ tỏi vàng ươm, rất được ưa chuộng cho nhóm bạn và trẻ nhỏ.";
        } else if (name.contains("Trà Đào Cam Sả")) {
            return "Thức uống giải nhiệt thanh mát, vị đào giòn ngọt thơm lừng hương sả, giúp giải ngấy sau bữa ăn.";
        } else if (name.contains("Cà Phê Sữa Đá")) {
            return "Hương vị cà phê nguyên chất rang xay thơm nồng hòa quyện cùng sữa đặc béo ngậy chuẩn gu Sài Gòn.";
        }
        return "Món ăn đặc sắc của nhà hàng, nguyên liệu tươi mới, hương vị hài hòa phù hợp với yêu cầu của quý khách.";
    }

    /**
     * Trích xuất nguyên liệu phỏng đoán từ tên món và mô tả nếu món chưa có bản ghi recipe_items.
     */
    private List<String> inferIngredientsFromNameAndDesc(String name, String desc) {
        List<String> list = new ArrayList<>();
        String combined = ((name != null ? name : "") + " " + (desc != null ? desc : "")).toLowerCase();
        if (combined.contains("bo") || combined.contains("bò")) list.add("Thịt bò");
        if (combined.contains("ga") || combined.contains("gà")) list.add("Thịt gà");
        if (combined.contains("tom") || combined.contains("tôm")) list.add("Tôm sú");
        if (combined.contains("heo") || combined.contains("lon") || combined.contains("lợn") || combined.contains("thịt")) list.add("Thịt heo");
        if (combined.contains("khoai tay") || combined.contains("khoai tây")) list.add("Khoai tây");
        if (combined.contains("bo toi") || combined.contains("bơ tỏi")) list.add("Bơ tỏi");
        if (combined.contains("nam huong") || combined.contains("nấm hương") || combined.contains("nấm")) list.add("Nấm hương");
        if (combined.contains("moc nhi") || combined.contains("mộc nhĩ")) list.add("Mộc nhĩ");
        if (combined.contains("ca phe") || combined.contains("cà phê")) list.add("Cà phê");
        if (combined.contains("tra") || combined.contains("trà")) list.add("Trà");
        if (combined.contains("cam sa") || combined.contains("cam sả") || combined.contains("sả")) list.add("Cam sả");
        if (combined.contains("dua") || combined.contains("dừa")) list.add("Nước dừa");
        return list;
    }

    /**
     * Kiểm tra khả năng bán thực tế của món ăn dựa trên trạng thái và tồn kho nguyên liệu công thức.
     */
    private boolean checkLiveSellability(MenuItem menuItem) {
        if (menuItem.getStatus() != MenuItemStatus.AVAILABLE) {
            return false;
        }

        List<RecipeItem> recipeItems = recipeItemRepository.findByMenuItemIdWithIngredient(menuItem.getId());
        if (recipeItems.isEmpty()) {
            return true;
        }

        for (RecipeItem recipeItem : recipeItems) {
            if (recipeItem.getIngredient() == null) {
                return false;
            }
            if (!Boolean.TRUE.equals(recipeItem.getIngredient().getIsActive())) {
                return false;
            }
            if (recipeItem.getIngredient().getCurrentStock() == null ||
                    recipeItem.getIngredient().getCurrentStock().compareTo(recipeItem.getQuantityRequired()) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Xóa dấu tiếng Việt và chuẩn hóa chữ thường.
     */
    private String normalize(String input) {
        if (input == null) return "";
        String preProcessed = input.toLowerCase()
                .replaceAll("\\bbơ\\b", "b_butter")
                .replaceAll("bơ tỏi", "butter_toi");
        String normalized = Normalizer.normalize(preProcessed, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("đ", "d")
                .replaceAll("Đ", "d")
                .trim();
    }

    /**
     * Xây dựng chỉ thị hệ thống (System Instruction) cho Gemini.
     */
    private String buildSystemInstruction() {
        return """
Bạn là Trợ lý Ẩm thực thông minh và ân cần của nhà hàng ROMS (Restaurant Operations Management System).
Nhiệm vụ của bạn là trò chuyện, trả lời chuẩn xác câu hỏi của thực khách và chỉ gợi ý món ăn khi thực sự phù hợp dựa trên danh sách thực đơn của nhà hàng.

QUY TẮC CỐT LÕI (BẮT BUỘC TUÂN THỦ NGHIÊM NGẶT):
1. PHÂN LOẠI Ý ĐỊNH VÀ TRẢ LỜI ĐÚNG TRỌNG TÂM:
   a. CÂU HỎI THÔNG TIN CHUNG / CHÀO HỎI / GIAO TIẾP (Không yêu cầu gợi ý món):
      - Ví dụ: "Xin chào", "Hello", "Cảm ơn", "Quán mở cửa mấy giờ?", "Có wifi không?", "Có chỗ đỗ ô tô không?", "Địa chỉ ở đâu?", "Nhà hàng có dịch vụ gì?"...
      - Hành động: Trả lời lịch sự, đầy đủ, thân thiện trong "message".
      - "recommendations": [] (BẮT BUỘC LÀ MẢNG RỖNG. TUYỆT ĐỐI KHÔNG gửi kèm món ăn khi khách chỉ hỏi thông tin chung hoặc chào hỏi).
   
   b. HỎI MÓN THEO NGUYÊN LIỆU CỤ THỂ HOẶC TÌM MÓN CỤ THỂ:
      - Ví dụ: "Món nào có thịt bò?", "Có món gì nấu từ tôm?", "Món nào có nấm?", "Có món cá hồi không?", "Quán có pizza / sushi không?", "Có lẩu không?", "Món có khoai tây"...
      - Hành động: Đối chiếu kỹ với danh sách nguyên liệu công thức ("ingredients"), tên món ("name") và mô tả ("description") trong danh sách thực đơn được cung cấp:
        + NẾU CÓ MÓN PHÙ HỢP:
          * Đưa đúng các món có nguyên liệu/tên trùng khớp vào "recommendations".
          * Trong "reason" của từng món: BẮT BUỘC nêu rõ món có chứa nguyên liệu [X] trong công thức chế biến đúng theo yêu cầu của khách.
          * Trong "message": Giới thiệu rõ ràng, ngắn gọn các món có nguyên liệu đó.
        + NẾU THỰC ĐƠN KHÔNG CÓ MÓN NÀO CHỨA NGUYÊN LIỆU ĐÓ (hoặc không có món đó):
          * BÁO RÕ RÀNG VÀ CHÍNH XÁC trong "message" là hiện tại nhà hàng KHÔNG CÓ món ăn này hoặc không có món nào chế biến từ nguyên liệu đó (ví dụ: "Dạ rất tiếc, hiện tại thực đơn nhà hàng chưa có món ăn nào chế biến từ cá hồi ạ.").
          * "recommendations": [] (BẮT BUỘC LÀ MẢNG RỖNG. TUYỆT ĐỐI KHÔNG ĐƯỢC GỢI Ý MÓN LINH TINH KHÁC khi khách hỏi tìm nguyên liệu mà quán không có).
   
   c. YÊU CẦU TƯ VẤN / GỢI Ý THỰC ĐƠN / COMBO ĂN UỐNG:
      - Ví dụ: "Gợi ý bữa trưa cho 4 người", "Tư vấn món thanh đạm", "Có món gì ngon?", "Món cay đậm vị", "Món tráng miệng", "Đồ uống giải nhiệt", "Set ăn dưới 500k"...
      - Hành động: Chọn lọc 1 đến 4 món ăn phù hợp nhất từ thực đơn, kết hợp hài hòa, tuân thủ dị ứng/kiêng cữ (nếu có).
      - Điền lời tư vấn trong "message" và đưa các món vào "recommendations".

2. CHỈ ĐƯỢC DÙNG CÁC MÓN ĂN VÀ menuItemId CÓ TRONG THỰC ĐƠN ĐƯỢC CUNG CẤP. Tuyệt đối không tự bịa ra món ăn hoặc ID không tồn tại.
3. Tuân thủ nghiêm ngặt dị ứng và kiêng cữ của khách hàng.
4. ĐỊNH DẠNG ĐẦU RA BẮT BUỘC PHẢI LÀ JSON HỢP LỆ VỚI CẤU TRÚC:
{
  "message": "Lời phản hồi chuẩn xác, ân cần theo đúng câu hỏi của thực khách",
  "recommendations": [
    {
      "menuItemId": 1,
      "reason": "Giải thích ngắn gọn, nêu rõ thành phần nguyên liệu trong công thức đáp ứng yêu cầu"
    }
  ]
}
Lưu ý: Nếu không đề xuất món (chào hỏi/thông tin chung/món không có), "recommendations" bắt buộc là mảng rỗng []. Không kèm bất kỳ văn bản nào ngoài JSON.
""";
    }

    /**
     * Xây dựng User Prompt kết hợp thông tin khách hàng và dữ liệu Menu Context.
     */
    private String buildUserPrompt(AiRecommendationRequest request, String menuContextJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("DANH SÁCH THỰC ĐƠN VÀ NGUYÊN LIỆU CÔNG THỨC NHÀ HÀNG (JSON):\n");
        sb.append(menuContextJson).append("\n\n");
        sb.append("THÔNG TIN TRÒ CHUYỆN & YÊU CẦU CỦA KHÁCH HÀNG:\n");

        String effectiveMsg = request.getEffectiveMessage() != null ? request.getEffectiveMessage().trim() : "";
        if (!effectiveMsg.isEmpty()) {
            sb.append("- Tin nhắn / Câu hỏi của khách: \"").append(effectiveMsg).append("\"\n");
        }
        if (request.getPreferences() != null && !request.getPreferences().trim().isEmpty() && !request.getPreferences().trim().equalsIgnoreCase(effectiveMsg)) {
            sb.append("- Khẩu vị / Sở thích: ").append(request.getPreferences().trim()).append("\n");
        }
        if (request.getPartySize() != null && request.getPartySize() > 0) {
            sb.append("- Số lượng người ăn: ").append(request.getPartySize()).append(" người\n");
        }
        if (request.getAllergies() != null && !request.getAllergies().trim().isEmpty()) {
            sb.append("- Dị ứng / Kiêng cử đặc biệt: ").append(request.getAllergies().trim()).append("\n");
        }
        if (request.getBudgetRange() != null && !request.getBudgetRange().trim().isEmpty()) {
            sb.append("- Ngân sách dự kiến: ").append(request.getBudgetRange().trim()).append("\n");
        }

        sb.append("\nHƯỚNG DẪN XỬ LÝ:\n");
        sb.append("1. Nếu khách chào hỏi, cảm ơn hoặc hỏi thông tin chung (giờ mở cửa, wifi, bãi đỗ xe...): Trả lời đúng thông tin trong \"message\" và để \"recommendations\": [].\n");
        sb.append("2. Nếu khách hỏi món theo nguyên liệu hoặc tìm món cụ thể:\n");
        sb.append("   - Tra cứu trong \"ingredients\" (công thức), \"name\" và \"description\".\n");
        sb.append("   - Nếu CÓ món chứa nguyên liệu đó: Gợi ý các món đó vào \"recommendations\" và nêu rõ nguyên liệu trong \"reason\".\n");
        sb.append("   - Nếu KHÔNG CÓ món nào chứa nguyên liệu đó: Thông báo rõ trong \"message\" là nhà hàng không có món này và để \"recommendations\": []. Tuyệt đối không gợi ý món linh tinh khác.\n");
        sb.append("3. Nếu khách yêu cầu gợi ý món/combo: Chọn 1-4 món phù hợp và giải thích lý do.\n");
        sb.append("Trả về đúng định dạng JSON.");
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
     * Lớp DTO nội bộ ánh xạ phản hồi JSON thô từ Gemini.
     */
    @Getter
    @Setter
    public static class AiRawResponse {
        private String message;
        private List<AiRawItem> recommendations;

        public AiRawResponse() {
        }
    }

    /**
     * Lớp DTO nội bộ biểu diễn từng món ăn gợi ý từ Gemini.
     */
    @Getter
    @Setter
    public static class AiRawItem {
        private Long menuItemId;
        private String reason;

        public AiRawItem() {
        }
    }
}
