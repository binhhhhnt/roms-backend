package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.event.StaffCallAckEvent;
import com.roms.dto.event.StaffCallEvent;
import com.roms.entity.RestaurantTable;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.RestaurantTableRepository;
import com.roms.security.UserPrincipal;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class StaffCallController {

    private final RestaurantTableRepository tableRepository;
    private final StompEventPublisher stompEventPublisher;

    /**
     * TABLE_ACCOUNT (hoặc SERVICE_STAFF/OWNER) gọi nhân viên hỗ trợ.
     * Kiểm tra ràng buộc sở hữu bàn đối với TABLE_ACCOUNT.
     */
    @PostMapping("/{tableId}/call-staff")
    @PreAuthorize("hasAnyRole('TABLE_ACCOUNT', 'SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> callStaff(
            @PathVariable Long tableId,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal.getRole().name().equals("TABLE_ACCOUNT")) {
            if (!tableId.equals(principal.getTableId())) {
                throw new ForbiddenException("Cannot call staff for another table");
            }
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        StaffCallEvent event = StaffCallEvent.builder()
                .tableId(tableId)
                .tableCode(table.getTableCode())
                .requestedAt(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();

        stompEventPublisher.send(StompDestinations.serviceStaffCalls(), event);

        return ResponseEntity.ok(ApiResponse.success("Staff call sent", null));
    }

    /**
     * SERVICE_STAFF hoặc OWNER xác nhận yêu cầu gọi nhân viên.
     * Broadcast ack event tới tất cả staff để đóng thông báo.
     */
    @PostMapping("/{tableId}/call-staff/acknowledge")
    @PreAuthorize("hasAnyRole('SERVICE_STAFF', 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> acknowledgeStaffCall(
            @PathVariable Long tableId,
            @RequestParam String requestId,
            @AuthenticationPrincipal UserPrincipal principal) {

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        StaffCallAckEvent ack = StaffCallAckEvent.builder()
                .tableId(tableId)
                .tableCode(table.getTableCode())
                .requestId(requestId)
                .acknowledgedBy(principal.getUsername())
                .acknowledgedAt(LocalDateTime.now())
                .build();

        stompEventPublisher.send(StompDestinations.serviceStaffCallsAck(), ack);

        return ResponseEntity.ok(ApiResponse.success("Acknowledged", null));
    }
}

