-- ROMS: Create invoices table for billing and checkout with discounts.
-- Prerequisite: V1, V2, V3 migrations applied.

CREATE TABLE invoices (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_code        VARCHAR(50)   NOT NULL UNIQUE,
    service_session_id  BIGINT        NOT NULL UNIQUE REFERENCES service_sessions(id),
    subtotal            NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    discount_type       VARCHAR(20)   NOT NULL DEFAULT 'NONE'
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    discount_reason     VARCHAR(255),
    final_amount        NUMERIC(12,2) NOT NULL CHECK (final_amount >= 0),
    note                VARCHAR(255),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_service_session_id ON invoices(service_session_id);
CREATE INDEX idx_invoices_created_at ON invoices(created_at);

