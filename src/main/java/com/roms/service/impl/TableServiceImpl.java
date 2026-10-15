package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.request.CreateTableRequest;
import com.roms.dto.request.UpdateTableRequest;
import com.roms.dto.request.UpdateTableStatusRequest;
import com.roms.dto.response.TableResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TableMapper;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.service.TableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service triển khai nghiệp vụ Quản lý Bàn ăn (Restaurant Table).
 * Hỗ trợ các chức năng:
 * - Tra cứu danh sách bàn kèm trạng thái thời gian thực (realtime status: EMPTY, OCCUPIED, OUT_OF_SERVICE).
 * - Xem chi tiết bàn ăn và phiên phục vụ đang hoạt động.
 * - Thêm bàn mới, cập nhật thông tin bàn ăn.
 * - Khóa/mở khóa bàn (ngăn chặn khóa bàn đang có khách ngồi).
 * - Lấy danh mục các khu vực phục vụ trong nhà hàng.
 */
@Slf4j
@Service
public class TableServiceImpl implements TableService {

    private final RestaurantTableRepository tableRepository;
    private final ServiceSessionRepository sessionRepository;
    private final TableMapper tableMapper;

    /**
     * Khởi tạo TableServiceImpl với các dependency cần thiết.
     *
     * @param tableRepository   Repository thao tác dữ liệu bảng restaurant_tables
     * @param sessionRepository Repository truy vấn phiên phục vụ của bàn
     * @param tableMapper       Mapper chuyển đổi giữa entity và DTO
     */
    public TableServiceImpl(RestaurantTableRepository tableRepository,
                            ServiceSessionRepository sessionRepository,
                            TableMapper tableMapper) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.tableMapper = tableMapper;
    }

    /**
     * Lấy danh sách bàn ăn có phân trang kết hợp bộ lọc theo trạng thái hoạt động, khu vực và từ khóa mã bàn.
     * Trạng thái thời gian thực (realtimeStatus) được tính toán dựa trên phiên ACTIVE tương ứng.
     *
     * @param isActive Trạng thái hoạt động của bàn (true/false, tùy chọn)
     * @param area     Tên khu vực (tùy chọn)
     * @param search   Từ khóa tìm kiếm theo mã bàn (tùy chọn)
     * @param pageable Thông tin phân trang và sắp xếp
     * @return PageResponse<TableResponse> Danh sách phân trang bàn ăn kèm trạng thái thực tế
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TableResponse> getTables(Boolean isActive, String area, String search, Pageable pageable) {
        // Chuẩn hóa chuỗi tìm kiếm và khu vực, loại bỏ khoảng trắng thừa
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String trimmedArea = (area != null && !area.trim().isEmpty()) ? area.trim() : null;

        // Truy vấn phân trang từ cơ sở dữ liệu
        Page<RestaurantTable> tablePage = tableRepository.findWithFilters(isActive, trimmedArea, trimmedSearch, pageable);

        // Ánh xạ sang DTO kèm kiểm tra phiên phục vụ ACTIVE hiện tại
        return PageResponse.from(tablePage, table -> {
            ServiceSession activeSession = sessionRepository
                    .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                    .orElse(null);
            return tableMapper.toResponseWithStatus(table, activeSession);
        });
    }

    /**
     * Lấy thông tin chi tiết một bàn ăn theo ID, kèm trạng thái thời gian thực và ID phiên ACTIVE (nếu có).
     *
     * @param id Mã ID của bàn ăn
     * @return TableResponse Thông tin bàn ăn
     * @throws ResourceNotFoundException Nếu không tìm thấy bàn ăn theo ID
     */
    @Override
    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        // Tìm kiếm bàn ăn theo ID
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        // Kiểm tra bàn có phiên phục vụ nào đang ACTIVE không
        ServiceSession activeSession = sessionRepository
                .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                .orElse(null);

        return tableMapper.toResponseWithStatus(table, activeSession);
    }

    /**
     * Tạo bàn ăn mới trong nhà hàng.
     * Mã bàn (tableCode) phải là duy nhất, không được trùng lặp.
     * Mặc định bàn mới tạo sẽ ở trạng thái hoạt động (isActive = true).
     *
     * @param request DTO chứa thông tin tạo bàn (tableCode, capacity, area)
     * @return TableResponse Thông tin bàn ăn vừa tạo
     * @throws BusinessRuleException Nếu mã bàn đã tồn tại trong hệ thống
     */
    @Override
    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        String tableCode = request.getTableCode().trim();

        // Kiểm tra trùng lặp mã bàn
        if (tableRepository.existsByTableCode(tableCode)) {
            throw new BusinessRuleException("Table code is already in use: " + tableCode, "DUPLICATE_TABLE_CODE");
        }

        // Tạo thực thể RestaurantTable mới
        RestaurantTable table = RestaurantTable.builder()
                .tableCode(tableCode)
                .capacity(request.getCapacity())
                .area(request.getArea() != null ? request.getArea().trim() : null)
                .isActive(true)
                .build();

        RestaurantTable savedTable = tableRepository.save(table);
        log.info("Created table id: {}, code: {}", savedTable.getId(), savedTable.getTableCode());

        return tableMapper.toResponseWithStatus(savedTable, null);
    }

    /**
     * Cập nhật thông tin bàn ăn (mã bàn, số chỗ ngồi, khu vực).
     * Nếu đổi mã bàn mới, phải đảm bảo mã bàn mới không trùng với các bàn khác.
     *
     * @param id      Mã ID của bàn ăn cần sửa
     * @param request Thông tin cập nhật mới
     * @return TableResponse Bàn ăn sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy bàn ăn
     * @throws BusinessRuleException Nếu mã bàn mới trùng với bàn khác
     */
    @Override
    @Transactional
    public TableResponse updateTable(Long id, UpdateTableRequest request) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        String newTableCode = request.getTableCode().trim();
        // Kiểm tra xem mã bàn mới có bị trùng với bàn khác không
        if (!newTableCode.equals(table.getTableCode()) && tableRepository.existsByTableCode(newTableCode)) {
            throw new BusinessRuleException("Table code is already in use: " + newTableCode, "DUPLICATE_TABLE_CODE");
        }

        table.setTableCode(newTableCode);
        table.setCapacity(request.getCapacity());
        table.setArea(request.getArea() != null ? request.getArea().trim() : null);

        RestaurantTable updatedTable = tableRepository.save(table);
        log.info("Updated table id: {}, code: {}", updatedTable.getId(), updatedTable.getTableCode());

        // Lấy lại trạng thái phiên ACTIVE hiện tại để trả về thông tin đầy đủ
        ServiceSession activeSession = sessionRepository
                .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                .orElse(null);

        return tableMapper.toResponseWithStatus(updatedTable, activeSession);
    }

    /**
     * Thay đổi trạng thái hoạt động/bảo trì của bàn ăn.
     * Quy tắc nghiệp vụ quan trọng: Không được phép vô hiệu hóa bàn khi bàn đó đang có khách (đang có phiên ACTIVE).
     *
     * @param id      Mã ID của bàn ăn
     * @param request Yêu cầu cập nhật trạng thái (isActive = true/false)
     * @return TableResponse Bàn ăn sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy bàn
     * @throws BusinessRuleException Nếu cố gắng vô hiệu hóa bàn đang có phiên phục vụ ACTIVE
     */
    @Override
    @Transactional
    public TableResponse updateTableStatus(Long id, UpdateTableStatusRequest request) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        // Ràng buộc nghiệp vụ: Không cho phép vô hiệu hóa bàn đang có phiên ACTIVE
        if (Boolean.FALSE.equals(request.getIsActive())) {
            boolean hasActiveSession = sessionRepository.existsByTableIdAndStatus(id, ServiceSessionStatus.ACTIVE);
            if (hasActiveSession) {
                throw new BusinessRuleException(
                        "Cannot deactivate table " + table.getTableCode() + " while it has an active service session",
                        "TABLE_HAS_ACTIVE_SESSION");
            }
        }

        table.setIsActive(request.getIsActive());
        RestaurantTable savedTable = tableRepository.save(table);

        if (Boolean.FALSE.equals(request.getIsActive())) {
            log.info("Deactivated table id: {}, code: {}", id, table.getTableCode());
        } else {
            log.info("Activated table id: {}, code: {}", id, table.getTableCode());
        }

        ServiceSession activeSession = sessionRepository
                .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                .orElse(null);

        return tableMapper.toResponseWithStatus(savedTable, activeSession);
    }

    /**
     * Lấy danh sách các khu vực (area) không trùng lặp hiện có trong nhà hàng.
     * Thường dùng để đổ dữ liệu vào dropdown lọc khu vực trên giao diện người dùng.
     *
     * @return List<String> Danh sách tên các khu vực
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctAreas() {
        return tableRepository.findDistinctAreas();
    }
}
