-- ---------------------------------------------------------------------
-- ROMS: V8 Migration - Relax promotions period check constraint
-- Allow open-ended start_date or end_date individually
-- ---------------------------------------------------------------------

ALTER TABLE promotions DROP CONSTRAINT IF EXISTS chk_promotions_period;

ALTER TABLE promotions ADD CONSTRAINT chk_promotions_period
    CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);

