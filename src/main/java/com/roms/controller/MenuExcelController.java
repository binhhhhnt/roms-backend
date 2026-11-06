package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.response.MenuExcelImportResponse;
import com.roms.dto.response.MenuExcelPreview;
import com.roms.service.MenuExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping("/api/menu-excel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class MenuExcelController {
    private final MenuExcelService service;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Menu_mau.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.template());
    }

    @GetMapping("/template/preview")
    public ApiResponse<MenuExcelPreview> preview() {
        return ApiResponse.success("Nội dung file Excel mẫu", service.preview());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MenuExcelImportResponse> importMenu(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String confirmationToken) {
        return ApiResponse.success("Nhập menu thành công", service.importMenu(file, confirmationToken));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidUpload(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Chọn file Excel tối đa 5 MB.", "VALIDATION_ERROR"));
    }
}
