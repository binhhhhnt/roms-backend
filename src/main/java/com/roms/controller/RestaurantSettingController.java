package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.service.PrintAgentRegistryService;
import com.roms.service.PrinterService;
import com.roms.service.RestaurantSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class RestaurantSettingController {

    private final RestaurantSettingService settingService;
    private final PrinterService printerService;
    private final PrintAgentRegistryService printAgentRegistryService;

    public RestaurantSettingController(
            RestaurantSettingService settingService,
            PrinterService printerService) {
        this(settingService, printerService, null);
    }

    @Autowired
    public RestaurantSettingController(
            RestaurantSettingService settingService,
            PrinterService printerService,
            PrintAgentRegistryService printAgentRegistryService) {
        this.settingService = settingService;
        this.printerService = printerService;
        this.printAgentRegistryService = printAgentRegistryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RestaurantSettingResponse>> getSettings() {
        RestaurantSettingResponse response = settingService.getSettings();
        return ResponseEntity.ok(ApiResponse.success("Restaurant settings retrieved successfully", response));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<RestaurantSettingResponse>> updateSettings(
            @Valid @RequestBody UpdateSettingRequest request) {
        RestaurantSettingResponse response = settingService.updateSettings(request);
        return ResponseEntity.ok(ApiResponse.success("Restaurant settings updated successfully", response));
    }

    @GetMapping("/printers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<String>>> getAvailablePrinters() {
        List<String> printers = printAgentRegistryService != null && printAgentRegistryService.isOnline()
                ? printAgentRegistryService.getPrinters()
                : printerService.getInstalledPrinters();
        return ResponseEntity.ok(ApiResponse.success("Available printers retrieved successfully", printers));
    }
}
