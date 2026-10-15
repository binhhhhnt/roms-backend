package com.roms.service;

/**
 * Service quản lý việc tạo, trích xuất và lưu bộ nhớ đệm (Redis Cache)
 * ngữ cảnh thực đơn (Menu Context) phục vụ cho Trợ lý AI Gemini.
 */
public interface MenuContextService {

    /**
     * Lấy chuỗi ngữ cảnh thực đơn từ Redis Cache nếu có.
     * Trong trường hợp Cache miss (chưa có hoặc đã hết hạn), service sẽ tự động
     * truy vấn cơ sở dữ liệu PostgreSQL, tạo mới ngữ cảnh, lưu vào Redis và trả về.
     *
     * @return Chuỗi JSON biểu diễn ngữ cảnh thực đơn nhà hàng
     */
    String getOrBuildContext();

    /**
     * Làm mới (xóa) bộ nhớ đệm ngữ cảnh thực đơn trong Redis.
     * Được gọi sau khi có thay đổi về món ăn, danh mục, hoặc công thức nguyên liệu.
     */
    void invalidateContext();

    /**
     * Buộc tái tạo lại ngữ cảnh thực đơn từ PostgreSQL và ghi đè vào Redis Cache.
     *
     * @return Chuỗi JSON ngữ cảnh thực đơn mới nhất
     */
    String rebuildContext();
}
