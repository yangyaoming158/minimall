SET @modify_inventory_records_order_no_nullable = (
  SELECT IF(
    is_nullable = 'NO',
    'ALTER TABLE inventory_records MODIFY COLUMN order_no VARCHAR(64) NULL',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'order_no'
);

PREPARE modify_inventory_records_order_no_nullable_stmt FROM @modify_inventory_records_order_no_nullable;
EXECUTE modify_inventory_records_order_no_nullable_stmt;
DEALLOCATE PREPARE modify_inventory_records_order_no_nullable_stmt;

SET @add_inventory_records_request_id_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN request_id VARCHAR(128) NULL AFTER order_no',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'request_id'
);

PREPARE add_inventory_records_request_id_column_stmt FROM @add_inventory_records_request_id_column;
EXECUTE add_inventory_records_request_id_column_stmt;
DEALLOCATE PREPARE add_inventory_records_request_id_column_stmt;

SET @add_inventory_records_source_type_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''ORDER_DEDUCT'' AFTER change_type',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'source_type'
);

PREPARE add_inventory_records_source_type_column_stmt FROM @add_inventory_records_source_type_column;
EXECUTE add_inventory_records_source_type_column_stmt;
DEALLOCATE PREPARE add_inventory_records_source_type_column_stmt;

SET @add_inventory_records_reason_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN reason VARCHAR(512) NULL AFTER quantity',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'reason'
);

PREPARE add_inventory_records_reason_column_stmt FROM @add_inventory_records_reason_column;
EXECUTE add_inventory_records_reason_column_stmt;
DEALLOCATE PREPARE add_inventory_records_reason_column_stmt;

SET @add_inventory_records_admin_user_id_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN admin_user_id BIGINT NULL AFTER reason',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'admin_user_id'
);

PREPARE add_inventory_records_admin_user_id_column_stmt FROM @add_inventory_records_admin_user_id_column;
EXECUTE add_inventory_records_admin_user_id_column_stmt;
DEALLOCATE PREPARE add_inventory_records_admin_user_id_column_stmt;

SET @add_inventory_records_admin_username_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN admin_username VARCHAR(64) NULL AFTER admin_user_id',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'admin_username'
);

PREPARE add_inventory_records_admin_username_column_stmt FROM @add_inventory_records_admin_username_column;
EXECUTE add_inventory_records_admin_username_column_stmt;
DEALLOCATE PREPARE add_inventory_records_admin_username_column_stmt;

SET @add_inventory_records_reference_no_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD COLUMN reference_no VARCHAR(128) NULL AFTER admin_username',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND column_name = 'reference_no'
);

PREPARE add_inventory_records_reference_no_column_stmt FROM @add_inventory_records_reference_no_column;
EXECUTE add_inventory_records_reference_no_column_stmt;
DEALLOCATE PREPARE add_inventory_records_reference_no_column_stmt;

UPDATE inventory_records
SET request_id = order_no
WHERE (request_id IS NULL OR request_id = '')
  AND order_no IS NOT NULL
  AND order_no <> '';

UPDATE inventory_records
SET reference_no = order_no
WHERE (reference_no IS NULL OR reference_no = '')
  AND order_no IS NOT NULL
  AND order_no <> '';

UPDATE inventory_records
SET source_type = CASE
    WHEN change_type = 'RELEASE' THEN 'ORDER_RELEASE'
    ELSE 'ORDER_DEDUCT'
  END
WHERE source_type IS NULL
   OR source_type = ''
   OR source_type IN ('ORDER_DEDUCT', 'ORDER_RELEASE');

SET @add_inventory_records_request_id_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD KEY idx_inventory_records_request_id (request_id)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND index_name = 'idx_inventory_records_request_id'
);

PREPARE add_inventory_records_request_id_index_stmt FROM @add_inventory_records_request_id_index;
EXECUTE add_inventory_records_request_id_index_stmt;
DEALLOCATE PREPARE add_inventory_records_request_id_index_stmt;

SET @add_inventory_records_reference_no_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD KEY idx_inventory_records_reference_no (reference_no)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND index_name = 'idx_inventory_records_reference_no'
);

PREPARE add_inventory_records_reference_no_index_stmt FROM @add_inventory_records_reference_no_index;
EXECUTE add_inventory_records_reference_no_index_stmt;
DEALLOCATE PREPARE add_inventory_records_reference_no_index_stmt;

SET @add_inventory_records_source_request_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory_records ADD UNIQUE KEY uk_inventory_records_source_request (source_type, request_id)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory_records'
    AND index_name = 'uk_inventory_records_source_request'
);

PREPARE add_inventory_records_source_request_key_stmt FROM @add_inventory_records_source_request_key;
EXECUTE add_inventory_records_source_request_key_stmt;
DEALLOCATE PREPARE add_inventory_records_source_request_key_stmt;
