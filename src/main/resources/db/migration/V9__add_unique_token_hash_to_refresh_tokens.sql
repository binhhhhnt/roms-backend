-- ---------------------------------------------------------------------
-- ROMS: V9 Migration - Add UNIQUE constraint on refresh_tokens.token_hash
-- ---------------------------------------------------------------------

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);
