CREATE TABLE IF NOT EXISTS inbound_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inbound_no VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  source VARCHAR(32) NOT NULL DEFAULT 'ADMIN_MANUAL',
  created_by_admin_user_id BIGINT NOT NULL,
  created_by_admin_username VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbound_order_inbound_no (inbound_no),
  KEY idx_inbound_order_status_created (status, created_at),
  KEY idx_inbound_order_source_created (source, created_at),
  KEY idx_inbound_order_admin_created (created_by_admin_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inbound_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inbound_no VARCHAR(64) NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  quantity INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbound_order_item_inbound_product (inbound_no, product_id),
  KEY idx_inbound_order_item_inbound_no (inbound_no),
  KEY idx_inbound_order_item_product_id (product_id),
  CONSTRAINT fk_inbound_order_item_inbound_no
    FOREIGN KEY (inbound_no) REFERENCES inbound_order (inbound_no),
  CONSTRAINT chk_inbound_order_item_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
