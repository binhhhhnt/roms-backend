package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.response.MenuItemImageResponse;
import com.roms.service.MenuItemImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.net.URI;

@RestController @RequestMapping("/api/menu-items/{menuItemId}/images") @RequiredArgsConstructor
public class MenuItemImageController {
    private final MenuItemImageService service;
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<MenuItemImageResponse>> upload(@PathVariable Long menuItemId,
            @RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "0") int displayOrder) {
        var response = service.uploadImage(menuItemId, file, displayOrder);
        return ResponseEntity.created(URI.create("/api/menu-items/" + menuItemId + "/images/" + response.getId()))
                .body(ApiResponse.success("Image uploaded successfully", response));
    }
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long menuItemId, @PathVariable Long imageId) {
        service.deleteImage(menuItemId, imageId);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidUpload(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid image upload request", "VALIDATION_ERROR"));
    }
}
