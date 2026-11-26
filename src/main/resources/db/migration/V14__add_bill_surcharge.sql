-- ROMS: V14 Migration - Add surcharge_amount and surcharge_note to bills

ALTER TABLE bills
    ADD COLUMN surcharge_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN surcharge_note   VARCHAR(255);

ALTER TABLE bills DROP CONSTRAINT IF EXISTS chk_bills_total;

ALTER TABLE bills ADD CONSTRAINT chk_bills_surcharge_amount
    CHECK (surcharge_amount >= 0);

ALTER TABLE bills ADD CONSTRAINT chk_bills_surcharge_note
    CHECK (surcharge_amount = 0 OR (surcharge_note IS NOT NULL AND LENGTH(TRIM(surcharge_note)) > 0));

ALTER TABLE bills ADD CONSTRAINT chk_bills_total
    CHECK (total_amount = amount_before_discount - discount_amount + surcharge_amount);
