-- ROMS: V12 Migration - Extract areas table from tables.area

CREATE TABLE areas (
    id        BIGSERIAL    PRIMARY KEY,
    name      VARCHAR(100) NOT NULL UNIQUE,
    is_active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uq_areas_name_lower ON areas (LOWER(name));

ALTER TABLE tables
    ADD COLUMN area_id BIGINT REFERENCES areas(id) ON DELETE RESTRICT;

CREATE INDEX idx_tables_area_id ON tables(area_id);

-- 1. Tạo các bản ghi area từ dữ liệu đang có trong tables (gom nhóm không phân biệt hoa/thường)
INSERT INTO areas (name)
SELECT DISTINCT ON (LOWER(TRIM(area))) TRIM(area)
FROM tables
WHERE area IS NOT NULL
  AND TRIM(area) <> ''
ORDER BY LOWER(TRIM(area)), TRIM(area)
ON CONFLICT DO NOTHING;

-- 2. Nếu có bàn chưa gán khu vực, đảm bảo tồn tại khu mặc định "Khu vực chung"
INSERT INTO areas (name)
SELECT 'Khu vực chung'
WHERE EXISTS (
    SELECT 1 FROM tables WHERE area IS NULL OR TRIM(area) = ''
)
ON CONFLICT DO NOTHING;

-- 3. Map area_id cho từng bàn
UPDATE tables t
SET area_id = a.id
FROM areas a
WHERE t.area IS NOT NULL
  AND TRIM(t.area) <> ''
  AND LOWER(TRIM(t.area)) = LOWER(a.name);

UPDATE tables t
SET area_id = a.id
FROM areas a
WHERE (t.area IS NULL OR TRIM(t.area) = '')
  AND LOWER(a.name) = LOWER('Khu vực chung');

-- 4. Bắt buộc NOT NULL và xóa cột text cũ
ALTER TABLE tables
    ALTER COLUMN area_id SET NOT NULL;

ALTER TABLE tables
    DROP COLUMN area;
