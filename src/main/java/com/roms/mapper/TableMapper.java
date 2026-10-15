package com.roms.mapper;

import com.roms.dto.response.ServiceSessionResponse;
import com.roms.dto.response.TableResponse;
import com.roms.dto.response.TableTransferHistoryResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.TableTransferHistory;
import com.roms.enums.ServiceSessionStatus;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa các thực thể JPA (RestaurantTable, ServiceSession, TableTransferHistory)
 * và các đối tượng DTO phản hồi (TableResponse, ServiceSessionResponse, TableTransferHistoryResponse).
 */
@Component
public class TableMapper {

    /**
     * Chuyển đổi một thực thể RestaurantTable sang TableResponse DTO cơ bản.
     * Chưa bao gồm trạng thái thời gian thực (realtimeStatus) và phiên ACTIVE.
     *
     * @param table Thực thể bàn ăn
     * @return TableResponse DTO bàn ăn
     */
    public TableResponse toResponse(RestaurantTable table) {
        if (table == null) {
            return null;
        }

        return TableResponse.builder()
                .id(table.getId())
                .tableCode(table.getTableCode())
                .capacity(table.getCapacity())
                .area(table.getArea())
                .isActive(table.getIsActive())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }

    /**
     * Chuyển đổi thực thể RestaurantTable sang TableResponse kèm trạng thái thời gian thực.
     * 
     * Quy tắc suy diễn trạng thái (derived realtimeStatus):
     * - OUT_OF_SERVICE: Khi bàn đang bị vô hiệu hóa (isActive = false).
     * - OCCUPIED: Khi bàn đang có một phiên phục vụ ở trạng thái ACTIVE.
     * - EMPTY: Khi bàn đang hoạt động và không có phiên ACTIVE nào (sẵn sàng đón khách).
     *
     * @param table         Thực thể bàn ăn
     * @param activeSession Phiên phục vụ đang ACTIVE tại bàn (nếu có, null nếu không có)
     * @return TableResponse DTO bàn ăn kèm trạng thái thực tế
     */
    public TableResponse toResponseWithStatus(RestaurantTable table, ServiceSession activeSession) {
        TableResponse response = toResponse(table);
        if (response == null) {
            return null;
        }

        if (!Boolean.TRUE.equals(table.getIsActive())) {
            response.setRealtimeStatus("OUT_OF_SERVICE");
        } else if (activeSession != null && activeSession.getStatus() == ServiceSessionStatus.ACTIVE) {
            response.setRealtimeStatus("OCCUPIED");
            response.setActiveSessionId(activeSession.getId());
        } else {
            response.setRealtimeStatus("EMPTY");
        }

        return response;
    }

    /**
     * Chuyển đổi thực thể ServiceSession sang ServiceSessionResponse DTO.
     * Trích xuất thông tin bàn ăn, người mở phiên và người đóng phiên.
     *
     * @param session Thực thể phiên phục vụ
     * @return ServiceSessionResponse DTO phiên phục vụ
     */
    public ServiceSessionResponse toSessionResponse(ServiceSession session) {
        if (session == null) {
            return null;
        }

        ServiceSessionResponse.ServiceSessionResponseBuilder builder = ServiceSessionResponse.builder()
                .id(session.getId())
                .status(session.getStatus())
                .openedAt(session.getOpenedAt())
                .closedAt(session.getClosedAt());

        if (session.getTable() != null) {
            builder.tableId(session.getTable().getId());
            builder.tableCode(session.getTable().getTableCode());
        }

        if (session.getOpenedBy() != null) {
            builder.openedById(session.getOpenedBy().getId());
            builder.openedByName(session.getOpenedBy().getFullName());
        }

        if (session.getClosedBy() != null) {
            builder.closedById(session.getClosedBy().getId());
            builder.closedByName(session.getClosedBy().getFullName());
        }

        return builder.build();
    }

    /**
     * Chuyển đổi thực thể TableTransferHistory sang TableTransferHistoryResponse DTO.
     * Trích xuất thông tin bàn nguồn (fromTable), bàn đích (toTable) và nhân viên chuyển bàn.
     *
     * @param history Thực thể lịch sử chuyển bàn
     * @return TableTransferHistoryResponse DTO lịch sử chuyển bàn
     */
    public TableTransferHistoryResponse toTransferHistoryResponse(TableTransferHistory history) {
        if (history == null) {
            return null;
        }

        TableTransferHistoryResponse.TableTransferHistoryResponseBuilder builder =
                TableTransferHistoryResponse.builder()
                        .id(history.getId())
                        .serviceSessionId(history.getServiceSession().getId())
                        .transferredAt(history.getTransferredAt());

        if (history.getFromTable() != null) {
            builder.fromTableId(history.getFromTable().getId());
            builder.fromTableCode(history.getFromTable().getTableCode());
        }

        if (history.getToTable() != null) {
            builder.toTableId(history.getToTable().getId());
            builder.toTableCode(history.getToTable().getTableCode());
        }

        if (history.getTransferredBy() != null) {
            builder.transferredById(history.getTransferredBy().getId());
            builder.transferredByName(history.getTransferredBy().getFullName());
        }

        return builder.build();
    }
}
