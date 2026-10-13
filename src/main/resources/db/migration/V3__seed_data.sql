-- =====================================================================
-- ROMS - Restaurant Operations Management System
-- Flyway Migration V3: Seed Initial Data
-- Vietnamese restaurant theme
-- Default password for all seed accounts: Password@123
-- BCrypt: $2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Tables (10 bàn, area = NULL)
-- ---------------------------------------------------------------------
INSERT INTO tables (table_code, capacity, area, is_active) VALUES
('T01', 4, NULL, TRUE),
('T02', 4, NULL, TRUE),
('T03', 4, NULL, TRUE),
('T04', 4, NULL, TRUE),
('T05', 4, NULL, TRUE),
('T06', 4, NULL, TRUE),
('T07', 2, NULL, TRUE),
('T08', 2, NULL, TRUE),
('T09', 8, NULL, TRUE),
('T10', 8, NULL, TRUE);

-- ---------------------------------------------------------------------
-- 2. Users (Nhân sự & 10 tài khoản tablet gắn với bàn)
-- ---------------------------------------------------------------------
-- Staff / Owner
INSERT INTO users (username, password_hash, full_name, email, role, table_id, is_active) VALUES
('owner',       '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq', 'Nguyễn Văn Chủ',    'owner@roms.vn',       'OWNER',           NULL, TRUE),
('service01',   '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq', 'Trần Thị Thu Ngân', 'service01@roms.vn',   'SERVICE_STAFF',   NULL, TRUE),
('service02',   '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq', 'Lê Hoàng Nam',      'service02@roms.vn',   'SERVICE_STAFF',   NULL, TRUE),
('kitchen01',   '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq', 'Võ Quốc Bếp',       'kitchen01@roms.vn',   'KITCHEN_STAFF',   NULL, TRUE),
('inventory01', '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq', 'Phạm Thị Kho',     'inventory01@roms.vn', 'INVENTORY_STAFF', NULL, TRUE);

-- Table Accounts (Tablet tại bàn)
INSERT INTO users (username, password_hash, full_name, role, table_id, is_active)
SELECT 
    'table_' || LOWER(table_code),
    '$2a$10$mmcdyK6TaL06ZtNkoC.DNeMzVDQPQi9ebwTud819BJWTnzZBEWcNq',
    'Tablet Bàn ' || table_code,
    'TABLE_ACCOUNT',
    id,
    TRUE
FROM tables
ORDER BY id;

-- ---------------------------------------------------------------------
-- 3. Categories (3 danh mục món)
-- ---------------------------------------------------------------------
INSERT INTO categories (name, description, display_order, is_active) VALUES
('Món Chính',               'Các món ăn truyền thống đặc sắc', 1, TRUE),
('Món Ăn Kèm & Khai Vị',   'Món khai vị nhẹ và ăn kèm',      2, TRUE),
('Đồ Uống & Tráng Miệng',   'Nước ép, trà và đồ uống tươi mát', 3, TRUE);

-- ---------------------------------------------------------------------
-- 4. Ingredients (10 nguyên liệu cơ bản)
-- ---------------------------------------------------------------------
INSERT INTO ingredients (name, unit, current_stock, minimum_stock, is_active) VALUES
('Thịt bò phi lê', 'kg',  20.000, 5.000, TRUE),
('Thịt heo ba chỉ', 'kg', 25.000, 5.000, TRUE),
('Gà ta thả vườn',  'kg', 15.000, 3.000, TRUE),
('Tôm sú tươi',    'kg', 10.000, 2.000, TRUE),
('Bánh phở tươi',  'kg', 30.000, 5.000, TRUE),
('Gạo ST25',       'kg', 50.000, 10.000, TRUE),
('Trà Ô Long',     'kg',  5.000, 1.000, TRUE),
('Hạt cà phê Robusta', 'kg', 5.000, 1.000, TRUE),
('Đường cát trắng', 'kg', 9.500, 2.000, TRUE),
('Rau thơm & gia vị', 'kg', 10.000, 2.000, TRUE);

-- ---------------------------------------------------------------------
-- 5. Menu Items (10 món ăn / đồ uống)
-- ---------------------------------------------------------------------
INSERT INTO menu_items (category_id, name, description, price, status, is_featured)
VALUES
-- Món Chính (Category 1)
((SELECT id FROM categories WHERE name = 'Món Chính'), 'Phở Bò Tái Nạm', 'Phở bò truyền thống nước dùng hầm xương 24h', 65000.00, 'AVAILABLE', TRUE),
((SELECT id FROM categories WHERE name = 'Món Chính'), 'Cơm Tấm Sườn Bì Chả', 'Cơm tấm sườn nướng than hoa đậm đà', 55000.00, 'AVAILABLE', TRUE),
((SELECT id FROM categories WHERE name = 'Món Chính'), 'Bún Bò Huế Đặc Biệt', 'Bún bò cay nồng chuẩn vị cố đô', 65000.00, 'AVAILABLE', FALSE),
((SELECT id FROM categories WHERE name = 'Món Chính'), 'Gà Nướng Muối Ớt (Nửa con)', 'Gà ta da giòn cay thơm nức mũi', 120000.00, 'AVAILABLE', TRUE),
((SELECT id FROM categories WHERE name = 'Món Chính'), 'Tôm Hấp Nước Dừa', 'Tôm sú ngọt thịt hấp nước dừa xiêm tươi', 145000.00, 'AVAILABLE', FALSE),

-- Món Ăn Kèm & Khai Vị (Category 2)
((SELECT id FROM categories WHERE name = 'Món Ăn Kèm & Khai Vị'), 'Gỏi Cuốn Tôm Thịt (3 cuốn)', 'Tôm sú, thịt ba chỉ cuộn rau sống bánh tráng', 45000.00, 'AVAILABLE', FALSE),
((SELECT id FROM categories WHERE name = 'Món Ăn Kèm & Khai Vị'), 'Nem Rán Hà Nội (5 chiếc)', 'Nem rán giòn rụm nhân thịt mộc nhĩ nấm hương', 50000.00, 'AVAILABLE', TRUE),
((SELECT id FROM categories WHERE name = 'Món Ăn Kèm & Khai Vị'), 'Khoai Tây Chiên Bơ Tỏi', 'Khoai tây vàng giòn lắc bơ tỏi thơm ngậy', 35000.00, 'AVAILABLE', FALSE),

-- Đồ Uống & Tráng Miệng (Category 3)
((SELECT id FROM categories WHERE name = 'Đồ Uống & Tráng Miệng'), 'Cà Phê Sữa Đá Sài Gòn', 'Cà phê pha phin truyền thống thơm béo', 28000.00, 'AVAILABLE', FALSE),
((SELECT id FROM categories WHERE name = 'Đồ Uống & Tráng Miệng'), 'Trà Đào Cam Sả', 'Trà ô long thanh mát kết hợp đào và hương sả', 38000.00, 'AVAILABLE', TRUE);

-- ---------------------------------------------------------------------
-- 6. Recipe Items (Công thức định lượng tượng trưng)
-- ---------------------------------------------------------------------
INSERT INTO recipe_items (menu_item_id, ingredient_id, quantity_required) VALUES
-- Phở Bò Tái Nạm: Thịt bò 0.15 kg, Bánh phở 0.20 kg
((SELECT id FROM menu_items WHERE name = 'Phở Bò Tái Nạm'), (SELECT id FROM ingredients WHERE name = 'Thịt bò phi lê'), 0.150),
((SELECT id FROM menu_items WHERE name = 'Phở Bò Tái Nạm'), (SELECT id FROM ingredients WHERE name = 'Bánh phở tươi'), 0.200),

-- Cơm Tấm Sườn: Thịt heo 0.18 kg, Gạo ST25 0.15 kg
((SELECT id FROM menu_items WHERE name = 'Cơm Tấm Sườn Bì Chả'), (SELECT id FROM ingredients WHERE name = 'Thịt heo ba chỉ'), 0.180),
((SELECT id FROM menu_items WHERE name = 'Cơm Tấm Sườn Bì Chả'), (SELECT id FROM ingredients WHERE name = 'Gạo ST25'), 0.150),

-- Gà Nướng Muối Ớt: Gà ta 0.60 kg
((SELECT id FROM menu_items WHERE name = 'Gà Nướng Muối Ớt (Nửa con)'), (SELECT id FROM ingredients WHERE name = 'Gà ta thả vườn'), 0.600),

-- Tôm Hấp Nước Dừa: Tôm sú 0.30 kg
((SELECT id FROM menu_items WHERE name = 'Tôm Hấp Nước Dừa'), (SELECT id FROM ingredients WHERE name = 'Tôm sú tươi'), 0.300),

-- Gỏi Cuốn: Tôm sú 0.05 kg, Thịt heo 0.05 kg
((SELECT id FROM menu_items WHERE name = 'Gỏi Cuốn Tôm Thịt (3 cuốn)'), (SELECT id FROM ingredients WHERE name = 'Tôm sú tươi'), 0.050),
((SELECT id FROM menu_items WHERE name = 'Gỏi Cuốn Tôm Thịt (3 cuốn)'), (SELECT id FROM ingredients WHERE name = 'Thịt heo ba chỉ'), 0.050),

-- Cà Phê Sữa Đá: Cà phê 0.03 kg, Đường 0.02 kg
((SELECT id FROM menu_items WHERE name = 'Cà Phê Sữa Đá Sài Gòn'), (SELECT id FROM ingredients WHERE name = 'Hạt cà phê Robusta'), 0.030),
((SELECT id FROM menu_items WHERE name = 'Cà Phê Sữa Đá Sài Gòn'), (SELECT id FROM ingredients WHERE name = 'Đường cát trắng'), 0.020),

-- Trà Đào Cam Sả: Trà Ô Long 0.02 kg, Đường 0.02 kg
((SELECT id FROM menu_items WHERE name = 'Trà Đào Cam Sả'), (SELECT id FROM ingredients WHERE name = 'Trà Ô Long'), 0.020),
((SELECT id FROM menu_items WHERE name = 'Trà Đào Cam Sả'), (SELECT id FROM ingredients WHERE name = 'Đường cát trắng'), 0.020);

-- ---------------------------------------------------------------------
-- 7. Purchases & Purchase Items & Stock Movements (Lô hàng nhập mẫu)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_user_inventory_id BIGINT;
    v_purchase_id BIGINT;
    v_p_item1_id BIGINT;
    v_p_item2_id BIGINT;
    v_p_item3_id BIGINT;
    v_ing_bo BIGINT;
    v_ing_heo BIGINT;
    v_ing_gao BIGINT;
BEGIN
    SELECT id INTO v_user_inventory_id FROM users WHERE username = 'inventory01';
    SELECT id INTO v_ing_bo FROM ingredients WHERE name = 'Thịt bò phi lê';
    SELECT id INTO v_ing_heo FROM ingredients WHERE name = 'Thịt heo ba chỉ';
    SELECT id INTO v_ing_gao FROM ingredients WHERE name = 'Gạo ST25';

    -- Tạo 1 phiếu nhập hàng ngày hôm qua
    INSERT INTO purchases (purchase_date, total_amount, created_by, note)
    VALUES (CURRENT_DATE - 1, 9250000.00, v_user_inventory_id, 'Nhập nguyên liệu đầu tuần')
    RETURNING id INTO v_purchase_id;

    -- Chi tiết nhập thịt bò (20kg @ 250k = 5,000,000)
    INSERT INTO purchase_items (purchase_id, ingredient_id, quantity, unit_price, subtotal)
    VALUES (v_purchase_id, v_ing_bo, 20.000, 250000.00, 5000000.00)
    RETURNING id INTO v_p_item1_id;

    -- Chi tiết nhập thịt heo (25kg @ 120k = 3,000,000)
    INSERT INTO purchase_items (purchase_id, ingredient_id, quantity, unit_price, subtotal)
    VALUES (v_purchase_id, v_ing_heo, 25.000, 120000.00, 3000000.00)
    RETURNING id INTO v_p_item2_id;

    -- Chi tiết nhập gạo ST25 (50kg @ 25k = 1,250,000)
    INSERT INTO purchase_items (purchase_id, ingredient_id, quantity, unit_price, subtotal)
    VALUES (v_purchase_id, v_ing_gao, 50.000, 25000.00, 1250000.00)
    RETURNING id INTO v_p_item3_id;

    -- Ghi sổ cái biến động kho (Stock Movements)
    INSERT INTO stock_movements (ingredient_id, movement_type, quantity_change, balance_after, purchase_item_id, note, occurred_at)
    VALUES 
    (v_ing_bo,  'PURCHASE', 20.000, 20.000, v_p_item1_id, 'Nhập kho theo phiếu #' || v_purchase_id, NOW() - INTERVAL '1 day'),
    (v_ing_heo, 'PURCHASE', 25.000, 25.000, v_p_item2_id, 'Nhập kho theo phiếu #' || v_purchase_id, NOW() - INTERVAL '1 day'),
    (v_ing_gao, 'PURCHASE', 50.000, 50.000, v_p_item3_id, 'Nhập kho theo phiếu #' || v_purchase_id, NOW() - INTERVAL '1 day');
END $$;

-- ---------------------------------------------------------------------
-- 8. Inventory Checks (1 phiếu kiểm kê điều chỉnh mẫu)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_user_inventory_id BIGINT;
    v_check_id BIGINT;
    v_check_item_id BIGINT;
    v_ing_duong BIGINT;
BEGIN
    SELECT id INTO v_user_inventory_id FROM users WHERE username = 'inventory01';
    SELECT id INTO v_ing_duong FROM ingredients WHERE name = 'Đường cát trắng';

    -- Tạo 1 phiếu kiểm kê
    INSERT INTO inventory_checks (checked_by, checked_at, note)
    VALUES (v_user_inventory_id, NOW() - INTERVAL '12 hours', 'Kiểm kê kho định kỳ cuối tuần')
    RETURNING id INTO v_check_id;

    -- Chi tiết chênh lệch: Đường cát trắng hệ thống 10.0kg, thực tế 9.5kg, lệch -0.5kg
    INSERT INTO inventory_check_items (inventory_check_id, ingredient_id, system_quantity, actual_quantity, difference, reason)
    VALUES (v_check_id, v_ing_duong, 10.000, 9.500, -0.500, 'Hao hụt tự nhiên trong quá trình lưu trữ')
    RETURNING id INTO v_check_item_id;

    -- Ghi sổ cái biến động kho do kiểm kê điều chỉnh
    INSERT INTO stock_movements (ingredient_id, movement_type, quantity_change, balance_after, inventory_check_item_id, note, occurred_at)
    VALUES (v_ing_duong, 'INVENTORY_ADJUSTMENT', -0.500, 9.500, v_check_item_id, 'Điều chỉnh theo phiếu kiểm kê #' || v_check_id, NOW() - INTERVAL '12 hours');
END $$;

-- ---------------------------------------------------------------------
-- 9. Expenses (2 khoản chi phí vận hành mẫu)
-- ---------------------------------------------------------------------
INSERT INTO expenses (name, amount, expense_date, note, created_by)
VALUES
('Tiền điện & nước tháng trước', 3500000.00, CURRENT_DATE - 5, 'Thanh toán hóa đơn EVN qua ngân hàng', (SELECT id FROM users WHERE username = 'owner')),
('Mua đồ dùng & khăn giấy lau bếp', 450000.00, CURRENT_DATE - 2, 'Mua bổ sung cho khu vực bếp', (SELECT id FROM users WHERE username = 'owner'));
