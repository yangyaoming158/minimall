-- Phase 2 Task 6.4: add an optimistic-locking version column to inventory so
-- concurrent admin adjustments cannot silently lose updates. Idempotent,
-- mirrors the information_schema guards used by earlier inventory migrations.

SET @add_inventory_version_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory'
    AND column_name = 'version'
);

PREPARE add_inventory_version_column_stmt FROM @add_inventory_version_column;
EXECUTE add_inventory_version_column_stmt;
DEALLOCATE PREPARE add_inventory_version_column_stmt;

UPDATE inventory
SET version = 0
WHERE version IS NULL;
