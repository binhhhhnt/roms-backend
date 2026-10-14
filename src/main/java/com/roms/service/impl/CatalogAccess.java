package com.roms.service.impl;

import com.roms.enums.UserRole;
import com.roms.exception.ForbiddenException;
import com.roms.exception.UnauthorizedException;
import com.roms.security.SecurityUtils;

final class CatalogAccess {
    private CatalogAccess() {}
    static void requireOwner() {
        UserRole role = SecurityUtils.getCurrentUserRole()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        if (role != UserRole.OWNER) throw new ForbiddenException("Only OWNER can manage the catalog");
    }
}
