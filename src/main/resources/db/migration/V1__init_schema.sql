-- =====================================================================
-- ROMS - Restaurant Operations Management System
-- Flyway Migration V1: Init Schema (PostgreSQL)
-- Naming convention: snake_case, BIGSERIAL primary keys
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. tables
-- Master data bàn ăn. Trạng thái "Trống/Đang phục vụ" KHÔNG lưu ở đây,
-- được suy ra real-time từ service_sessions đang ACTIVE.
-- ---------------------------------------------------------------------
CREATE TABLE tables (
    id          BIGSERIAL PRIMARY KEY,
    table_code  VARCHAR(20)  NOT NULL UNIQUE,
    capacity    INT          NOT NULL,
    area        VARCHAR(100),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 2. users
-- Tài khoản đăng nhập: Owner/Service Staff/Kitchen Staff/Inventory Staff
-- và TABLE_ACCOUNT (tài khoản tablet gắn cố định theo bàn qua table_id).
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(100)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255)  NOT NULL,
    full_name      VARCHAR(255),
    email          VARCHAR(255),
    role           VARCHAR(30)   NOT NULL
        CHECK (role IN ('OWNER', 'SERVICE_STAFF', 'KITCHEN_STAFF', 'INVENTORY_STAFF', 'TABLE_ACCOUNT')),
    table_id       BIGINT UNIQUE REFERENCES tables(id),
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role ON users(role);

-- ---------------------------------------------------------------------
-- 3. refresh_tokens
-- TABLE_ACCOUNT: tối đa 1 phiên đăng nhập active.
-- OWNER và STAFF: cho phép nhiều phiên trên nhiều thiết bị.
-- Refresh token được lưu dưới dạng hash và hỗ trợ rotation/revocation.-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(255)  NOT NULL,
    device_id   VARCHAR(255),
    expires_at  TIMESTAMP     NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ---------------------------------------------------------------------
-- 4. service_sessions
-- 1 lượt khách dùng 1 bàn. opened_by là tài khoản đã mở session: tài khoản
-- TABLE_ACCOUNT nếu khách sử dụng tablet tại bàn,
-- hoặc SERVICE_STAFF nếu nhân viên mở hộ.
-- ---------------------------------------------------------------------
CREATE TABLE service_sessions (
    id          BIGSERIAL PRIMARY KEY,
    table_id    BIGINT       NOT NULL REFERENCES tables(id),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    opened_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    opened_by   BIGINT       NOT NULL REFERENCES users(id),
    closed_at   TIMESTAMP,
    closed_by   BIGINT REFERENCES users(id)
);

CREATE INDEX idx_service_sessions_table_id ON service_sessions(table_id);
CREATE INDEX idx_service_sessions_status ON service_sessions(status);

-- Mỗi bàn chỉ có tối đa 1 session ACTIVE. Chốt chặn cuối khi 2 yêu cầu
-- mở session (tablet và staff, hoặc bấm đúp) đến cùng lúc.
CREATE UNIQUE INDEX uq_service_sessions_active_table
    ON service_sessions(table_id) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- 5. table_transfer_history
-- Lịch sử chuyển bàn của 1 session (chỉ để truy vết, không ảnh hưởng
-- orders/order_items vì chúng tham chiếu service_session, không phải table).
-- ---------------------------------------------------------------------
CREATE TABLE table_transfer_history (
    id                   BIGSERIAL PRIMARY KEY,
    service_session_id   BIGINT    NOT NULL REFERENCES service_sessions(id),
    from_table_id        BIGINT    NOT NULL REFERENCES tables(id),
    to_table_id           BIGINT    NOT NULL REFERENCES tables(id),
    transferred_by       BIGINT    NOT NULL REFERENCES users(id),
    transferred_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_table_transfer_history_session_id ON table_transfer_history(service_session_id);

-- ---------------------------------------------------------------------
-- 6. categories
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100)  NOT NULL,
    description    VARCHAR(255),
    display_order  INT           NOT NULL DEFAULT 0,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 7. menu_items
-- is_featured: Owner chọn món nổi bật,
-- độc lập với Best-Selling Dishes Analytics.
-- ---------------------------------------------------------------------
CREATE TABLE menu_items (
    id                   BIGSERIAL PRIMARY KEY,
    category_id          BIGINT         NOT NULL REFERENCES categories(id),
    name                 VARCHAR(255)   NOT NULL,
    description          TEXT,
    price                NUMERIC(12,2)  NOT NULL CHECK (price >= 0),
    status               VARCHAR(30)    NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'TEMPORARILY_UNAVAILABLE', 'DISCONTINUED')),
    is_featured          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_items_category_id ON menu_items(category_id);
CREATE INDEX idx_menu_items_status ON menu_items(status);

-- ---------------------------------------------------------------------
-- 8. menu_item_images
-- ---------------------------------------------------------------------
CREATE TABLE menu_item_images (
    id             BIGSERIAL PRIMARY KEY,
    menu_item_id   BIGINT        NOT NULL REFERENCES menu_items(id),
    image_url      VARCHAR(500)  NOT NULL,
    display_order  INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_item_images_menu_item_id ON menu_item_images(menu_item_id);

-- ---------------------------------------------------------------------
-- 9. orders
-- Không lưu table_id trực tiếp - đi qua service_session -> table.
-- created_by: tài khoản tablet của bàn hoặc tài khoản staff order hộ.
-- termination_reason/by/at dùng chung cho cả REJECTED lẫn CANCELLED.
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                    BIGSERIAL PRIMARY KEY,
    service_session_id    BIGINT        NOT NULL REFERENCES service_sessions(id),
    created_by            BIGINT        NOT NULL REFERENCES users(id),
    status                VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COOKING', 'READY', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    termination_reason    VARCHAR(255),
    terminated_by         BIGINT REFERENCES users(id),
    terminated_at         TIMESTAMP,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_service_session_id ON orders(service_session_id);
CREATE INDEX idx_orders_status ON orders(status);

-- ---------------------------------------------------------------------
-- 10. order_items
-- unit_price: snapshot giá tại thời điểm đặt, không đổi theo menu_items.
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT         NOT NULL REFERENCES orders(id),
    menu_item_id         BIGINT         NOT NULL REFERENCES menu_items(id),
    quantity             INT            NOT NULL CHECK (quantity > 0),
    unit_price           NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    note                 VARCHAR(255),
    status               VARCHAR(30)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COOKING', 'READY', 'CANCELLED')),
    termination_reason   VARCHAR(255),
    terminated_by        BIGINT REFERENCES users(id),
    terminated_at        TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_menu_item_id ON order_items(menu_item_id);
CREATE INDEX idx_order_items_status ON order_items(status);

-- ---------------------------------------------------------------------
-- 11. ingredients
-- current_stock là giá trị tồn hiện tại (truy vấn nhanh); lịch sử biến
-- động đầy đủ nằm ở stock_movements.
-- ---------------------------------------------------------------------
CREATE TABLE ingredients (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255)   NOT NULL,
    unit             VARCHAR(20)    NOT NULL,
    current_stock    NUMERIC(12,3)  NOT NULL DEFAULT 0,
    minimum_stock    NUMERIC(12,3),
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 12. recipe_items
-- Không có cột unit riêng - luôn dùng ingredients.unit để tránh lệch.
-- ---------------------------------------------------------------------
CREATE TABLE recipe_items (
    id                  BIGSERIAL PRIMARY KEY,
    menu_item_id        BIGINT         NOT NULL REFERENCES menu_items(id),
    ingredient_id       BIGINT         NOT NULL REFERENCES ingredients(id),
    quantity_required   NUMERIC(12,3)  NOT NULL CHECK (quantity_required > 0),
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE (menu_item_id, ingredient_id)
);

CREATE INDEX idx_recipe_items_menu_item_id ON recipe_items(menu_item_id);

-- ---------------------------------------------------------------------
-- 13. purchases
-- Ghi nhận hàng đã mua/đã mang về (không phải PO chờ nhận).
-- ---------------------------------------------------------------------
CREATE TABLE purchases (
    id              BIGSERIAL PRIMARY KEY,
    purchase_date   DATE           NOT NULL,
    total_amount    NUMERIC(14,2)  NOT NULL DEFAULT 0,
    created_by      BIGINT         NOT NULL REFERENCES users(id),
    note            VARCHAR(255),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_purchases_purchase_date ON purchases(purchase_date);

-- ---------------------------------------------------------------------
-- 14. purchase_items
-- ---------------------------------------------------------------------
CREATE TABLE purchase_items (
    id              BIGSERIAL PRIMARY KEY,
    purchase_id     BIGINT         NOT NULL REFERENCES purchases(id),
    ingredient_id   BIGINT         NOT NULL REFERENCES ingredients(id),
    quantity        NUMERIC(12,3)  NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    subtotal        NUMERIC(14,2)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_purchase_items_purchase_id ON purchase_items(purchase_id);
CREATE INDEX idx_purchase_items_ingredient_id ON purchase_items(ingredient_id);

-- ---------------------------------------------------------------------
-- 15. inventory_checks
-- Phiếu kiểm kê. 0 dòng inventory_check_items = "xác nhận không chênh
-- lệch" (card loại 2); >=1 dòng = "phiếu điều chỉnh" (card loại 1).
-- ---------------------------------------------------------------------
CREATE TABLE inventory_checks (
    id           BIGSERIAL PRIMARY KEY,
    checked_by   BIGINT     NOT NULL REFERENCES users(id),
    checked_at   TIMESTAMP  NOT NULL DEFAULT NOW(),
    note         VARCHAR(255),
    created_at   TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_checks_checked_at ON inventory_checks(checked_at);

-- ---------------------------------------------------------------------
-- 16. inventory_check_items
-- ---------------------------------------------------------------------
CREATE TABLE inventory_check_items (
    id                    BIGSERIAL PRIMARY KEY,
    inventory_check_id    BIGINT         NOT NULL REFERENCES inventory_checks(id),
    ingredient_id         BIGINT         NOT NULL REFERENCES ingredients(id),
    system_quantity       NUMERIC(12,3)  NOT NULL,
    actual_quantity       NUMERIC(12,3)  NOT NULL,
    difference            NUMERIC(12,3)  NOT NULL,
    reason                VARCHAR(255),
    created_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_check_items_check_id ON inventory_check_items(inventory_check_id);

-- ---------------------------------------------------------------------
-- 17. stock_movements
-- Sổ cái bất biến (event log) - chỉ occurred_at, không created_at/
-- updated_at. Đúng 1 trong 3 FK nguồn gốc phải có giá trị.
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
    id                          BIGSERIAL PRIMARY KEY,
    ingredient_id               BIGINT         NOT NULL REFERENCES ingredients(id),
    movement_type               VARCHAR(30)    NOT NULL
        CHECK (movement_type IN ('PURCHASE', 'ORDER_CONSUMPTION', 'INVENTORY_ADJUSTMENT')),
    quantity_change             NUMERIC(12,3)  NOT NULL,
    balance_after               NUMERIC(12,3)  NOT NULL,
    purchase_item_id            BIGINT REFERENCES purchase_items(id),
    order_item_id               BIGINT REFERENCES order_items(id),
    inventory_check_item_id     BIGINT REFERENCES inventory_check_items(id),
    note                        VARCHAR(255),
    occurred_at                 TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_stock_movements_single_source CHECK (
        (CASE WHEN purchase_item_id IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN order_item_id IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN inventory_check_item_id IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
);

CREATE INDEX idx_stock_movements_ingredient_id ON stock_movements(ingredient_id);
CREATE INDEX idx_stock_movements_occurred_at ON stock_movements(occurred_at);

-- ---------------------------------------------------------------------
-- 18. expenses
-- name: nội dung tự do do Owner nhập, không dùng bảng category riêng.
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255)   NOT NULL,
    amount           NUMERIC(14,2)  NOT NULL CHECK (amount >= 0),
    expense_date     DATE           NOT NULL,
    note             VARCHAR(255),
    created_by       BIGINT         NOT NULL REFERENCES users(id),
    cancelled_at     TIMESTAMP,
    cancelled_by     BIGINT REFERENCES users(id),
    cancel_reason    VARCHAR(255),
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_expense_date ON expenses(expense_date);