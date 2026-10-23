package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.event.TableTransferEvent;
import com.roms.dto.request.TransferTableRequest;
import com.roms.dto.response.ServiceSessionResponse;
import com.roms.dto.response.TableTransferHistoryResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.TableTransferHistory;
import com.roms.entity.User;
import com.roms.enums.OrderStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.mapper.TableMapper;
import com.roms.repository.OrderRepository;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.TableTransferHistoryRepository;
import com.roms.repository.UserRepository;
import com.roms.security.SecurityUtils;
import com.roms.security.UserPrincipal;
import com.roms.service.ServiceSessionService;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service triển khai nghiệp vụ Quản lý Phiên phục vụ (Service Session) và Chuyển bàn (Table Transfer).
 * Đảm bảo các quy tắc nghiệp vụ cốt lõi:
 * - Mỗi bàn chỉ có tối đa 1 phiên ACTIVE tại một thời điểm (được bảo vệ cả ở tầng service và DB unique partial index).
 * - Bàn bị vô hiệu hóa (is_active = false) không được phép mở phiên hoặc chuyển đến.
 * - Tài khoản tablet (TABLE_ACCOUNT) chỉ được thao tác trên chính bàn được gán (users.table_id == tableId).
 * - Không được đóng phiên khi vẫn còn đơn hàng chưa hoàn tất (trạng thái PENDING hoặc COOKING).
 * - Khi mở phiên, đóng phiên, hoặc chuyển bàn, sự kiện STOMP WebSocket được phát sau khi giao dịch cơ sở dữ liệu đã commit thành công.
 */
@Slf4j
@Service
public class ServiceSessionServiceImpl implements ServiceSessionService {

    private final ServiceSessionRepository sessionRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableTransferHistoryRepository transferHistoryRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StompEventPublisher stompEventPublisher;
    private final TableMapper tableMapper;

    /**
     * Khởi tạo ServiceSessionServiceImpl với đầy đủ các dependency cần thiết.
     *
     * @param sessionRepository         Repository thao tác dữ liệu phiên phục vụ
     * @param tableRepository           Repository thao tác dữ liệu bàn ăn
     * @param transferHistoryRepository Repository lưu trữ lịch sử chuyển bàn
     * @param userRepository            Repository thao tác dữ liệu người dùng
     * @param orderRepository           Repository kiểm tra trạng thái đơn hàng của phiên
     * @param stompEventPublisher       Bộ phát sự kiện STOMP sau khi commit transaction
     * @param tableMapper               Mapper chuyển đổi entity sang DTO
     */
    public ServiceSessionServiceImpl(ServiceSessionRepository sessionRepository,
                                     RestaurantTableRepository tableRepository,
                                     TableTransferHistoryRepository transferHistoryRepository,
                                     UserRepository userRepository,
                                     OrderRepository orderRepository,
                                     StompEventPublisher stompEventPublisher,
                                     TableMapper tableMapper) {
        this.sessionRepository = sessionRepository;
        this.tableRepository = tableRepository;
        this.transferHistoryRepository = transferHistoryRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.stompEventPublisher = stompEventPublisher;
        this.tableMapper = tableMapper;
    }

    /**
     * Mở một phiên phục vụ mới trên bàn ăn chỉ định.
     * 
     * Quy tắc nghiệp vụ:
     * 1. Xác thực người dùng hiện tại (bắt buộc đăng nhập).
     * 2. Bàn ăn phải tồn tại và đang hoạt động (isActive = true).
     * 3. Nếu người dùng là tài khoản máy tính bảng (TABLE_ACCOUNT), chỉ được mở phiên cho bàn được gán.
     * 4. Bàn không được có phiên nào đang ACTIVE (tránh mở trùng lặp).
     * 5. Tạo mới phiên phục vụ với trạng thái ACTIVE và lưu vào DB.
     * 6. Phát sự kiện WebSocket SessionStatusChangedEvent tới kênh /topic/tables sau khi commit để cập nhật sơ đồ bàn.
     *
     * @param tableId Mã ID của bàn ăn cần mở phiên
     * @return ServiceSessionResponse Thông tin chi tiết phiên vừa mở
     * @throws UnauthorizedException Nếu người dùng chưa đăng nhập
     * @throws ResourceNotFoundException Nếu bàn hoặc user không tồn tại
     * @throws BusinessRuleException Nếu bàn đang bị khóa, bàn khác quyền tablet, hoặc đã có phiên active
     */
    @Override
    @Transactional
    public ServiceSessionResponse openSession(Long tableId) {
        // 1. Lấy thông tin tài khoản đang thực hiện thao tác
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        // 2. Tìm kiếm bàn ăn trong cơ sở dữ liệu
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        // 3. Kiểm tra bàn ăn có đang hoạt động hay đang ngừng phục vụ
        if (!Boolean.TRUE.equals(table.getIsActive())) {
            throw new BusinessRuleException(
                    "Cannot open session on inactive table: " + table.getTableCode(),
                    "TABLE_INACTIVE");
        }

        // 3.1. Kiểm tra tài khoản máy tính bảng của bàn: nếu tài khoản bàn đang bị khóa thì không thể mở phiên phục vụ
        userRepository.findByTableId(tableId).ifPresent(tableUser -> {
            if (Boolean.FALSE.equals(tableUser.getIsActive())) {
                throw new BusinessRuleException(
                        "Không thể mở phiên phục vụ cho bàn có tài khoản máy tính bảng đang bị khóa (" + table.getTableCode() + ").",
                        "TABLE_ACCOUNT_LOCKED");
            }
        });

        // 4. Kiểm tra phân quyền: Tablet chỉ được mở phiên tại chính bàn được phân công
        if (principal.getRole() == UserRole.TABLE_ACCOUNT) {
            if (principal.getTableId() == null || !principal.getTableId().equals(tableId)) {
                throw new BusinessRuleException(
                        "TABLE_ACCOUNT can only open sessions on their assigned table",
                        "TABLE_NOT_ASSIGNED");
            }
        }

        // 5. Kiểm tra bàn đã có phiên phục vụ nào đang ACTIVE hay chưa
        boolean hasActiveSession = sessionRepository.existsByTableIdAndStatus(tableId, ServiceSessionStatus.ACTIVE);
        if (hasActiveSession) {
            throw new BusinessRuleException(
                    "Table " + table.getTableCode() + " already has an active service session",
                    "SESSION_ALREADY_ACTIVE");
        }

        // 6. Lấy thực thể User mở phiên phục vụ để gán vào quan hệ audit
        User openedByUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        // 7. Tạo mới và lưu phiên phục vụ
        ServiceSession session = ServiceSession.builder()
                .table(table)
                .status(ServiceSessionStatus.ACTIVE)
                .openedBy(openedByUser)
                .build();

        ServiceSession savedSession = sessionRepository.save(session);
        log.info("Opened service session id: {} for table: {} by user: {}",
                savedSession.getId(), table.getTableCode(), principal.getUsername());

        // 8. Phát sự kiện STOMP realtime sau khi commit thành công
        stompEventPublisher.publishAfterCommit(
                StompDestinations.tables(),
                SessionStatusChangedEvent.builder()
                        .sessionId(savedSession.getId())
                        .tableId(table.getId())
                        .tableName(table.getTableCode())
                        .status(ServiceSessionStatus.ACTIVE)
                        .occurredAt(savedSession.getOpenedAt())
                        .build()
        );

        return tableMapper.toSessionResponse(savedSession);
    }

    /**
     * Đóng phiên phục vụ hiện tại của bàn ăn.
     * 
     * Quy tắc nghiệp vụ:
     * 1. Xác thực người dùng hiện tại.
     * 2. Phiên phục vụ phải tồn tại và đang ở trạng thái ACTIVE (không được đóng lại phiên đã đóng).
     * 3. Tài khoản máy tính bảng chỉ được đóng phiên trên bàn của chính mình.
     * 4. Kiểm tra đơn hàng: Toàn bộ đơn hàng thuộc phiên phải đã hoàn tất (COMPLETED) hoặc bị hủy (CANCELLED).
     *    Nếu còn bất kỳ đơn hàng nào đang PENDING hoặc COOKING, từ chối đóng phiên với mã lỗi 409 Conflict.
     * 5. Cập nhật trạng thái phiên sang CLOSED, ghi nhận thời gian đóng và người đóng.
     * 6. Phát sự kiện WebSocket SessionStatusChangedEvent tới /topic/tables sau khi commit.
     *
     * @param sessionId Mã ID của phiên phục vụ cần đóng
     * @return ServiceSessionResponse Thông tin chi tiết phiên sau khi đóng
     * @throws UnauthorizedException Nếu người dùng chưa đăng nhập
     * @throws ResourceNotFoundException Nếu phiên hoặc user không tồn tại
     * @throws BusinessRuleException Nếu phiên đã đóng, sai quyền tablet, hoặc còn đơn hàng chưa kết thúc
     */
    @Override
    @Transactional
    public ServiceSessionResponse closeSession(Long sessionId) {
        // 1. Lấy thông tin tài khoản hiện tại
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        // 2. Tìm kiếm phiên phục vụ kèm thông tin bàn và tài khoản liên quan
        ServiceSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));

        // 3. Kiểm tra trạng thái phiên: chỉ được đóng phiên đang ACTIVE
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Service session is already closed",
                    "SESSION_ALREADY_CLOSED");
        }

        // 4. Tablet chỉ được đóng phiên của chính bàn được phân công
        if (principal.getRole() == UserRole.TABLE_ACCOUNT) {
            if (principal.getTableId() == null || !principal.getTableId().equals(session.getTable().getId())) {
                throw new BusinessRuleException(
                        "TABLE_ACCOUNT can only close sessions on their assigned table",
                        "TABLE_NOT_ASSIGNED");
            }
        }

        // 5. Kiểm tra đơn hàng dở dang: Không được đóng phiên khi còn món đang chờ xử lý hoặc đang nấu
        boolean hasUnfinishedOrders = orderRepository.existsByServiceSessionIdAndStatusIn(
                sessionId,
                List.of(OrderStatus.PENDING, OrderStatus.COOKING)
        );
        if (hasUnfinishedOrders) {
            throw new BusinessRuleException(
                    "Cannot close session while orders are still pending or cooking",
                    "SESSION_HAS_UNFINISHED_ORDERS");
        }

        // 6. Ghi nhận người đóng và thời điểm đóng phiên
        User closedByUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        session.setStatus(ServiceSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(closedByUser);

        ServiceSession savedSession = sessionRepository.save(session);
        log.info("Closed service session id: {} for table: {} by user: {}",
                savedSession.getId(), session.getTable().getTableCode(), principal.getUsername());

        // 7. Phát sự kiện STOMP realtime cập nhật bàn trống sau khi commit
        stompEventPublisher.publishAfterCommit(
                StompDestinations.tables(),
                SessionStatusChangedEvent.builder()
                        .sessionId(savedSession.getId())
                        .tableId(session.getTable().getId())
                        .tableName(session.getTable().getTableCode())
                        .status(ServiceSessionStatus.CLOSED)
                        .occurredAt(savedSession.getClosedAt())
                        .build()
        );

        return tableMapper.toSessionResponse(savedSession);
    }

    /**
     * Lấy thông tin chi tiết của một phiên phục vụ theo ID.
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @return ServiceSessionResponse DTO chứa dữ liệu phiên phục vụ
     * @throws ResourceNotFoundException Nếu không tìm thấy phiên phục vụ
     */
    @Override
    @Transactional(readOnly = true)
    public ServiceSessionResponse getSessionById(Long sessionId) {
        ServiceSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));
        return tableMapper.toSessionResponse(session);
    }

    /**
     * Lấy danh sách phiên phục vụ có hỗ trợ phân trang và bộ lọc theo bàn hoặc trạng thái.
     *
     * @param tableId  Mã ID của bàn ăn (tùy chọn)
     * @param status   Trạng thái phiên phục vụ (ACTIVE hoặc CLOSED - tùy chọn)
     * @param pageable Thông tin phân trang và sắp xếp
     * @return PageResponse<ServiceSessionResponse> Danh sách phân trang phiên phục vụ
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ServiceSessionResponse> getSessions(Long tableId, ServiceSessionStatus status, Pageable pageable) {
        Page<ServiceSession> sessionPage = sessionRepository.findWithFilters(tableId, status, pageable);
        return PageResponse.from(sessionPage, tableMapper::toSessionResponse);
    }

    /**
     * Chuyển phiên phục vụ đang hoạt động sang một bàn ăn khác.
     * 
     * Quy tắc nghiệp vụ:
     * 1. Xác thực người dùng thực hiện (nhân viên hoặc chủ quán).
     * 2. Phiên phục vụ phải đang ACTIVE.
     * 3. Không được chuyển sang cùng một bàn ban đầu.
     * 4. Bàn đích phải tồn tại và đang hoạt động (isActive = true).
     * 5. Bàn đích hiện tại phải trống (không có bất kỳ phiên nào đang ACTIVE).
     * 6. Lưu bản ghi lịch sử chuyển bàn vào table_transfer_history.
     * 7. Cập nhật con trỏ bàn ăn của phiên phục vụ sang bàn đích.
     * 8. Phát sự kiện STOMP TableTransferEvent tới kênh /topic/tables sau khi commit.
     *
     * @param sessionId Mã ID của phiên phục vụ cần chuyển
     * @param request   Yêu cầu chuyển bàn chứa toTableId
     * @return ServiceSessionResponse Thông tin phiên phục vụ sau khi chuyển sang bàn mới
     * @throws UnauthorizedException Nếu người dùng chưa đăng nhập
     * @throws ResourceNotFoundException Nếu phiên hoặc bàn đích không tồn tại
     * @throws BusinessRuleException Nếu phiên đã đóng, chuyển cùng bàn, hoặc bàn đích không khả dụng/đang có khách
     */
    @Override
    @Transactional
    public ServiceSessionResponse transferTable(Long sessionId, TransferTableRequest request) {
        // 1. Xác thực người dùng
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        // 2. Tìm kiếm phiên phục vụ cần chuyển
        ServiceSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service session not found with id: " + sessionId));

        // 3. Chỉ được chuyển phiên đang ACTIVE
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot transfer a closed service session",
                    "SESSION_NOT_ACTIVE");
        }

        RestaurantTable fromTable = session.getTable();
        Long toTableId = request.getToTableId();

        // 4. Kiểm tra bàn đích không được trùng bàn hiện tại
        if (fromTable.getId().equals(toTableId)) {
            throw new BusinessRuleException(
                    "Cannot transfer to the same table",
                    "TRANSFER_SAME_TABLE");
        }

        // 5. Kiểm tra bàn đích có tồn tại và đang hoạt động không
        RestaurantTable toTable = tableRepository.findById(toTableId)
                .orElseThrow(() -> new ResourceNotFoundException("Target table not found with id: " + toTableId));

        if (!Boolean.TRUE.equals(toTable.getIsActive())) {
            throw new BusinessRuleException(
                    "Cannot transfer to inactive table: " + toTable.getTableCode(),
                    "TARGET_TABLE_INACTIVE");
        }

        // Kiểm tra tài khoản máy tính bảng của bàn đích: nếu tài khoản bàn đang bị khóa thì không thể chuyển bàn
        userRepository.findByTableId(toTableId).ifPresent(targetTableUser -> {
            if (Boolean.FALSE.equals(targetTableUser.getIsActive())) {
                throw new BusinessRuleException(
                        "Không thể chuyển bàn sang bàn có tài khoản máy tính bảng đang bị khóa (" + toTable.getTableCode() + ").",
                        "TARGET_TABLE_ACCOUNT_LOCKED");
            }
        });

        // 6. Kiểm tra bàn đích có đang có phiên ACTIVE nào khác không
        boolean targetHasActiveSession = sessionRepository.existsByTableIdAndStatus(toTableId, ServiceSessionStatus.ACTIVE);
        if (targetHasActiveSession) {
            throw new BusinessRuleException(
                    "Target table " + toTable.getTableCode() + " already has an active service session",
                    "TARGET_TABLE_OCCUPIED");
        }

        User transferredByUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        // 7. Ghi nhận lịch sử chuyển bàn
        TableTransferHistory transferHistory = TableTransferHistory.builder()
                .serviceSession(session)
                .fromTable(fromTable)
                .toTable(toTable)
                .transferredBy(transferredByUser)
                .build();
        TableTransferHistory savedHistory = transferHistoryRepository.save(transferHistory);

        // 8. Cập nhật bàn mới cho phiên phục vụ
        session.setTable(toTable);
        ServiceSession savedSession = sessionRepository.save(session);

        log.info("Transferred session id: {} from table {} to table {} by user: {}",
                sessionId, fromTable.getTableCode(), toTable.getTableCode(), principal.getUsername());

        // 9. Phát sự kiện STOMP realtime cập nhật trạng thái 2 bàn sau khi commit
        stompEventPublisher.publishAfterCommit(
                StompDestinations.tables(),
                TableTransferEvent.builder()
                        .transferHistoryId(savedHistory.getId())
                        .sessionId(sessionId)
                        .fromTableId(fromTable.getId())
                        .fromTableName(fromTable.getTableCode())
                        .toTableId(toTable.getId())
                        .toTableName(toTable.getTableCode())
                        .occurredAt(savedHistory.getTransferredAt())
                        .build()
        );

        return tableMapper.toSessionResponse(savedSession);
    }

    /**
     * Lấy lịch sử chuyển bàn của một phiên phục vụ cụ thể.
     *
     * @param sessionId Mã ID của phiên phục vụ
     * @param pageable  Thông tin phân trang
     * @return PageResponse<TableTransferHistoryResponse> Lịch sử chuyển bàn phân trang
     * @throws ResourceNotFoundException Nếu phiên phục vụ không tồn tại
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TableTransferHistoryResponse> getTransferHistory(Long sessionId, Pageable pageable) {
        // Kiểm tra phiên phục vụ có tồn tại không
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Service session not found with id: " + sessionId);
        }

        Page<TableTransferHistory> historyPage = transferHistoryRepository.findByServiceSessionId(sessionId, pageable);
        return PageResponse.from(historyPage, tableMapper::toTransferHistoryResponse);
    }
}
