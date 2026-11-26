ALTER TABLE restaurant_settings
    ADD COLUMN IF NOT EXISTS print_kitchen_order_ticket BOOLEAN NOT NULL DEFAULT FALSE;
