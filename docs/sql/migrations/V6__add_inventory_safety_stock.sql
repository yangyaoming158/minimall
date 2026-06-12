-- Phase 2 Task 6.2: add a structured safety-stock threshold to inventory so the
-- admin API and later AI replenishment analysis can query low-stock products
-- without direct database access. Idempotent, mirrors V5's information_schema
-- guards so re-running against existing local data is safe.

SET @add_inventory_safety_stock_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory ADD COLUMN safety_stock INT NOT NULL DEFAULT 0 AFTER locked_stock',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory'
    AND column_name = 'safety_stock'
);

PREPARE add_inventory_safety_stock_column_stmt FROM @add_inventory_safety_stock_column;
EXECUTE add_inventory_safety_stock_column_stmt;
DEALLOCATE PREPARE add_inventory_safety_stock_column_stmt;

UPDATE inventory
SET safety_stock = 0
WHERE safety_stock IS NULL;

SET @add_inventory_safety_stock_check = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory ADD CONSTRAINT chk_inventory_safety_stock_non_negative CHECK (safety_stock >= 0)',
    'SELECT 1'
  )
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory'
    AND constraint_name = 'chk_inventory_safety_stock_non_negative'
);

PREPARE add_inventory_safety_stock_check_stmt FROM @add_inventory_safety_stock_check;
EXECUTE add_inventory_safety_stock_check_stmt;
DEALLOCATE PREPARE add_inventory_safety_stock_check_stmt;

SET @add_inventory_low_stock_index = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inventory ADD KEY idx_inventory_low_stock (status, safety_stock, available_stock)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'inventory'
    AND index_name = 'idx_inventory_low_stock'
);

PREPARE add_inventory_low_stock_index_stmt FROM @add_inventory_low_stock_index;
EXECUTE add_inventory_low_stock_index_stmt;
DEALLOCATE PREPARE add_inventory_low_stock_index_stmt;
