-- Add soft delete flag for categories, menu_items, and users
ALTER TABLE categories ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_categories_is_deleted ON categories(is_deleted);
CREATE INDEX idx_menu_items_is_deleted ON menu_items(is_deleted);
CREATE INDEX idx_users_is_deleted ON users(is_deleted);
