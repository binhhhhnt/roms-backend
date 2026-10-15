package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.TransferTableRequest;
import com.roms.dto.response.ServiceSessionResponse;
import com.roms.dto.response.TableTransferHistoryResponse;
import com.roms.enums.ServiceSessionStatus;
import org.springframework.data.domain.Pageable;

/**
 * Interface định nghĩa các dịch vụ nghiệp vụ liên quan đến Phiên phục vụ (Service Session) và Chuyển bàn ăn.
 */
public interface ServiceSessionService {

    /**
     * Mở một phiên phục vụ mới trên bàn ăn.
     *
     * @param tableId Mã ID của bàn ăn
     * @return ServiceSessionResponse Thông tin chi tiết phiên phục vụ vừa mở
     */
    ServiceSessionResponse openSession(Long tableId);

    /**
     * Đóng phiên phục vụ hiện tại của bàn ăn.
     *
     * @param sessionId Mã ID của phiên phục vụ cần đóng
     * @return ServiceSessionResponse Thông tin chi tiết phiên phục vụ sau khi đóng
     */
    ServiceSessionResponse closeSession(Long sessionId);

    /**
     * Lấy thông tin chi tiết của một phiên phục vụ theo ID.
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @return ServiceSessionResponse DTO chứa dữ liệu phiên phục vụ
     */
    ServiceSessionResponse getSessionById(Long sessionId);

    /**
     * Lấy danh sách các phiên phục vụ có hỗ trợ phân trang và lọc theo bàn/trạng thái.
     *
     * @param tableId  Mã ID của bàn ăn (tùy chọn)
     * @param status   Trạng thái phiên phục vụ (tùy chọn)
     * @param pageable Thông tin phân trang và sắp xếp
     * @return PageResponse<ServiceSessionResponse> Danh sách phân trang phiên phục vụ
     */
    PageResponse<ServiceSessionResponse> getSessions(Long tableId, ServiceSessionStatus status, Pageable pageable);

    /**
     * Chuyển phiên phục vụ sang một bàn ăn khác.
     *
     * @param sessionId Mã ID của phiên phục vụ cần chuyển
     * @param request   Yêu cầu chuyển bàn chứa ID bàn đích
     * @return ServiceSessionResponse Thông tin phiên phục vụ sau khi chuyển
     */
    ServiceSessionResponse transferTable(Long sessionId, TransferTableRequest request);

    /**
     * Lấy lịch sử chuyển bàn của một phiên phục vụ cụ thể.
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @param pageable  Thông tin phân trang
     * @return PageResponse<TableTransferHistoryResponse> Danh sách phân trang lịch sử chuyển bàn
     */
    PageResponse<TableTransferHistoryResponse> getTransferHistory(Long sessionId, Pageable pageable);
}
