package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CreateTableRequest;
import com.roms.dto.request.UpdateTableRequest;
import com.roms.dto.request.UpdateTableStatusRequest;
import com.roms.dto.response.TableResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface định nghĩa các dịch vụ nghiệp vụ liên quan đến Quản lý Bàn ăn (Restaurant Table).
 */
public interface TableService {

    /**
     * Lấy danh sách bàn ăn có phân trang kèm các bộ lọc trạng thái, khu vực, tìm kiếm mã bàn.
     * Trạng thái thời gian thực (realtimeStatus) được tính toán động (EMPTY, OCCUPIED, OUT_OF_SERVICE).
     *
     * @param isActive Trạng thái hoạt động (true/false, tùy chọn)
     * @param area     Tên khu vực (tùy chọn)
     * @param search   Từ khóa tìm kiếm theo mã bàn (tùy chọn)
     * @param pageable Thông tin phân trang và sắp xếp
     * @return PageResponse<TableResponse> Danh sách phân trang bàn ăn
     */
    PageResponse<TableResponse> getTables(Boolean isActive, String area, String search, Pageable pageable);

    /**
     * Lấy thông tin chi tiết của một bàn ăn theo ID, kèm trạng thái thời gian thực và ID phiên đang phục vụ (nếu có).
     *
     * @param id Mã ID của bàn ăn
     * @return TableResponse Thông tin bàn ăn
     */
    TableResponse getTableById(Long id);

    /**
     * Thêm bàn ăn mới vào hệ thống nhà hàng.
     *
     * @param request Yêu cầu tạo bàn (tableCode, capacity, area)
     * @return TableResponse Bàn ăn vừa tạo
     */
    TableResponse createTable(CreateTableRequest request);

    /**
     * Cập nhật thông tin bàn ăn (mã bàn, số lượng chỗ ngồi, khu vực).
     *
     * @param id      Mã ID của bàn ăn cần sửa
     * @param request Thông tin cập nhật
     * @return TableResponse Bàn ăn sau khi cập nhật
     */
    TableResponse updateTable(Long id, UpdateTableRequest request);

    /**
     * Cập nhật trạng thái hoạt động/bảo trì của bàn (isActive).
     * Không cho phép vô hiệu hóa bàn đang có khách ngồi (phiên ACTIVE).
     *
     * @param id      Mã ID của bàn ăn
     * @param request Trạng thái hoạt động mới (true: hoạt động, false: bảo trì/ngừng phục vụ)
     * @return TableResponse Bàn ăn sau khi đổi trạng thái
     */
    TableResponse updateTableStatus(Long id, UpdateTableStatusRequest request);

    /**
     * Lấy danh sách tất cả các khu vực (area) không trùng lặp hiện có để hiển thị trên bộ lọc.
     *
     * @return List<String> Danh sách tên các khu vực
     */
    List<String> getDistinctAreas();
}
