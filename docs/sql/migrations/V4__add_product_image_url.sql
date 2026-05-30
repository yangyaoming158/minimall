SET @add_product_image_url_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE products ADD COLUMN image_url VARCHAR(512) NULL AFTER description',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'products'
    AND column_name = 'image_url'
);

PREPARE add_product_image_url_column_stmt FROM @add_product_image_url_column;
EXECUTE add_product_image_url_column_stmt;
DEALLOCATE PREPARE add_product_image_url_column_stmt;
