-- ---------------------------------------------------------------------
-- ROMS: V6 Migration - Promotions, Bills, Bill Items & Payments
-- ---------------------------------------------------------------------

-- 1. Table: promotions
CREATE TABLE promotions (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255)   NOT NULL,
    code            VARCHAR(50)    NOT NULL,               -- lưu IN HOA
    discount_type   VARCHAR(20)    NOT NULL
        CHECK (discount_type IN ('PERCENT', 'FIXED_AMOUNT')),
    discount_value  NUMERIC(12,2)  NOT NULL CHECK (discount_value > 0),
    max_uses        INT            CHECK (max_uses > 0),   -- NULL = không giới hạn
    start_date      DATE,                                  -- cả 2 NULL = không giới hạn thời gian
    end_date        DATE,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by      BIGINT         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_promotions_percent
        CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CONSTRAINT chk_promotions_period
        CHECK ((start_date IS NULL AND end_date IS NULL)
            OR (start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date))
);

CREATE UNIQUE INDEX uq_promotions_code ON promotions(code) WHERE is_deleted = FALSE;

-- 2. Table: bills
CREATE TABLE bills (
    id                      BIGSERIAL PRIMARY KEY,
    bill_code               VARCHAR(30)    NOT NULL UNIQUE,
    service_session_id      BIGINT         NOT NULL UNIQUE REFERENCES service_sessions(id),
    table_code              VARCHAR(20)    NOT NULL,        -- chốt lúc thanh toán
    amount_before_discount  NUMERIC(14,2)  NOT NULL,        -- khách phải trả trước giảm giá
    promotion_id            BIGINT         REFERENCES promotions(id),
    discount_amount         NUMERIC(14,2)  NOT NULL DEFAULT 0,
    total_amount            NUMERIC(14,2)  NOT NULL,        -- khách trả sau giảm giá
    created_by              BIGINT         NOT NULL REFERENCES users(id),
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bills_discount
        CHECK (discount_amount >= 0 AND discount_amount <= amount_before_discount),
    CONSTRAINT chk_bills_total
        CHECK (total_amount = amount_before_discount - discount_amount)
);

CREATE INDEX idx_bills_service_session_id ON bills(service_session_id);
CREATE INDEX idx_bills_created_at ON bills(created_at);

-- 3. Table: bill_items
CREATE TABLE bill_items (
    id                BIGSERIAL PRIMARY KEY,
    bill_id           BIGINT         NOT NULL REFERENCES bills(id),
    order_item_id     BIGINT         NOT NULL UNIQUE REFERENCES order_items(id),
    item_name         VARCHAR(255)   NOT NULL,
    quantity          INT            NOT NULL CHECK (quantity > 0),
    unit_price        NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    tax_rate          NUMERIC(5,2)   NOT NULL,
    is_vat_inclusive  BOOLEAN        NOT NULL
);
CREATE INDEX idx_bill_items_bill_id ON bill_items(bill_id);

-- 4. Table: payments
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT         NOT NULL REFERENCES bills(id),
    payment_method  VARCHAR(20)    NOT NULL
        CHECK (payment_method IN ('CASH', 'BANK_TRANSFER')),
    amount          NUMERIC(14,2)  NOT NULL CHECK (amount > 0)
);
CREATE INDEX idx_payments_bill_id ON payments(bill_id);

-- 5. Seed sample promotions for testing
INSERT INTO promotions (name, code, discount_type, discount_value, max_uses, is_active, created_by)
SELECT 'Khuyến mãi Khai trương 10%', 'WELCOME10', 'PERCENT', 10.00, 100, TRUE, id
FROM users WHERE username = 'owner'
LIMIT 1;

INSERT INTO promotions (name, code, discount_type, discount_value, max_uses, is_active, created_by)
SELECT 'Giảm giá 50K Tri ân', 'GIAM50K', 'FIXED_AMOUNT', 50000.00, 50, TRUE, id
FROM users WHERE username = 'owner'
LIMIT 1;

