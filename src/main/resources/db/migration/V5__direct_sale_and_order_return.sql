-- ---------------------------------------------------------------------
-- ROMS: V5 Migration - Direct Sale Menu Items & Order Item Returns
-- ---------------------------------------------------------------------

-- 1. Table: menu_items - Add is_direct_sale flag
ALTER TABLE menu_items
ADD COLUMN is_direct_sale BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Table: order_items - Add returned_quantity and returned_by
ALTER TABLE order_items
ADD COLUMN returned_quantity INTEGER NOT NULL DEFAULT 0,
ADD COLUMN returned_by BIGINT REFERENCES users(id);

-- Constraint: 0 <= returned_quantity <= quantity
ALTER TABLE order_items
ADD CONSTRAINT chk_order_items_returned_quantity
CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);

-- 3. Table: stock_movements - Update check constraint to include ORDER_RETURN
ALTER TABLE stock_movements
DROP CONSTRAINT IF EXISTS stock_movements_movement_type_check;

ALTER TABLE stock_movements
ADD CONSTRAINT stock_movements_movement_type_check
CHECK (movement_type IN ('PURCHASE', 'ORDER_CONSUMPTION', 'INVENTORY_ADJUSTMENT', 'ORDER_RETURN'));

