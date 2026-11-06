-- ---------------------------------------------------------------------
-- ROMS: V7 Migration - Printing & Restaurant Information Configuration
-- ---------------------------------------------------------------------

ALTER TABLE restaurant_settings
    ADD COLUMN restaurant_name          VARCHAR(255) DEFAULT 'Nhà hàng ROMS',
    ADD COLUMN address                  VARCHAR(500) DEFAULT '123 Đường Ẩm Thực, TP. Hồ Chí Minh',
    ADD COLUMN phone                    VARCHAR(30)  DEFAULT '0901 234 567',
    ADD COLUMN receipt_footer           VARCHAR(500) DEFAULT 'Cảm ơn quý khách và hẹn gặp lại! Wifi: ROMS_VIP / Pass: 88888888',
    ADD COLUMN print_ready_ticket       BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN bill_printer_name        VARCHAR(255),
    ADD COLUMN bill_paper_size          VARCHAR(20)  NOT NULL DEFAULT 'K80'
        CHECK (bill_paper_size IN ('A5', 'K80', 'K58')),
    ADD COLUMN ready_ticket_printer_name VARCHAR(255),
    ADD COLUMN ready_ticket_paper_size  VARCHAR(20)  NOT NULL DEFAULT 'K80'
        CHECK (ready_ticket_paper_size IN ('A5', 'K80', 'K58'));

