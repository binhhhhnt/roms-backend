-- ROMS: V15 Migration - Add session_code (S-YYYYMMDD-XXXX) to service_sessions

CREATE TABLE IF NOT EXISTS daily_sequences (
    seq_key    VARCHAR(40) PRIMARY KEY,
    last_value BIGINT      NOT NULL DEFAULT 0
);

ALTER TABLE service_sessions
    ADD COLUMN session_code VARCHAR(30);

WITH numbered AS (
    SELECT id,
           'S-' || TO_CHAR(opened_at, 'YYYYMMDD') || '-' ||
           LPAD(ROW_NUMBER() OVER (PARTITION BY DATE(opened_at) ORDER BY opened_at, id)::TEXT, 4, '0') AS code
    FROM service_sessions
)
UPDATE service_sessions s
SET session_code = n.code
FROM numbered n
WHERE s.id = n.id;

INSERT INTO daily_sequences (seq_key, last_value)
SELECT 'SESSION-' || TO_CHAR(opened_at, 'YYYYMMDD') AS seq_key,
       COUNT(*)::BIGINT AS last_value
FROM service_sessions
GROUP BY TO_CHAR(opened_at, 'YYYYMMDD')
ON CONFLICT (seq_key) DO UPDATE
    SET last_value = GREATEST(daily_sequences.last_value, EXCLUDED.last_value);

ALTER TABLE service_sessions
    ALTER COLUMN session_code SET NOT NULL;

ALTER TABLE service_sessions
    ADD CONSTRAINT uq_service_sessions_session_code UNIQUE (session_code);
