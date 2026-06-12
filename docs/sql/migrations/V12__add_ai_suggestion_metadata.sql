SET @add_ai_suggestion_model_provider_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN model_provider VARCHAR(64) NULL AFTER input_summary',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'model_provider'
);

PREPARE add_ai_suggestion_model_provider_column_stmt FROM @add_ai_suggestion_model_provider_column;
EXECUTE add_ai_suggestion_model_provider_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_model_provider_column_stmt;

SET @add_ai_suggestion_model_name_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN model_name VARCHAR(128) NULL AFTER model_provider',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'model_name'
);

PREPARE add_ai_suggestion_model_name_column_stmt FROM @add_ai_suggestion_model_name_column;
EXECUTE add_ai_suggestion_model_name_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_model_name_column_stmt;

SET @add_ai_suggestion_prompt_version_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN prompt_version VARCHAR(64) NULL AFTER model_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'prompt_version'
);

PREPARE add_ai_suggestion_prompt_version_column_stmt FROM @add_ai_suggestion_prompt_version_column;
EXECUTE add_ai_suggestion_prompt_version_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_prompt_version_column_stmt;

SET @add_ai_suggestion_output_schema_version_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN output_schema_version VARCHAR(64) NULL AFTER prompt_version',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'output_schema_version'
);

PREPARE add_ai_suggestion_output_schema_version_column_stmt FROM @add_ai_suggestion_output_schema_version_column;
EXECUTE add_ai_suggestion_output_schema_version_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_output_schema_version_column_stmt;

SET @add_ai_suggestion_validation_status_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN validation_status VARCHAR(32) NULL AFTER output_schema_version',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'validation_status'
);

PREPARE add_ai_suggestion_validation_status_column_stmt FROM @add_ai_suggestion_validation_status_column;
EXECUTE add_ai_suggestion_validation_status_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_validation_status_column_stmt;

SET @add_ai_suggestion_validation_error_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN validation_error VARCHAR(512) NULL AFTER validation_status',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'validation_error'
);

PREPARE add_ai_suggestion_validation_error_column_stmt FROM @add_ai_suggestion_validation_error_column;
EXECUTE add_ai_suggestion_validation_error_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_validation_error_column_stmt;

SET @add_ai_suggestion_input_snapshot_json_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN input_snapshot_json JSON NULL AFTER validation_error',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'input_snapshot_json'
);

PREPARE add_ai_suggestion_input_snapshot_json_column_stmt FROM @add_ai_suggestion_input_snapshot_json_column;
EXECUTE add_ai_suggestion_input_snapshot_json_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_input_snapshot_json_column_stmt;

SET @add_ai_suggestion_validated_output_json_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN validated_output_json JSON NULL AFTER input_snapshot_json',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'validated_output_json'
);

PREPARE add_ai_suggestion_validated_output_json_column_stmt FROM @add_ai_suggestion_validated_output_json_column;
EXECUTE add_ai_suggestion_validated_output_json_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_validated_output_json_column_stmt;

SET @add_ai_suggestion_raw_model_output_json_column = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE ai_operation_suggestion ADD COLUMN raw_model_output_json JSON NULL AFTER validated_output_json',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_operation_suggestion'
    AND column_name = 'raw_model_output_json'
);

PREPARE add_ai_suggestion_raw_model_output_json_column_stmt FROM @add_ai_suggestion_raw_model_output_json_column;
EXECUTE add_ai_suggestion_raw_model_output_json_column_stmt;
DEALLOCATE PREPARE add_ai_suggestion_raw_model_output_json_column_stmt;
