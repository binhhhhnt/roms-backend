-- ---------------------------------------------------------------------
-- ROMS: V10 Migration - Seed Bia 333 (Direct Sale Menu Item & Ingredient)
-- ---------------------------------------------------------------------

DO $$
DECLARE
    v_category_id BIGINT;
    v_tax_category_id BIGINT;
    v_ingredient_id BIGINT;
    v_menu_item_id BIGINT;
BEGIN
    -- 1. Lấy danh mục Đồ uống (ưu tiên tên 'Đồ uống', fallback 'Đồ Uống & Tráng Miệng')
    SELECT id INTO v_category_id
    FROM categories
    WHERE LOWER(name) IN ('đồ uống', 'đồ uống & tráng miệng')
    ORDER BY id ASC
    LIMIT 1;

    IF v_category_id IS NULL THEN
        INSERT INTO categories (name, description, display_order, is_active)
        VALUES ('Đồ uống', 'Các loại đồ uống, bia và nước giải khát', 3, TRUE)
        RETURNING id INTO v_category_id;
    END IF;

    -- 2. Lấy tax_category mặc định 8.00%
    SELECT id INTO v_tax_category_id
    FROM tax_categories
    WHERE rate = 8.00
    LIMIT 1;

    -- 3. Thêm nguyên liệu Bia 333 nếu chưa tồn tại
    SELECT id INTO v_ingredient_id
    FROM ingredients
    WHERE LOWER(name) = 'bia 333'
    LIMIT 1;

    IF v_ingredient_id IS NULL THEN
        INSERT INTO ingredients (name, unit, current_stock, minimum_stock, is_active)
        VALUES ('Bia 333', 'lon', 100.000, 20.000, TRUE)
        RETURNING id INTO v_ingredient_id;
    END IF;

    -- 4. Thêm món Bia 333 (is_direct_sale = TRUE) nếu chưa tồn tại
    SELECT id INTO v_menu_item_id
    FROM menu_items
    WHERE LOWER(name) = 'bia 333'
    LIMIT 1;

    IF v_menu_item_id IS NULL THEN
        INSERT INTO menu_items (category_id, tax_category_id, name, description, price, status, is_featured, is_direct_sale)
        VALUES (v_category_id, v_tax_category_id, 'Bia 333', 'Bia 333 lon 330ml ướp lạnh', 25000.00, 'AVAILABLE', FALSE, TRUE)
        RETURNING id INTO v_menu_item_id;
    END IF;

    -- 5. Gắn công thức định lượng 1 phần = 1 lon Bia 333
    IF NOT EXISTS (
        SELECT 1 FROM recipe_items
        WHERE menu_item_id = v_menu_item_id AND ingredient_id = v_ingredient_id
    ) THEN
        INSERT INTO recipe_items (menu_item_id, ingredient_id, quantity_required)
        VALUES (v_menu_item_id, v_ingredient_id, 1.000);
    END IF;
END $$;
