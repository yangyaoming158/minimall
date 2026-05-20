# Architecture

This document summarizes the delivered MiniMall backend architecture and links
to the detailed contracts used by deployment, frontend integration, messaging,
and pressure testing.

## Runtime Topology

```text
Browser or k6 client
        |
        v
api-gateway :8080
        |
        +--> user-service :8101
        +--> product-service :8102
        +--> inventory-service :8103
        +--> order-service :8104
        +--> payment-service :8105

notification-service :8106 consumes payment events without a public gateway route.

Shared local dependencies:
MySQL, Redis, RabbitMQ, Prometheus, Grafana
```

The gateway is the only browser-facing API entrypoint. It validates JWTs,
removes browser-supplied `X-User-Id` and `X-Username` headers, injects trusted
user headers for authenticated downstream requests, applies CORS, and performs
Redis-backed rate limiting.

No `/internal/**` route is exposed through the gateway. Internal service APIs
are only for service-to-service calls inside the backend network.

## Module Responsibilities

| Module | Responsibility | Key dependencies |
| --- | --- | --- |
| `common-core` | Shared response envelope, errors, business exceptions, payment event DTO, RabbitMQ topology. | Spring Boot auto-configuration, Spring AMQP where used. |
| `common-auth` | JWT parsing/issuing, trusted user context, auth filters. | `common-core`, JWT secret from environment. |
| `api-gateway` | External API gateway, route rewriting, auth, CORS, rate limiting, request logging. | Redis, downstream service URLs. |
| `user-service` | Register, login, current-user lookup. | MySQL, JWT config. |
| `product-service` | Product CRUD/query and product detail cache. | MySQL, Redis. |
| `inventory-service` | Inventory query and idempotent stock operations. | MySQL. |
| `order-service` | Order lifecycle, downstream product/inventory calls, timeout cancellation, payment-success consumer. | MySQL, Redis, RabbitMQ, product-service, inventory-service. |
| `payment-service` | Payment creation/query, idempotent success handling, payment-success publisher. | MySQL, RabbitMQ. |
| `notification-service` | Payment-success notification consumer and notification logs. | MySQL, RabbitMQ. |

## Request Flow

Browser-facing routes use canonical gateway prefixes and are forwarded to the
matching downstream `/api/**` controller paths:

| Gateway prefix | Downstream service |
| --- | --- |
| `/api/users/**` | `user-service` |
| `/api/products/**` | `product-service` |
| `/api/inventories/**` | `inventory-service` |
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |

All REST APIs return the shared `ApiResponse` envelope. Business errors should
be represented with `BusinessException` and stable `ErrorCode` values. See
`docs/frontend-integration.md` for the frontend-facing envelope, error codes,
and example payloads.

## Data Model

The local MVP uses one MySQL schema configured by `MYSQL_DATABASE`. The initial
schema migration is:

```text
docs/sql/migrations/V1__initial_schema.sql
```

Core tables:

- `users`
- `products`
- `inventory`
- `inventory_records`
- `orders`
- `order_events`
- `payments`
- `notification_logs`

The baseline constraints and migration naming rules are documented in
`docs/sql/README.md`.

## Event Flow

`payment-service` publishes successful payment events to RabbitMQ after a
payment is committed as successful.

```text
payment-service
  exchange: minimall.payment.exchange
  routing key: payment.success
        |
        +--> minimall.order.payment-success.queue
        |       consumed by order-service
        |
        +--> minimall.notification.payment-success.queue
                consumed by notification-service
```

Consumers must deduplicate by `eventId` because RabbitMQ delivery is
at-least-once. The full topology, payload, producer rules, consumer rules, and
versioning policy are documented in `docs/messaging/payment-success-event.md`.

## Environment And Configuration Boundaries

Runtime configuration is environment-driven. Secrets, passwords, JWT signing
keys, hostnames, and ports must not be hardcoded in source or docs beyond
placeholder examples.

Compose service containers use Docker network names:

- `mysql:3306`
- `redis:6379`
- `rabbitmq:5672`
- `http://user-service:8101`
- `http://product-service:8102`
- `http://inventory-service:8103`
- `http://order-service:8104`
- `http://payment-service:8105`

Host-based development uses `127.0.0.1` and the published ports from `.env`.
The deployment mapping is documented in `docs/deployment.md`.

## Observability And Pressure Testing

Every Spring Boot service exposes:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

Prometheus scrapes Java service containers by Compose service name. Grafana is
provisioned with the `MiniMall Observability` dashboard. See
`docs/observability.md` for checks and Docker Desktop + WSL network notes.

Gateway pressure testing is defined in `pressure/mini-mall-gateway.js` and must
use `api-gateway` as the only API entrypoint. See `pressure/README.md` for k6
variables, thresholds, smoke runs, and bottleneck notes.

## Related Contracts

- Gateway contract: `docs/api-gateway-contract.md`
- Frontend integration: `docs/frontend-integration.md`
- API review notes: `docs/api-contract-review.md`
- Database schema: `docs/sql/README.md`
- Payment success event: `docs/messaging/payment-success-event.md`
- Deployment operations: `docs/deployment.md`
