-- ---------------------------------------------------------------------
-- ROMS: V11 Migration - Add UNIQUE constraint (and index) on refresh_tokens.token_hash
-- ---------------------------------------------------------------------

ALTER TABLE refresh_tokens
ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);
