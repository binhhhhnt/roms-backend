package com.roms.dto.request;

import com.roms.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionWriteRequest {

    @NotBlank(message = "Tên chương trình khuyến mãi không được để trống")
    @Size(max = 255, message = "Tên chương trình không được vượt quá 255 ký tự")
    private String name;

    @NotBlank(message = "Mã khuyến mãi không được để trống")
    @Size(max = 50, message = "Mã khuyến mãi không được vượt quá 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã khuyến mãi chỉ được chứa chữ cái, số, dấu gạch nối (-) hoặc gạch dưới (_)")
    private String code;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @Positive(message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    @Positive(message = "Số lượt dùng tối đa phải lớn hơn 0")
    private Integer maxUses;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isActive = true;
}

