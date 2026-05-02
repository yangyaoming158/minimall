# SQL Migrations

Put versioned MySQL migration files in this directory.

## Execution

For local verification, execute migrations against the MySQL container started by Docker Compose. Use the values from `.env.example` unless you have copied them to `.env` and changed them locally.

Example command for Task 3.2 or 3.3:

```bash
docker compose --env-file .env.example exec -T mysql \
  mysql -uroot -pchange-me-root minimall_order < docs/sql/migrations/V1__initial_schema.sql
```

If using a local `.env`, replace `--env-file .env.example` with `--env-file .env` and use your local root password.

## Verification

Task 3.3 should verify both table existence and unique constraints in `information_schema`.

Minimum table check:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'minimall_order'
ORDER BY table_name;
```

Minimum unique-constraint check:

```sql
SELECT table_name, constraint_name
FROM information_schema.table_constraints
WHERE table_schema = 'minimall_order'
  AND constraint_type = 'UNIQUE'
ORDER BY table_name, constraint_name;
```

## Conventions

- Prefer `bigint` surrogate primary keys for internal rows.
- Keep external business identifiers, such as `order_no`, `payment_no`, and `event_id`, unique.
- Store status values as `varchar` columns; Java services will model them as enums.
- Use `created_at` and `updated_at` timestamps on mutable business tables.
- Keep SQL idempotent where practical with `CREATE TABLE IF NOT EXISTS`.
