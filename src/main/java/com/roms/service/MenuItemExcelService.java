package com.roms.service;

import com.roms.dto.response.ExcelImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface MenuItemExcelService {

    /**
     * Tạo file mẫu Excel (.xlsx) để nhập thực đơn món ăn.
     *
     * @return mảng byte của file Excel
     */
    byte[] generateTemplate();

    /**
     * Xuất toàn bộ danh mục thực đơn món ăn hiện có ra file Excel (.xlsx).
     *
     * @return mảng byte của file Excel
     */
    byte[] exportMenuItems();

    /**
     * Nhập danh sách món ăn từ file Excel (.xlsx).
     * Cập nhật thông tin nếu trùng tên món, tự động bổ sung danh mục và thuế suất nếu chưa có.
     *
     * @param file file Excel upload
     * @return kết quả import tổng hợp (số lượng thêm mới, cập nhật, lỗi)
     */
    ExcelImportResultResponse importMenuItems(MultipartFile file);
}

