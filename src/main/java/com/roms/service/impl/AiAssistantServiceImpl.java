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
 * Service triển khai nghiệp vụ Trợ lý AI Gemini gợi ý món ăn.
 * 
 * Các quy tắc cốt lõi:
 * 1. Gọi Gemini API nếu có cấu hình; nếu dịch vụ AI bên ngoài gặp sự cố mạng/hết hạn quota,
 *    tự động kích hoạt Động cơ Phân tích Thực đơn Thông minh (Smart Menu Items Recommender)
 *    dựa trực tiếp vào bảng menu_items và câu hỏi/nhu cầu của thực khách.
 * 2. Xác thực ID món ăn: Chỉ chấp nhận các món ăn thực sự tồn tại trong PostgreSQL và chưa bị DISCONTINUED.
 * 3. Kiểm tra khả năng bán thực tế (Live Recipe/Stock Sellability): Món phải có trạng thái AVAILABLE
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
     * Gợi ý món ăn thông minh theo yêu cầu của khách hàng.
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
                if (parsedResponse != null && parsedResponse.getRecommendations() != null && !parsedResponse.getRecommendations().isEmpty()) {
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

                        List<AiRecommendationItem> finalRecommendations = new ArrayList<>();
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
                                        .reason(!reason.isEmpty() ? reason : generateDishReason(menuItem, normalize(safeReq.getMessage())))
                                        .isSellable(true)
                                        .build());
                            }
                        }

                        if (!finalRecommendations.isEmpty()) {
                            String aiMessage = (parsedResponse.getMessage() != null && !parsedResponse.getMessage().trim().isEmpty())
                                    ? parsedResponse.getMessage().trim()
                                    : "Dưới đây là các món ăn được lựa chọn riêng theo khẩu vị và yêu cầu của quý khách:";
                            return AiRecommendationResponse.builder()
                                    .message(aiMessage)
                                    .recommendations(finalRecommendations)
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed or timed out ({}). Activating smart database-driven menu recommender.", e.getMessage());
            }
        }

        // 2. Kích hoạt Động cơ Phân tích Thực đơn Thông minh (Truy vấn trực tiếp từ menu_items)
        return recommendFromMenuItems(safeReq);
    }

    /**
     * Phân tích câu hỏi của khách hàng, lọc và xếp hạng các món ăn trong menu_items
     * theo đúng sở thích, dị ứng, số lượng người và ngân sách.
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
        String effectiveMsg = request.getEffectiveMessage() != null ? request.getEffectiveMessage() : "";
        String rawQuery = String.join(" ",
                effectiveMsg,
                request.getPreferences() != null ? request.getPreferences() : ""
        ).trim();
        String queryNorm = normalize(rawQuery);
        String allergyNorm = normalize(request.getAllergies());
        int partySize = request.getPartySize() != null && request.getPartySize() > 0 ? request.getPartySize() : 0;

        // Phân tích ý định (Intent Detection)
        boolean wantsVegetarian = queryNorm.contains("chay") || queryNorm.contains("thanh dam") || queryNorm.contains("it dau");
        boolean wantsNoSpicy = queryNorm.contains("khong cay") || queryNorm.contains("it cay") || allergyNorm.contains("cay") || allergyNorm.contains("ot");
        boolean wantsSpicy = (queryNorm.contains("cay") || queryNorm.contains("dam da") || queryNorm.contains("nhau")) && !wantsNoSpicy;
        boolean wantsSeafood = queryNorm.contains("hai san") || queryNorm.contains("tom") || queryNorm.contains("cua") || queryNorm.contains("muc") || queryNorm.contains("ca");
        boolean wantsDrinks = queryNorm.contains("do uong") || queryNorm.contains("nuoc") || queryNorm.contains("tra") || queryNorm.contains("trang mieng") || queryNorm.contains("ca phe");
        boolean wantsFamilyCombo = queryNorm.contains("gia dinh") || queryNorm.contains("combo") || queryNorm.contains("nhom") || partySize >= 3;

        // 1. Lọc loại trừ dị ứng
        List<MenuItem> nonAllergicItems = availableItems.stream()
                .filter(item -> !containsAllergy(item, allergyNorm))
                .toList();

        if (nonAllergicItems.isEmpty()) {
            nonAllergicItems = availableItems;
        }

        // 2. Tính điểm độ phù hợp cho từng món
        final String fQueryNorm = queryNorm;
        Map<MenuItem, Integer> scoredItems = new HashMap<>();
        for (MenuItem item : nonAllergicItems) {
            int score = calculateScore(item, fQueryNorm, wantsVegetarian, wantsNoSpicy, wantsSpicy, wantsSeafood, wantsDrinks, wantsFamilyCombo);
            scoredItems.put(item, score);
        }

        // 3. Sắp xếp món theo điểm số giảm dần
        List<MenuItem> sortedItems = nonAllergicItems.stream()
                .sorted((a, b) -> Integer.compare(scoredItems.getOrDefault(b, 0), scoredItems.getOrDefault(a, 0)))
                .toList();

        // 4. Chọn lọc danh sách hài hòa (Combo cân bằng nếu khách đi theo nhóm/gia đình)
        List<MenuItem> selectedItems = new ArrayList<>();
        if (wantsFamilyCombo && sortedItems.size() >= 3) {
            // Chọn 1 khai vị
            sortedItems.stream()
                    .filter(m -> m.getCategory() != null && normalize(m.getCategory().getName()).contains("khai vi"))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            // Chọn 1-2 món chính
            sortedItems.stream()
                    .filter(m -> m.getCategory() != null && normalize(m.getCategory().getName()).contains("chinh") && !selectedItems.contains(m))
                    .limit(partySize >= 4 ? 2 : 1)
                    .forEach(selectedItems::add);

            // Chọn 1 món ăn kèm / món chơi
            sortedItems.stream()
                    .filter(m -> !selectedItems.contains(m) && m.getCategory() != null && (normalize(m.getCategory().getName()).contains("kem") || normalize(m.getName()).contains("khoai")))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            // Chọn 1 đồ uống
            sortedItems.stream()
                    .filter(m -> !selectedItems.contains(m) && m.getCategory() != null && normalize(m.getCategory().getName()).contains("do uong"))
                    .findFirst()
                    .ifPresent(selectedItems::add);

            // Nếu chưa đủ món, bổ sung các món điểm cao nhất còn lại
            for (MenuItem item : sortedItems) {
                if (selectedItems.size() >= (partySize >= 4 ? 4 : 3)) break;
                if (!selectedItems.contains(item)) {
                    selectedItems.add(item);
                }
            }
        } else {
            // Kiểm tra xem có món nào trùng khớp mạnh với câu hỏi không (score >= 20)
            int maxScore = sortedItems.isEmpty() ? 0 : scoredItems.getOrDefault(sortedItems.get(0), 0);
            if (maxScore >= 20) {
                // Khách hỏi nhu cầu cụ thể (thịt bò, gà, hải sản, đồ uống...), chỉ gợi ý các món khớp nhu cầu (tối đa 4 món)
                sortedItems.stream()
                        .filter(item -> scoredItems.getOrDefault(item, 0) >= Math.max(15, maxScore / 2))
                        .limit(4)
                        .forEach(selectedItems::add);
            }

            // Nếu câu hỏi chung chung hoặc chưa chọn được món nào
            if (selectedItems.isEmpty()) {
                int targetCount = Math.min(4, Math.max(2, sortedItems.size()));
                sortedItems.stream().limit(targetCount).forEach(selectedItems::add);
            }
        }

        // 5. Tạo thông điệp chào mừng và giải thích phù hợp với câu hỏi
        String introMessage = generateIntroMessage(fQueryNorm, partySize, wantsVegetarian, wantsNoSpicy, wantsSpicy, wantsSeafood, wantsDrinks, wantsFamilyCombo);

        // 6. Ánh xạ sang DTO trả về kèm lý do sinh động
        List<AiRecommendationItem> recommendations = selectedItems.stream()
                .map(item -> AiRecommendationItem.builder()
                        .menuItemId(item.getId())
                        .name(item.getName())
                        .price(item.getPrice())
                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "Món ăn")
                        .description(item.getDescription())
                        .reason(generateDishReason(item, fQueryNorm))
                        .isSellable(true)
                        .build())
                .toList();

        return AiRecommendationResponse.builder()
                .message(introMessage)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Tính điểm độ phù hợp của món ăn với câu hỏi.
     */
    private int calculateScore(MenuItem item, String queryNorm, boolean wantsVegetarian, boolean wantsNoSpicy,
                               boolean wantsSpicy, boolean wantsSeafood, boolean wantsDrinks, boolean wantsFamilyCombo) {
        int score = 0;
        String nameNorm = normalize(item.getName());
        String catNorm = item.getCategory() != null ? normalize(item.getCategory().getName()) : "";
        String descNorm = normalize(item.getDescription());

        // Món nổi bật được ưu tiên nhẹ
        if (Boolean.TRUE.equals(item.getIsFeatured())) {
            score += 4;
        }

        // Nhu cầu đồ uống / tráng miệng
        if (wantsDrinks) {
            if (catNorm.contains("do uong") || catNorm.contains("trang mieng") || nameNorm.contains("tra") || nameNorm.contains("ca phe")) {
                score += 30;
            } else {
                score -= 15;
            }
        }

        // Nhu cầu hải sản
        if (wantsSeafood) {
            if (nameNorm.contains("tom") || nameNorm.contains("muc") || nameNorm.contains("ca") || nameNorm.contains("hai san") || descNorm.contains("tom")) {
                score += 35;
            }
        }

        // Nhu cầu thanh đạm / ít dầu mỡ
        if (wantsVegetarian) {
            if (catNorm.contains("chay") || nameNorm.contains("goi cuon") || nameNorm.contains("pho") || descNorm.contains("thanh mat")) {
                score += 30;
            }
            if (nameNorm.contains("muoi ot") || nameNorm.contains("chien") || nameNorm.contains("ran")) {
                score -= 25;
            }
        }

        // Không cay vs Cay
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

        // Trùng khớp từ khóa món cụ thể (sử dụng ranh giới từ để tránh nhầm "combo" với "bò")
        boolean queryHasBeef = matchesWord(queryNorm, "bo") || queryNorm.contains("thit bo");
        boolean queryHasChicken = matchesWord(queryNorm, "ga") || queryNorm.contains("thit ga");
        if (queryHasBeef && (matchesWord(nameNorm, "bo") || matchesWord(descNorm, "bo"))) score += 50;
        if (queryHasChicken && (matchesWord(nameNorm, "ga") || matchesWord(descNorm, "ga"))) score += 45;
        if (matchesWord(queryNorm, "pho") && matchesWord(nameNorm, "pho")) score += 40;
        if ((queryNorm.contains("bun bo") || matchesWord(queryNorm, "bun")) && matchesWord(nameNorm, "bun")) score += 40;
        if (matchesWord(queryNorm, "tom") && matchesWord(nameNorm, "tom")) score += 40;
        if (matchesWord(queryNorm, "nem") && matchesWord(nameNorm, "nem")) score += 40;
        if (matchesWord(queryNorm, "khoai") && matchesWord(nameNorm, "khoai")) score += 40;
        if (queryNorm.contains("ca phe") && nameNorm.contains("ca phe")) score += 40;
        if (matchesWord(queryNorm, "tra") && matchesWord(nameNorm, "tra")) score += 40;

        // Trùng khớp từ khóa chung
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
    private boolean containsAllergy(MenuItem item, String allergyNorm) {
        if (allergyNorm.isEmpty()) return false;
        String nameNorm = normalize(item.getName());
        String descNorm = normalize(item.getDescription());

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
     * Sinh lời dẫn mở đầu sinh động phù hợp với câu hỏi.
     */
    private String generateIntroMessage(String queryNorm, int partySize, boolean wantsVegetarian, boolean wantsNoSpicy,
                                        boolean wantsSpicy, boolean wantsSeafood, boolean wantsDrinks, boolean wantsFamilyCombo) {
        boolean queryHasBeef = matchesWord(queryNorm, "bo") || queryNorm.contains("thit bo");
        boolean queryHasChicken = matchesWord(queryNorm, "ga") || queryNorm.contains("thit ga");
        if (queryHasBeef) {
            return "Nhà hàng xin giới thiệu các món ăn đặc sắc chế biến từ thịt bò tươi mềm, đậm đà dinh dưỡng sau:";
        } else if (queryHasChicken) {
            return "Nhà hàng xin giới thiệu các món gà ta thơm ngon, da giòn thịt ngọt sẵn sàng phục vụ:";
        } else if (wantsDrinks) {
            return "Nhà hàng xin gửi tới quý khách các thức uống thanh mát và món tráng miệng sảng khoái sau bữa ăn:";
        } else if (wantsSeafood) {
            return "Dành riêng cho thực khách yêu thích hải sản tươi ngon, nhà hàng xin giới thiệu các món hải sản đặc sắc đậm đà dinh dưỡng sau:";
        } else if (wantsVegetarian || queryNorm.contains("thanh dam")) {
            return "Để đáp ứng khẩu vị thanh đạm, tốt cho sức khỏe và ít dầu mỡ, nhà hàng đã chọn lọc những món ăn tươi mát, nhẹ nhàng phục vụ quý khách:";
        } else if (wantsNoSpicy) {
            return "Dưới đây là các món ăn thơm ngon, thanh ngọt tự nhiên nhưng hoàn toàn không cay, rất phù hợp cho khẩu vị của bạn:";
        } else if (wantsSpicy || queryNorm.contains("nhau")) {
            return "Thực đơn lai rai đậm vị cùng bạn bè với các món nướng giòn rụm và đồ nhắm thơm nức mũi:";
        } else if (wantsFamilyCombo || partySize >= 3) {
            int count = partySize > 0 ? partySize : 4;
            return String.format("Để quý khách có một bữa ăn gia đình ấm cúng và trọn vẹn cho nhóm %d người, nhà hàng xin gợi ý set thực đơn kết hợp hài hòa giữa món chính, khai vị và thức uống sau:", count);
        } else if (queryNorm.contains("duoi 500") || queryNorm.contains("500k") || queryNorm.contains("tiet kiem")) {
            return "Nhà hàng xin gợi ý set ăn hấp dẫn, đầy đặn và rất tiết kiệm (ngân sách dưới 500.000đ) phù hợp cho 2 người:";
        } else if (queryNorm.contains("pho") || queryNorm.contains("bun")) {
            return "Nhà hàng xin giới thiệu các món bún, phở truyền thống với nước dùng ninh xương đậm đà, nóng hổi thơm ngon:";
        }
        return "Dựa trên yêu cầu của quý khách, nhà hàng đã chọn lọc những món ăn tươi ngon, phù hợp nhất đang sẵn sàng phục vụ:";
    }

    /**
     * Sinh lý do hấp dẫn cho từng món ăn tương ứng.
     */
    private String generateDishReason(MenuItem item, String queryNorm) {
        String name = item.getName();
        if (name.contains("Phở Bò")) {
            return "Nước dùng thanh ngọt hầm xương 24h, thịt bò mềm tươi ngon, hương vị truyền thống chuẩn vị Bắc.";
        } else if (name.contains("Bún Bò Huế")) {
            return "Nước dùng đậm đà thơm mùi sả và mắm ruốc Huế đặc trưng, thích hợp cho thực khách thích hương vị đậm đà.";
        } else if (name.contains("Gà Nướng")) {
            return "Gà ta thịt chắc ngọt, da nướng giòn rụm cay thơm nức mũi, là món khoái khẩu trong các bữa tiệc gia đình và bạn bè.";
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
     * Chú ý thay thế "bơ" thành "b_butter" trước khi khử dấu để tránh nhầm lẫn giữa "bơ" (bơ tỏi) và "bò" (thịt bò).
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
        return "Bạn là Trợ lý Ẩm thực thông minh và ân cần của nhà hàng ROMS (Restaurant Operations Management System).\n" +
                "Nhiệm vụ của bạn là trò chuyện trực tiếp, lắng nghe nhu cầu và gợi ý những món ăn ngon, chuẩn vị, hài hòa nhất cho thực khách dựa trên danh sách thực đơn thực tế của nhà hàng.\n\n" +
                "QUY TẮC BẮT BUỘC:\n" +
                "1. BẠN CHỈ ĐƯỢC GỢI Ý CÁC MÓN ĂN CÓ TRONG DANH SÁCH THỰC ĐƠN ĐƯỢC CUNG CẤP (với đúng menuItemId). TUYỆT ĐỐI KHÔNG TỰ BỊA RA TÊN MÓN HOẶC ID KHÔNG CÓ TRONG THỰC ĐƠN.\n" +
                "2. Tuân thủ nghiêm ngặt các thông tin dị ứng/kiêng kị của khách hàng để loại bỏ các món chứa nguyên liệu đó.\n" +
                "3. Khi khách yêu cầu gợi ý món hoặc combo, hãy tư vấn kết hợp cân đối (Khai vị / Món chính / Canh hoặc Rau / Đồ uống hoặc Tráng miệng) phù hợp với khẩu vị, số người và ngân sách nếu có.\n" +
                "4. Lời nhắn (message): Hãy đóng vai chuyên gia ẩm thực chu đáo, trò chuyện tự nhiên, giải thích súc tích lý do vì sao sự kết hợp này tạo nên bữa ăn trọn vẹn.\n" +
                "5. Kết quả trả về BẮT BUỘC PHẢI LÀ MỘT ĐỐI TƯỢNG JSON HỢP LỆ VỚI CẤU TRÚC SAU (KHÔNG KÈM THEO BẤT KỲ VĂN BẢN NGOÀI NÀO):\n" +
                "{\n" +
                "  \"message\": \"Lời phản hồi tư vấn thân thiện, ân cần và giải thích sự kết hợp món ăn cho thực khách\",\n" +
                "  \"recommendations\": [\n" +
                "    {\n" +
                "      \"menuItemId\": 1,\n" +
                "      \"reason\": \"Giải thích ngắn gọn, cuốn hút lý do món ăn này phù hợp với yêu cầu\"\n" +
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
        sb.append("THÔNG TIN TRÒ CHUYỆN & YÊU CẦU CỦA KHÁCH HÀNG:\n");

        if (request.getEffectiveMessage() != null && !request.getEffectiveMessage().trim().isEmpty()) {
            sb.append("- Tin nhắn / Câu hỏi của khách: \"").append(request.getEffectiveMessage().trim()).append("\"\n");
        }
        if (request.getPreferences() != null && !request.getPreferences().trim().isEmpty()) {
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
