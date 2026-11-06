package com.roms.service.impl;

import com.roms.dto.request.PromotionWriteRequest;
import com.roms.dto.response.PromotionDto;
import com.roms.dto.response.PromotionResponse;
import com.roms.entity.Promotion;
import com.roms.entity.User;
import com.roms.enums.DiscountType;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.PromotionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceImplTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PromotionServiceImpl promotionService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(1L)
                .username("owner")
                .fullName("Quản lý")
                .role(UserRole.OWNER)
                .build();

        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .username("owner")
                .role(UserRole.OWNER)
                .active(true)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPromotion_Success() {
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("Khai trương")
                .code("welcome10")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .maxUses(100)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .isActive(true)
                .build();

        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("WELCOME10")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        Promotion savedPromo = Promotion.builder()
                .id(10L)
                .name("Khai trương")
                .code("WELCOME10")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .maxUses(100)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .isActive(true)
                .isDeleted(false)
                .createdBy(currentUser)
                .build();

        when(promotionRepository.save(any(Promotion.class))).thenReturn(savedPromo);

        PromotionResponse response = promotionService.createPromotion(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("WELCOME10");
        assertThat(response.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(response.getStatusBadge()).isEqualTo("ACTIVE");
        verify(promotionRepository).save(any(Promotion.class));
    }

    @Test
    void createPromotion_DuplicateCode_ThrowsException() {
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("Trùng code")
                .code("DUPLICATE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50000))
                .build();

        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã tồn tại");

        verify(promotionRepository, never()).save(any());
    }

    @Test
    void createPromotion_PercentGreaterThan100_ThrowsException() {
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("Giảm quá lố")
                .code("OVER100")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(105))
                .build();

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vượt quá 100%");
    }

    @Test
    void createPromotion_InvalidDateRange_ThrowsException() {
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("Ngày sai")
                .code("INVALIDDATE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000))
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(1))
                .build();

        assertThatThrownBy(() -> promotionService.createPromotion(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ngày kết thúc");
    }

    @Test
    void getPromotions_BatchCountsUsage_AvoidsNPlusOne() {
        Pageable pageable = PageRequest.of(0, 10);
        Promotion p1 = Promotion.builder().id(1L).code("P1").name("Promo 1").isActive(true).discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();
        Promotion p2 = Promotion.builder().id(2L).code("P2").name("Promo 2").isActive(true).discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();

        when(promotionRepository.findAllFiltered(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(p1, p2), pageable, 2));

        // Mock batch count query - exactly one invocation for all IDs!
        List<Object[]> batchCounts = List.of(
                new Object[]{1L, 5L},
                new Object[]{2L, 12L}
        );
        when(promotionRepository.countUsedBillsByPromotionIds(anyCollection())).thenReturn(batchCounts);

        Page<PromotionResponse> result = promotionService.getPromotions(null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUsedCount()).isEqualTo(5L);
        assertThat(result.getContent().get(1).getUsedCount()).isEqualTo(12L);

        // Verify countUsedBills was NEVER called in a loop (zero N+1)
        verify(promotionRepository, never()).countUsedBills(anyLong());
        verify(promotionRepository, times(1)).countUsedBillsByPromotionIds(anyCollection());
    }

    @Test
    void getValidPromotions_FiltersOutMaxUsesExceeded() {
        LocalDate today = LocalDate.now();
        Promotion pUnlimited = Promotion.builder().id(1L).code("UNLIMITED").maxUses(null).isActive(true).discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();
        Promotion pAvailable = Promotion.builder().id(2L).code("AVAILABLE").maxUses(10).isActive(true).discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();
        Promotion pFull = Promotion.builder().id(3L).code("FULL").maxUses(5).isActive(true).discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).build();

        when(promotionRepository.findValidPromotions(today)).thenReturn(List.of(pUnlimited, pAvailable, pFull));

        List<Object[]> batchCounts = List.of(
                new Object[]{2L, 3L},
                new Object[]{3L, 5L}
        );
        when(promotionRepository.countUsedBillsByPromotionIds(anyCollection())).thenReturn(batchCounts);

        List<PromotionDto> validList = promotionService.getValidPromotions();

        assertThat(validList).hasSize(2);
        assertThat(validList.stream().map(PromotionDto::getCode)).containsExactly("UNLIMITED", "AVAILABLE");
    }

    @Test
    void deletePromotion_SoftDeletes() {
        Promotion promo = Promotion.builder().id(5L).code("DEL").isDeleted(false).build();
        when(promotionRepository.findById(5L)).thenReturn(Optional.of(promo));

        promotionService.deletePromotion(5L);

        assertThat(promo.getIsDeleted()).isTrue();
        verify(promotionRepository).save(promo);
    }

    @Test
    void toggleActive_TogglesState() {
        Promotion promo = Promotion.builder().id(5L).code("TOGGLE").isActive(true).isDeleted(false).build();
        when(promotionRepository.findById(5L)).thenReturn(Optional.of(promo));
        when(promotionRepository.save(promo)).thenReturn(promo);
        when(promotionRepository.countUsedBills(5L)).thenReturn(0L);

        PromotionResponse resp = promotionService.toggleActive(5L);

        assertThat(promo.getIsActive()).isFalse();
        verify(promotionRepository).save(promo);
    }
}

