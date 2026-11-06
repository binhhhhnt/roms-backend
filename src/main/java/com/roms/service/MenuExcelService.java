package com.roms.service;

import com.roms.dto.response.MenuExcelImportResponse;
import com.roms.dto.response.MenuExcelPreview;
import org.springframework.web.multipart.MultipartFile;

public interface MenuExcelService {
    MenuExcelPreview preview();
    byte[] template();
    MenuExcelImportResponse importMenu(MultipartFile file);
    MenuExcelImportResponse importMenu(MultipartFile file, String confirmationToken);
}
