package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.CreateTableRequest;
import com.roms.dto.request.UpdateTableRequest;
import com.roms.dto.request.UpdateTableStatusRequest;
import com.roms.dto.response.TableResponse;
import com.roms.service.TableService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý bàn ăn (Restaurant Tables).
 * Cung cấp các API:
 * - Tra cứu danh sách bàn, trạng thái thời gian thực (EMPTY, OCCUPIED, OUT_OF_SERVICE).
 * - Xem chi tiết bàn ăn.
 * - Thêm, sửa thông tin bàn ăn (chỉ OWNER).
 * - Bật/tắt trạng thái bàn ăn (chỉ OWNER).
 * - Lấy danh sách các khu vực bàn ăn để hiển thị bộ lọc.
 */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    /**
     * Lấy danh sách bàn ăn có hỗ trợ bộ lọc và phân trang.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @param isActive Bộ lọc theo trạng thái hoạt động (true/false, tùy chọn)
     * @param area     Bộ lọc theo khu vực (tùy chọn)
     * @param search   Từ khóa tìm kiếm theo mã bàn (tùy chọn)
     * @param page     Số trang cần lấy (mặc định: 0)
     * @param size     Số bản ghi mỗi trang (mặc định: 20, tối đa: 100)
     * @param sort     Tiêu chí sắp xếp (mặc định: "tableCode,asc")
     * @return ResponseEntity bọc ApiResponse chứa PageResponse danh sách bàn
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<TableResponse>>> getTables(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "tableCode,asc") String[] sort) {

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "tableCode");
        if (sort.length >= 2) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sort[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sortOrder = Sort.by(direction, sort[0]);
        } else if (sort.length == 1) {
            sortOrder = Sort.by(Sort.Direction.ASC, sort[0]);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), sortOrder);
        PageResponse<TableResponse> tables = tableService.getTables(isActive, area, search, pageable);

        return ResponseEntity.ok(ApiResponse.success("Tables retrieved successfully", tables));
    }

    /**
     * Lấy chi tiết thông tin một bàn ăn theo ID.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @param id Mã ID của bàn ăn
     * @return ResponseEntity bọc ApiResponse chứa thông tin bàn ăn
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<TableResponse>> getTableById(@PathVariable Long id) {
        TableResponse table = tableService.getTableById(id);
        return ResponseEntity.ok(ApiResponse.success("Table retrieved successfully", table));
    }

    /**
     * Thêm bàn ăn mới vào hệ thống.
     * Quyền truy cập: Chỉ OWNER
     *
     * @param request DTO chứa thông tin bàn mới (tableCode, capacity, area)
     * @return ResponseEntity bọc ApiResponse chứa thông tin bàn vừa tạo với HTTP status 201 Created
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<TableResponse>> createTable(@Valid @RequestBody CreateTableRequest request) {
        TableResponse table = tableService.createTable(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Table created successfully", table));
    }

    /**
     * Cập nhật thông tin bàn ăn (mã bàn, số chỗ ngồi, khu vực).
     * Quyền truy cập: Chỉ OWNER
     *
     * @param id      Mã ID của bàn ăn cần sửa
     * @param request DTO chứa thông tin cập nhật
     * @return ResponseEntity bọc ApiResponse chứa thông tin bàn sau cập nhật
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<TableResponse>> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request) {
        TableResponse table = tableService.updateTable(id, request);
        return ResponseEntity.ok(ApiResponse.success("Table updated successfully", table));
    }

    /**
     * Cập nhật trạng thái hoạt động/bảo trì của bàn ăn (isActive).
     * Quyền truy cập: Chỉ OWNER
     *
     * @param id      Mã ID của bàn ăn
     * @param request DTO chứa trạng thái mới (isActive = true/false)
     * @return ResponseEntity bọc ApiResponse chứa thông tin bàn sau cập nhật
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<TableResponse>> updateTableStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableStatusRequest request) {
        TableResponse table = tableService.updateTableStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Table status updated successfully", table));
    }

    /**
     * Lấy danh sách các tên khu vực phục vụ trong nhà hàng.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @return ResponseEntity bọc ApiResponse chứa danh sách tên các khu vực
     */
    @GetMapping("/areas")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<List<String>>> getAreas() {
        List<String> areas = tableService.getDistinctAreas();
        return ResponseEntity.ok(ApiResponse.success("Areas retrieved successfully", areas));
    }
}
