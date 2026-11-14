-- =====================================================================
-- ROMS: V9 Migration - QR Ordering per Table & Remove TABLE_ACCOUNT
-- =====================================================================

-- 1. Toggle QR ordering in restaurant_settings
ALTER TABLE restaurant_settings
    ADD COLUMN qr_ordering_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Add unique qr_token per table (no QR image stored; URL generated from token)
ALTER TABLE tables
    ADD COLUMN qr_token VARCHAR(64) UNIQUE;

UPDATE tables
SET qr_token = gen_random_uuid()::text
WHERE qr_token IS NULL;

ALTER TABLE tables
    ALTER COLUMN qr_token SET NOT NULL;

-- 3. Allow service_sessions.opened_by to be NULL when opened by anonymous QR scan
ALTER TABLE service_sessions
    ALTER COLUMN opened_by DROP NOT NULL;

-- 4. QR order requests (staging requests before Service Staff confirmation)
CREATE TABLE qr_order_requests (
    id                  BIGSERIAL   PRIMARY KEY,
    service_session_id  BIGINT      NOT NULL REFERENCES service_sessions(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONFIRMATION'
        CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'CANCELLED')),
    confirmed_by        BIGINT REFERENCES users(id),
    confirmed_order_id  BIGINT REFERENCES orders(id),
    cancelled_by        BIGINT REFERENCES users(id),
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_order_requests_session_id ON qr_order_requests(service_session_id);
CREATE INDEX idx_qr_order_requests_status ON qr_order_requests(status);

CREATE TABLE qr_order_request_items (
    id                   BIGSERIAL      PRIMARY KEY,
    qr_order_request_id  BIGINT         NOT NULL REFERENCES qr_order_requests(id) ON DELETE CASCADE,
    menu_item_id         BIGINT         NOT NULL REFERENCES menu_items(id),
    quantity             INT            NOT NULL CHECK (quantity > 0),
    unit_price           NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    tax_rate             DECIMAL(5,2)   NOT NULL DEFAULT 8.00,
    is_vat_inclusive     BOOLEAN        NOT NULL DEFAULT TRUE,
    note                 VARCHAR(255)
);

CREATE INDEX idx_qr_order_request_items_request_id ON qr_order_request_items(qr_order_request_id);

-- 5. Safely clean up TABLE_ACCOUNT users and narrow users.role check constraint
DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users WHERE role = 'TABLE_ACCOUNT');

DELETE FROM users u
WHERE u.role = 'TABLE_ACCOUNT'
  AND NOT EXISTS (SELECT 1 FROM service_sessions ss WHERE ss.opened_by = u.id)
  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.created_by = u.id)
  AND NOT EXISTS (SELECT 1 FROM table_transfer_history tth WHERE tth.transferred_by = u.id);

UPDATE users
SET is_active = FALSE,
    table_id = NULL,
    role = 'SERVICE_STAFF'
WHERE role = 'TABLE_ACCOUNT';

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('OWNER', 'SERVICE_STAFF', 'KITCHEN_STAFF', 'INVENTORY_STAFF'));
