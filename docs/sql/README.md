# Database Schema

This directory stores SQL schema assets for the MiniMall Order project.

## Layout

- `migrations/`: versioned SQL migrations for the local MySQL database.

## Naming

Migration files use Flyway-style names even before a migration tool is wired into the services:

```text
V<version>__<description>.sql
```

The initial schema migration for Task 3.2 must be:

```text
migrations/V1__initial_schema.sql
```

## Scope

Task 3 defines the MVP database baseline only. Service implementation tasks may add columns or indexes later when a concrete use case requires them.

The initial schema must cover these core tables:

- `users`
- `products`
- `inventory`
- `inventory_records`
- `orders`
- `order_events`
- `payments`
- `notification_logs`

Required unique constraints for the initial schema:

- `users.username`
- `products.product_id`
- `inventory.product_id`
- `orders.order_no`
- `order_events.event_id`
- `payments.payment_no`
- `payments.order_no`
- `notification_logs.event_id`
- `inventory_records(order_no, change_type)`
