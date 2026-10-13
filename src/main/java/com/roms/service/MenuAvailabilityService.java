package com.roms.service;

import com.roms.dto.request.UpdateMenuStatusRequest;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.dto.response.MenuItemStatusResponse;

public interface MenuAvailabilityService {

    /**
     * Update the status of a menu item (AVAILABLE, TEMPORARILY_UNAVAILABLE, DISCONTINUED).
     * OWNER can set any status.
     * KITCHEN_STAFF can only set AVAILABLE or TEMPORARILY_UNAVAILABLE (DISCONTINUED yields 403 Forbidden).
     * Automatically invalidates Redis Gemini menu context cache after transaction commit.
     *
     * @param id menu item ID
     * @param request update request containing target status
     * @return updated menu item status response
     */
    MenuItemStatusResponse updateMenuStatus(Long id, UpdateMenuStatusRequest request);

    /**
     * Check live sellability of a menu item based on its status and recipe ingredients' current stock in PostgreSQL.
     * Does NOT mutate database. Does NOT cache availability.
     *
     * @param id menu item ID
     * @param quantity requested quantity (must be >= 1)
     * @return sellability details
     */
    MenuItemSellabilityResponse checkSellability(Long id, Integer quantity);
}
