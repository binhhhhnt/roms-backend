package com.roms.service.impl;

import com.roms.dto.request.CreateInventoryCheckRequest;
import com.roms.dto.request.InventoryCheckItemRequest;
import com.roms.dto.response.InventoryCheckResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.InventoryCheck;
import com.roms.entity.InventoryCheckItem;
import com.roms.entity.User;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.InventoryCheckMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.InventoryCheckItemRepository;
import com.roms.repository.InventoryCheckRepository;
import com.roms.repository.UserRepository;
import com.roms.service.InventoryCheckService;
import com.roms.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCheckServiceImpl implements InventoryCheckService {

    private final InventoryCheckRepository inventoryCheckRepository;
    private final InventoryCheckItemRepository inventoryCheckItemRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;
    private final StockMovementService stockMovementService;
    private final InventoryCheckMapper inventoryCheckMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryCheckResponse> getInventoryChecks(Pageable pageable) {
        Page<InventoryCheck> page = inventoryCheckRepository.findAllByOrderByCheckedAtDesc(pageable);
        return page.map(check -> {
            List<InventoryCheckItem> items = inventoryCheckItemRepository
                    .findByInventoryCheckIdOrderByIdAsc(check.getId());
            return inventoryCheckMapper.toResponse(check, items);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryCheckResponse getInventoryCheckById(Long id) {
        InventoryCheck check = inventoryCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory check not found with id: " + id));
        List<InventoryCheckItem> items = inventoryCheckItemRepository
                .findByInventoryCheckIdOrderByIdAsc(check.getId());
        return inventoryCheckMapper.toResponse(check, items);
    }

    @Override
    @Transactional
    public InventoryCheckResponse createInventoryCheck(CreateInventoryCheckRequest request, Long userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        }

        LocalDateTime checkedAt = request.getCheckedAt() != null
                ? request.getCheckedAt()
                : LocalDateTime.now();

        InventoryCheck check = InventoryCheck.builder()
                .checkedBy(user)
                .checkedAt(checkedAt)
                .note(request.getNote())
                .build();
        InventoryCheck savedCheck = inventoryCheckRepository.save(check);

        List<InventoryCheckItem> savedItems = new ArrayList<>();

        List<InventoryCheckItemRequest> sortedItemRequests = request.getItems() != null
                ? request.getItems().stream()
                        .sorted(Comparator.comparing(InventoryCheckItemRequest::getIngredientId))
                        .toList()
                : Collections.emptyList();

        for (InventoryCheckItemRequest itemReq : sortedItemRequests) {
            // Lock ingredient for concurrent balance protection (ordered by ingredientId ASC to prevent deadlocks)
            Ingredient ingredient = ingredientRepository.findByIdWithPessimisticLock(itemReq.getIngredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + itemReq.getIngredientId()));

            BigDecimal systemQuantity = ingredient.getCurrentStock() != null
                    ? ingredient.getCurrentStock()
                    : BigDecimal.ZERO;
            BigDecimal actualQuantity = itemReq.getActualQuantity();
            BigDecimal difference = actualQuantity.subtract(systemQuantity);

            InventoryCheckItem checkItem = InventoryCheckItem.builder()
                    .inventoryCheck(savedCheck)
                    .ingredient(ingredient)
                    .systemQuantity(systemQuantity)
                    .actualQuantity(actualQuantity)
                    .difference(difference)
                    .reason(itemReq.getReason())
                    .build();
            InventoryCheckItem savedItem = inventoryCheckItemRepository.save(checkItem);
            savedItems.add(savedItem);

            // If discrepancy detected, update current stock and append traceable movement
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                ingredient.setCurrentStock(actualQuantity);
                ingredientRepository.save(ingredient);

                String reasonText = (itemReq.getReason() != null && !itemReq.getReason().trim().isEmpty())
                        ? " (" + itemReq.getReason().trim() + ")"
                        : "";
                String checkMovementNote = "Kiểm kê #" + savedCheck.getId() + reasonText + " • Bởi: "
                        + (user != null ? user.getUsername() + " (#" + user.getId() + ")" : "Hệ thống");

                stockMovementService.recordInventoryAdjustment(
                        ingredient,
                        difference,
                        savedItem,
                        checkMovementNote
                );
            }
        }

        log.info("Created inventory check id={} with {} items by userId={}",
                savedCheck.getId(), savedItems.size(), userId);
        return inventoryCheckMapper.toResponse(savedCheck, savedItems);
    }
}
