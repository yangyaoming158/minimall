SET @drop_inventory_records_source_request_key = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE inventory_records DROP INDEX uk_inventory_records_source_request',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND index_name = 'uk_inventory_records_source_request'
);

PREPARE drop_inventory_records_source_request_key_stmt FROM @drop_inventory_records_source_request_key;
EXECUTE drop_inventory_records_source_request_key_stmt;
DEALLOCATE PREPARE drop_inventory_records_source_request_key_stmt;

SET @add_inventory_records_source_request_product_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD UNIQUE KEY uk_inventory_records_source_request_product (source_type, request_id, product_id)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND index_name = 'uk_inventory_records_source_request_product'
);

PREPARE add_inventory_records_source_request_product_key_stmt FROM @add_inventory_records_source_request_product_key;
EXECUTE add_inventory_records_source_request_product_key_stmt;
DEALLOCATE PREPARE add_inventory_records_source_request_product_key_stmt;
