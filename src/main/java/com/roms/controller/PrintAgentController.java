package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.exception.UnauthorizedException;
import com.roms.service.PrintAgentRegistryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/print-agent")
@RequiredArgsConstructor
public class PrintAgentController {

    private final PrintAgentRegistryService printAgentRegistryService;

    @PostMapping("/printers")
    public ResponseEntity<ApiResponse<Void>> registerPrinters(
            @RequestHeader(value = "X-Print-Agent-Key", required = false) String agentKey,
            @RequestBody(required = false) RegisterPrintersRequest request) {
        if (!printAgentRegistryService.isValidKey(agentKey)) {
            throw new UnauthorizedException("Invalid Print Agent key");
        }
        List<String> printers = request != null ? request.getPrinters() : List.of();
        printAgentRegistryService.updatePrinters(printers);
        return ResponseEntity.ok(ApiResponse.success("Print agent printers registered", null));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'CASHIER_STAFF', 'KITCHEN_STAFF')")
    public ResponseEntity<ApiResponse<PrintAgentStatusResponse>> getStatus() {
        boolean online = printAgentRegistryService.isOnline();
        Instant lastSeenAt = printAgentRegistryService.getLastSeenAt();
        List<String> printers = printAgentRegistryService.getPrinters();
        return ResponseEntity.ok(ApiResponse.success("Print agent status retrieved",
                new PrintAgentStatusResponse(online, lastSeenAt, printers)));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterPrintersRequest {
        private List<String> printers;
    }

    @Data
    @AllArgsConstructor
    public static class PrintAgentStatusResponse {
        private boolean online;
        private Instant lastSeenAt;
        private List<String> printers;
    }
}
