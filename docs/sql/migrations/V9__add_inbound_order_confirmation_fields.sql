-- Phase 2.5 Task 6.1: persist inbound-order confirmation state and the
-- requestId idempotency key before stock application and audit records are
-- wired in later subtasks. Idempotent, matching earlier guarded migrations.

SET @add_inbound_order_confirm_request_id_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD COLUMN confirm_request_id VARCHAR(128) NULL AFTER created_by_admin_username',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND column_name = 'confirm_request_id'
);

PREPARE add_inbound_order_confirm_request_id_column_stmt FROM @add_inbound_order_confirm_request_id_column;
EXECUTE add_inbound_order_confirm_request_id_column_stmt;
DEALLOCATE PREPARE add_inbound_order_confirm_request_id_column_stmt;

SET @add_inbound_order_confirmed_by_admin_user_id_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD COLUMN confirmed_by_admin_user_id BIGINT NULL AFTER confirm_request_id',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND column_name = 'confirmed_by_admin_user_id'
);

PREPARE add_inbound_order_confirmed_by_admin_user_id_column_stmt FROM @add_inbound_order_confirmed_by_admin_user_id_column;
EXECUTE add_inbound_order_confirmed_by_admin_user_id_column_stmt;
DEALLOCATE PREPARE add_inbound_order_confirmed_by_admin_user_id_column_stmt;

SET @add_inbound_order_confirmed_by_admin_username_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD COLUMN confirmed_by_admin_username VARCHAR(64) NULL AFTER confirmed_by_admin_user_id',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND column_name = 'confirmed_by_admin_username'
);

PREPARE add_inbound_order_confirmed_by_admin_username_column_stmt FROM @add_inbound_order_confirmed_by_admin_username_column;
EXECUTE add_inbound_order_confirmed_by_admin_username_column_stmt;
DEALLOCATE PREPARE add_inbound_order_confirmed_by_admin_username_column_stmt;

SET @add_inbound_order_confirmed_at_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD COLUMN confirmed_at TIMESTAMP NULL AFTER confirmed_by_admin_username',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND column_name = 'confirmed_at'
);

PREPARE add_inbound_order_confirmed_at_column_stmt FROM @add_inbound_order_confirmed_at_column;
EXECUTE add_inbound_order_confirmed_at_column_stmt;
DEALLOCATE PREPARE add_inbound_order_confirmed_at_column_stmt;

SET @add_inbound_order_confirm_request_id_key = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD UNIQUE KEY uk_inbound_order_confirm_request_id (confirm_request_id)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND index_name = 'uk_inbound_order_confirm_request_id'
);

PREPARE add_inbound_order_confirm_request_id_key_stmt FROM @add_inbound_order_confirm_request_id_key;
EXECUTE add_inbound_order_confirm_request_id_key_stmt;
DEALLOCATE PREPARE add_inbound_order_confirm_request_id_key_stmt;

SET @add_inbound_order_confirmed_at_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inbound_order ADD KEY idx_inbound_order_confirmed_at (confirmed_at)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inbound_order'
    AND index_name = 'idx_inbound_order_confirmed_at'
);

PREPARE add_inbound_order_confirmed_at_index_stmt FROM @add_inbound_order_confirmed_at_index;
EXECUTE add_inbound_order_confirmed_at_index_stmt;
DEALLOCATE PREPARE add_inbound_order_confirmed_at_index_stmt;
