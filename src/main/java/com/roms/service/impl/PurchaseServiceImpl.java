package com.roms.service.impl;

import com.roms.dto.request.CreatePurchaseRequest;
import com.roms.dto.request.PurchaseItemRequest;
import com.roms.dto.request.UpdatePurchaseItemRequest;
import com.roms.dto.response.PurchaseDetailResponse;
import com.roms.dto.response.PurchaseResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.Purchase;
import com.roms.entity.PurchaseItem;
import com.roms.entity.User;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.PurchaseMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.PurchaseItemRepository;
import com.roms.repository.PurchaseRepository;
import com.roms.repository.UserRepository;
import com.roms.service.PurchaseService;
import com.roms.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;
    private final StockMovementService stockMovementService;
    private final PurchaseMapper purchaseMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseResponse> getPurchases(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Purchase> page;
        if (fromDate != null && toDate != null) {
            page = purchaseRepository.findByPurchaseDateBetween(fromDate, toDate, pageable);
        } else if (fromDate != null) {
            page = purchaseRepository.findByPurchaseDateGreaterThanEqual(fromDate, pageable);
        } else if (toDate != null) {
            page = purchaseRepository.findByPurchaseDateLessThanEqual(toDate, pageable);
        } else {
            page = purchaseRepository.findAllByOrderByPurchaseDateDesc(pageable);
        }
        return page.map(purchaseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseDetailResponse getPurchaseById(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with id: " + id));
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByIdAsc(id);
        return purchaseMapper.toDetailResponse(purchase, items);
    }

    @Override
    @Transactional
    public PurchaseDetailResponse createPurchase(CreatePurchaseRequest request, Long userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        }

        Purchase purchase = Purchase.builder()
                .purchaseDate(request.getPurchaseDate())
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .createdBy(user)
                .build();
        Purchase savedPurchase = purchaseRepository.save(purchase);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseItem> savedItems = new ArrayList<>();

        for (PurchaseItemRequest itemReq : request.getItems()) {
            Ingredient ingredient = ingredientRepository.findByIdWithPessimisticLock(itemReq.getIngredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + itemReq.getIngredientId()));

            BigDecimal subtotal = itemReq.getQuantity().multiply(itemReq.getUnitPrice());
            totalAmount = totalAmount.add(subtotal);

            // Increase stock
            BigDecimal currentStock = ingredient.getCurrentStock() != null
                    ? ingredient.getCurrentStock()
                    : BigDecimal.ZERO;
            ingredient.setCurrentStock(currentStock.add(itemReq.getQuantity()));
            ingredientRepository.save(ingredient);

            PurchaseItem purchaseItem = PurchaseItem.builder()
                    .purchase(savedPurchase)
                    .ingredient(ingredient)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .subtotal(subtotal)
                    .build();
            PurchaseItem savedItem = purchaseItemRepository.save(purchaseItem);
            savedItems.add(savedItem);

            // Record append-only stock movement
            stockMovementService.recordPurchase(
                    ingredient,
                    itemReq.getQuantity(),
                    savedItem,
                    "Nhập hàng theo phiếu #" + savedPurchase.getId()
            );
        }

        savedPurchase.setTotalAmount(totalAmount);
        purchaseRepository.save(savedPurchase);

        log.info("Created purchase id={} with {} items, totalAmount={}",
                savedPurchase.getId(), savedItems.size(), totalAmount);
        return purchaseMapper.toDetailResponse(savedPurchase, savedItems);
    }

    @Override
    @Transactional
    public PurchaseDetailResponse correctPurchaseItem(Long purchaseId, Long itemId, UpdatePurchaseItemRequest request) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with id: " + purchaseId));

        PurchaseItem item = purchaseItemRepository.findByIdAndPurchaseId(itemId, purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase item not found with id: " + itemId + " in purchase: " + purchaseId));

        Ingredient ingredient = ingredientRepository.findByIdWithPessimisticLock(item.getIngredient().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + item.getIngredient().getId()));

        BigDecimal oldQuantity = item.getQuantity();
        BigDecimal oldSubtotal = item.getSubtotal();
        BigDecimal newQuantity = request.getQuantity();
        BigDecimal newUnitPrice = request.getUnitPrice();
        BigDecimal deltaQuantity = newQuantity.subtract(oldQuantity);

        // Adjust stock balance
        BigDecimal currentStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock()
                : BigDecimal.ZERO;
        BigDecimal newStock = currentStock.add(deltaQuantity);

        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "Cannot decrease purchase quantity. Current stock (" + currentStock + ") is insufficient to deduct " + deltaQuantity.abs());
        }

        ingredient.setCurrentStock(newStock);
        ingredientRepository.save(ingredient);

        // Update purchase item
        BigDecimal newSubtotal = newQuantity.multiply(newUnitPrice);
        BigDecimal deltaSubtotal = newSubtotal.subtract(oldSubtotal);

        item.setQuantity(newQuantity);
        item.setUnitPrice(newUnitPrice);
        item.setSubtotal(newSubtotal);
        purchaseItemRepository.save(item);

        // Update purchase total amount
        purchase.setTotalAmount(purchase.getTotalAmount().add(deltaSubtotal));
        purchaseRepository.save(purchase);

        // Record compensating movement if quantity changed
        if (deltaQuantity.compareTo(BigDecimal.ZERO) != 0) {
            String note = (request.getReason() != null && !request.getReason().trim().isEmpty())
                    ? "Điều chỉnh phiếu #" + purchaseId + ": " + request.getReason().trim()
                    : "Điều chỉnh chi tiết phiếu nhập #" + purchaseId;

            stockMovementService.recordPurchase(ingredient, deltaQuantity, item, note);
        }

        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByIdAsc(purchaseId);
        log.info("Corrected purchase item id={} in purchase id={}, deltaQty={}, deltaSubtotal={}",
                itemId, purchaseId, deltaQuantity, deltaSubtotal);
        return purchaseMapper.toDetailResponse(purchase, items);
    }
}
