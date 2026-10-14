package com.roms.enums;

/**
 * Derived stock status for an ingredient.
 * Computed from current_stock and minimum_stock; never persisted.
 */
public enum StockStatus {
    /** current_stock > minimum_stock (or minimum_stock is null) */
    NORMAL,
    /** current_stock > 0 AND current_stock <= minimum_stock */
    LOW,
    /** current_stock == 0 */
    OUT_OF_STOCK
}
