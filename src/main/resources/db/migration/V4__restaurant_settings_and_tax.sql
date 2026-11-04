-- ---------------------------------------------------------------------
-- ROMS: V4 Migration - Restaurant Settings, Tax Categories & VAT Support
-- ---------------------------------------------------------------------

-- 1. Table: restaurant_settings (Singleton configuration)
CREATE TABLE restaurant_settings (
    id                      BIGSERIAL PRIMARY KEY,
    menu_price_includes_vat BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed default setting (Default: Prices include VAT)
INSERT INTO restaurant_settings (menu_price_includes_vat) VALUES (TRUE);

-- 2. Table: tax_categories (Unique non-negative rate)
CREATE TABLE tax_categories (
    id          BIGSERIAL     PRIMARY KEY,
    rate        DECIMAL(5,2)  NOT NULL UNIQUE CHECK (rate >= 0),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Seed default tax rates (0%, 5%, 8%, 10%)
INSERT INTO tax_categories (rate) VALUES
(0.00),
(5.00),
(8.00),
(10.00);

-- 3. Link menu_items with tax_category_id
ALTER TABLE menu_items
ADD COLUMN tax_category_id BIGINT REFERENCES tax_categories(id);

-- Assign default 8.00% VAT to existing menu items
UPDATE menu_items SET tax_category_id = (SELECT id FROM tax_categories WHERE rate = 8.00);

CREATE INDEX idx_menu_items_tax_category_id ON menu_items(tax_category_id);

-- 4. Update order_items: Add tax snapshot columns for audit and invoicing integrity
ALTER TABLE order_items
ADD COLUMN tax_rate DECIMAL(5,2) NOT NULL DEFAULT 8.00,
ADD COLUMN is_vat_inclusive BOOLEAN NOT NULL DEFAULT TRUE;

