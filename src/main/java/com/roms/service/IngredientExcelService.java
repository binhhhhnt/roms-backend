package com.roms.service;

import com.roms.dto.response.ExcelImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IngredientExcelService {

    /**
     * Tạo file mẫu Excel (.xlsx) để nhập nguyên liệu kho.
     *
     * @return mảng byte của file Excel
     */
    byte[] generateTemplate();

    /**
     * Xuất toàn bộ danh sách nguyên liệu và tồn kho hiện có ra file Excel (.xlsx).
     *
     * @return mảng byte của file Excel
     */
    byte[] exportIngredients();

    /**
     * Nhập danh sách nguyên liệu từ file Excel (.xlsx).
     * Cập nhật thông tin nếu trùng tên nguyên liệu.
     *
     * @param file file Excel upload
     * @return kết quả import tổng hợp (số lượng thêm mới, cập nhật, lỗi)
     */
    ExcelImportResultResponse importIngredients(MultipartFile file);
}

