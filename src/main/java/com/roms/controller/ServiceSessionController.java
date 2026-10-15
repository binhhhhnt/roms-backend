package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.TransferTableRequest;
import com.roms.dto.response.ServiceSessionResponse;
import com.roms.dto.response.TableTransferHistoryResponse;
import com.roms.enums.ServiceSessionStatus;
import com.roms.service.ServiceSessionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý Phiên phục vụ (Service Session) và Chuyển bàn (Table Transfer).
 * Cung cấp các API:
 * - Mở phiên phục vụ tại bàn (bởi nhân viên hoặc tablet chính bàn đó).
 * - Đóng phiên phục vụ (kiểm tra không còn đơn hàng pending/cooking).
 * - Xem chi tiết phiên phục vụ và lịch sử các phiên.
 * - Chuyển phiên phục vụ sang bàn trống khác.
 * - Xem lịch sử các lần chuyển bàn của phiên.
 */
@RestController
@RequestMapping("/api/sessions")
public class ServiceSessionController {

    private final ServiceSessionService sessionService;

    public ServiceSessionController(ServiceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Mở một phiên phục vụ mới tại bàn ăn chỉ định.
     * Quyền truy cập: OWNER, SERVICE_STAFF, TABLE_ACCOUNT (chỉ mở cho chính bàn được gán)
     *
     * @param tableId Mã ID của bàn ăn cần mở phiên
     * @return ResponseEntity bọc ApiResponse chứa thông tin phiên vừa mở với HTTP status 201 Created
     */
    @PostMapping("/tables/{tableId}/open")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<ServiceSessionResponse>> openSession(@PathVariable Long tableId) {
        ServiceSessionResponse session = sessionService.openSession(tableId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service session opened successfully", session));
    }

    /**
     * Đóng phiên phục vụ hiện tại.
     * Quy tắc: Không được đóng nếu vẫn còn đơn hàng PENDING hoặc COOKING.
     * Quyền truy cập: OWNER, SERVICE_STAFF, TABLE_ACCOUNT (chỉ đóng cho chính bàn được gán)
     *
     * @param sessionId Mã ID của phiên phục vụ cần đóng
     * @return ResponseEntity bọc ApiResponse chứa thông tin phiên sau khi đóng
     */
    @PostMapping("/{sessionId}/close")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<ServiceSessionResponse>> closeSession(@PathVariable Long sessionId) {
        ServiceSessionResponse session = sessionService.closeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Service session closed successfully", session));
    }

    /**
     * Lấy thông tin chi tiết của một phiên phục vụ theo ID.
     * Quyền truy cập: OWNER, SERVICE_STAFF, TABLE_ACCOUNT
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @return ResponseEntity bọc ApiResponse chứa thông tin chi tiết phiên
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<ServiceSessionResponse>> getSessionById(@PathVariable Long sessionId) {
        ServiceSessionResponse session = sessionService.getSessionById(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Service session retrieved successfully", session));
    }

    /**
     * Lấy danh sách phiên phục vụ có hỗ trợ lọc theo bàn, trạng thái và phân trang.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @param tableId Mã ID của bàn ăn (tùy chọn)
     * @param status  Trạng thái phiên phục vụ (ACTIVE / CLOSED, tùy chọn)
     * @param page    Số trang (mặc định: 0)
     * @param size    Số phần tử mỗi trang (mặc định: 20, tối đa: 100)
     * @param sort    Tiêu chí sắp xếp (mặc định: "openedAt,desc")
     * @return ResponseEntity bọc ApiResponse chứa PageResponse danh sách phiên
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<ServiceSessionResponse>>> getSessions(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) ServiceSessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "openedAt,desc") String[] sort) {

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "openedAt");
        if (sort.length >= 2) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sort[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortOrder = Sort.by(direction, sort[0]);
        } else if (sort.length == 1) {
            sortOrder = Sort.by(Sort.Direction.ASC, sort[0]);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), sortOrder);
        PageResponse<ServiceSessionResponse> sessions = sessionService.getSessions(tableId, status, pageable);

        return ResponseEntity.ok(ApiResponse.success("Service sessions retrieved successfully", sessions));
    }

    /**
     * Chuyển một phiên phục vụ đang hoạt động sang một bàn trống khác.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @param sessionId Mã ID của phiên phục vụ đang chuyển
     * @param request   DTO chứa toTableId (ID bàn đích)
     * @return ResponseEntity bọc ApiResponse chứa thông tin phiên sau khi đổi bàn
     */
    @PostMapping("/{sessionId}/transfer")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<ServiceSessionResponse>> transferTable(
            @PathVariable Long sessionId,
            @Valid @RequestBody TransferTableRequest request) {
        ServiceSessionResponse session = sessionService.transferTable(sessionId, request);
        return ResponseEntity.ok(ApiResponse.success("Table transferred successfully", session));
    }

    /**
     * Lấy lịch sử chuyển bàn của một phiên phục vụ cụ thể.
     * Quyền truy cập: OWNER, SERVICE_STAFF
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @param page      Số trang (mặc định: 0)
     * @param size      Số bản ghi mỗi trang (mặc định: 20)
     * @return ResponseEntity bọc ApiResponse chứa danh sách lịch sử chuyển bàn phân trang
     */
    @GetMapping("/{sessionId}/transfers")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<TableTransferHistoryResponse>>> getTransferHistory(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "transferredAt"));
        PageResponse<TableTransferHistoryResponse> history = sessionService.getTransferHistory(sessionId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Transfer history retrieved successfully", history));
    }
}
