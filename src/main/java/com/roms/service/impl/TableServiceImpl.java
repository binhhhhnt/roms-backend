package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.request.CreateTableRequest;
import com.roms.dto.request.GenerateTableQrRequest;
import com.roms.dto.request.UpdateTableRequest;
import com.roms.dto.request.UpdateTableStatusRequest;
import com.roms.dto.response.GenerateTableQrResponse;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.dto.response.TableResponse;
import com.roms.entity.Area;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TableMapper;
import com.roms.repository.AreaRepository;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.service.RestaurantSettingService;
import com.roms.service.TableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final AreaRepository areaRepository;
    private final ServiceSessionRepository sessionRepository;
    private final TableMapper tableMapper;
    private final RestaurantSettingService restaurantSettingService;

    public TableServiceImpl(RestaurantTableRepository tableRepository,
                            AreaRepository areaRepository,
                            ServiceSessionRepository sessionRepository,
                            TableMapper tableMapper,
                            RestaurantSettingService restaurantSettingService) {
        this.tableRepository = tableRepository;
        this.areaRepository = areaRepository;
        this.sessionRepository = sessionRepository;
        this.tableMapper = tableMapper;
        this.restaurantSettingService = restaurantSettingService;
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
    public PageResponse<TableResponse> getTables(Boolean isActive, Long areaId, String search, Pageable pageable) {
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<RestaurantTable> tablePage = tableRepository.findWithFilters(isActive, areaId, trimmedSearch, pageable);

        return PageResponse.from(tablePage, table -> {
            ServiceSession activeSession = sessionRepository
                    .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                    .orElse(null);
            return tableMapper.toResponseWithStatus(table, activeSession);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        RestaurantTable table = tableRepository.findWithAreaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        ServiceSession activeSession = sessionRepository
                .findByTableIdAndStatus(table.getId(), ServiceSessionStatus.ACTIVE)
                .orElse(null);

        return tableMapper.toResponseWithStatus(table, activeSession);
    }

    @Override
    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        // Lock Area first (Area -> Table ordering) to prevent race condition with area deactivation.
        Area area = areaRepository.findByIdForUpdate(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found with id: " + request.getAreaId()));

        if (!Boolean.TRUE.equals(area.getIsActive())) {
            throw new BusinessRuleException("Cannot assign table to an inactive area: " + area.getName(), "AREA_INACTIVE");
        }

        String tableCode = request.getTableCode().trim();
        if (tableRepository.existsByTableCode(tableCode)) {
            throw new BusinessRuleException("Table code is already in use: " + tableCode, "DUPLICATE_TABLE_CODE");
        }

        RestaurantTable table = RestaurantTable.builder()
                .tableCode(tableCode)
                .capacity(request.getCapacity())
                .area(area)
                .isActive(true)
                .build();

        RestaurantTable savedTable = tableRepository.save(table);
        log.info("Created table id: {}, code: {}, areaId: {}", savedTable.getId(), savedTable.getTableCode(), area.getId());

        return tableMapper.toResponseWithStatus(savedTable, null);
    }

    @Override
    @Transactional
    public TableResponse updateTable(Long id, UpdateTableRequest request) {
        // Lock Area before loading/modifying Table to keep consistent lock order (Area -> Table).
        Area targetArea = areaRepository.findByIdForUpdate(request.getAreaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found with id: " + request.getAreaId()));

        RestaurantTable table = tableRepository.findWithAreaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        boolean isChangingArea = table.getArea() == null || !targetArea.getId().equals(table.getArea().getId());
        if (isChangingArea && !Boolean.TRUE.equals(targetArea.getIsActive())) {
            throw new BusinessRuleException("Cannot assign table to an inactive area: " + targetArea.getName(), "AREA_INACTIVE");
        }

        String newTableCode = request.getTableCode().trim();
        if (!newTableCode.equals(table.getTableCode()) && tableRepository.existsByTableCode(newTableCode)) {
            throw new BusinessRuleException("Table code is already in use: " + newTableCode, "DUPLICATE_TABLE_CODE");
        }

        table.setTableCode(newTableCode);
        table.setCapacity(request.getCapacity());
        table.setArea(targetArea);

        RestaurantTable updatedTable = tableRepository.save(table);
        log.info("Updated table id: {}, code: {}, areaId: {}", updatedTable.getId(), updatedTable.getTableCode(), targetArea.getId());

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

    @Override
    @Transactional
    public GenerateTableQrResponse generateQrTokens(GenerateTableQrRequest request) {
        Set<Long> uniqueIds = new LinkedHashSet<>(request.getTableIds());
        List<RestaurantTable> tables = tableRepository.findByIdInOrderByIdAsc(uniqueIds);
        if (tables.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more tables were not found");
        }

        boolean regenerate = Boolean.TRUE.equals(request.getRegenerate());
        for (RestaurantTable table : tables) {
            if (regenerate || table.getQrToken() == null || table.getQrToken().isBlank()) {
                table.setQrToken(UUID.randomUUID().toString());
            }
        }
        List<RestaurantTable> saved = tableRepository.saveAll(tables);
        RestaurantSettingResponse settings = restaurantSettingService.getSettings();

        List<GenerateTableQrResponse.TableQrItem> items = saved.stream()
                .map(t -> GenerateTableQrResponse.TableQrItem.builder()
                        .tableId(t.getId())
                        .tableCode(t.getTableCode())
                        .area(t.getArea() != null ? t.getArea().getName() : null)
                        .qrToken(t.getQrToken())
                        .build())
                .toList();

        return GenerateTableQrResponse.builder()
                .restaurantName(settings.getRestaurantName())
                .address(settings.getAddress())
                .tables(items)
                .build();
    }
}
