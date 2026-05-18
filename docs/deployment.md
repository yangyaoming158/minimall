# Deployment And Operations

This guide covers the local delivery path for MiniMall Order: dependencies,
service startup order, environment variables, health checks, database migration,
RabbitMQ events, observability, and pressure-test entrypoints.

## Prerequisites

- JDK 17
- Maven
- Docker with Docker Compose v2

Create a private `.env` file from the checked-in template and replace every
`change-me-*` value before starting long-lived services:

```bash
cp .env.example .env
```

Do not commit `.env`.

## Environment Variable Map

| Area | Variables | Used by |
| --- | --- | --- |
| MySQL container | `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Docker Compose `mysql`; Java datasource mapping. |
| Redis container | `REDIS_PORT`, `REDIS_PASSWORD` | Docker Compose `redis`; product cache; gateway rate limiting; order idempotency/locks. |
| RabbitMQ container | `RABBITMQ_AMQP_PORT`, `RABBITMQ_MANAGEMENT_PORT`, `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS` | Docker Compose `rabbitmq`; payment event publisher/consumers. |
| Observability | `PROMETHEUS_IMAGE`, `PROMETHEUS_PORT`, `GRAFANA_IMAGE`, `GRAFANA_PORT`, `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `GRAFANA_USERS_ALLOW_SIGN_UP`, `GRAFANA_PROMETHEUS_URL` | Prometheus and Grafana containers. |
| Java images | `JAVA_RUNTIME_IMAGE`, `JAVA_OPTS`, `MINIMALL_IMAGE_PREFIX`, `MINIMALL_IMAGE_TAG` | Compose `services` profile image build/runtime. |
| Host ports | `API_GATEWAY_PORT`, `USER_SERVICE_PORT`, `PRODUCT_SERVICE_PORT`, `INVENTORY_SERVICE_PORT`, `ORDER_SERVICE_PORT`, `PAYMENT_SERVICE_PORT`, `NOTIFICATION_SERVICE_PORT` | Published host ports for Java service containers. |
| Spring datasource | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Host-run Java services; Compose overrides these to `mysql:3306`. |
| Spring Redis | `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD` | Host-run Java services; Compose overrides these to `redis:6379`. |
| Spring RabbitMQ | `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, `SPRING_RABBITMQ_VIRTUAL_HOST` | Host-run Java services; Compose overrides these to `rabbitmq:5672`. |
| Auth | `MINIMALL_AUTH_JWT_SECRET`, `MINIMALL_AUTH_JWT_EXPIRE_SECONDS` | `common-auth`, `user-service`, `api-gateway`. |
| Gateway CORS | `MINIMALL_GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS`, `MINIMALL_GATEWAY_CORS_ALLOWED_METHODS`, `MINIMALL_GATEWAY_CORS_ALLOWED_HEADERS`, `MINIMALL_GATEWAY_CORS_ALLOW_CREDENTIALS`, `MINIMALL_GATEWAY_CORS_MAX_AGE` | `api-gateway`. |
| Gateway rate limit | `MINIMALL_GATEWAY_RATE_LIMIT_ENABLED`, `MINIMALL_GATEWAY_RATE_LIMIT_REPLENISH_RATE`, `MINIMALL_GATEWAY_RATE_LIMIT_BURST_CAPACITY`, `MINIMALL_GATEWAY_RATE_LIMIT_REQUESTED_TOKENS`, `MINIMALL_GATEWAY_RATE_LIMIT_KEY_PREFIX`, `MINIMALL_GATEWAY_RATE_LIMIT_FAIL_OPEN` | `api-gateway`. |
| Order operations | `MINIMALL_ORDER_TIMEOUT_ENABLED`, `MINIMALL_ORDER_TIMEOUT_FIXED_DELAY`, `MINIMALL_ORDER_TIMEOUT_BATCH_SIZE`, `ORDER_PAYMENT_EXPIRE_SECONDS`, `ORDER_IDEMPOTENCY_LOCK_TTL_SECONDS` | `order-service`. |

Inside the Compose `services` profile, service-to-service URLs are set to
Compose network names. For host-run services, use `127.0.0.1` and the published
ports from `.env`.

## Startup Order

For a new local environment, start infrastructure before Java services:

```bash
docker compose --env-file .env up -d mysql redis rabbitmq prometheus grafana
```

Wait until the dependency containers are healthy:

```bash
docker compose --env-file .env ps
```

Apply the schema when the MySQL data volume is new or empty:

```bash
docker compose --env-file .env exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < docs/sql/migrations/V1__initial_schema.sql
```

Build all service jars:

```bash
mvn clean package -DskipTests
```

Start Java services:

```bash
docker compose --env-file .env --profile services up -d --build
```

The Compose dependency order is:

1. `mysql`, `redis`, `rabbitmq`
2. `prometheus`
3. `grafana`
4. `user-service`, `product-service`, `inventory-service`, `payment-service`, `notification-service`
5. `order-service`
6. `api-gateway`

`order-service` waits for product, inventory, Redis, RabbitMQ, and MySQL health.
`api-gateway` waits for Redis and the routed downstream services.

## Health Checks

Check container health:

```bash
docker compose --env-file .env --profile services ps
```

Check the gateway from the host:

```bash
curl --noproxy '*' -fsS http://127.0.0.1:${API_GATEWAY_PORT}/actuator/health
```

If the shell does not expand `.env` values automatically, use the configured
port directly, for example `8080` with the default `.env.example` template.

Check a service from inside the Compose network:

```bash
docker compose --env-file .env exec -T prometheus wget -q -O - http://inventory-service:8103/actuator/health
```

Check Prometheus target status:

```bash
docker compose --env-file .env exec -T prometheus promtool query instant http://127.0.0.1:9090 up
```

## Database Operations

The migration assets live under:

```text
docs/sql/migrations/
```

The current baseline migration is:

```text
docs/sql/migrations/V1__initial_schema.sql
```

The schema contains the MVP tables and uniqueness constraints documented in
`docs/sql/README.md`. Re-run the migration only when it is safe for the target
database. Local disposable data can be reset with:

```bash
docker compose down -v
```

Then repeat the startup and migration steps.

## RabbitMQ Operations

RabbitMQ management is published at:

```text
http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT}
```

Use the local credentials from `.env`.

Payment success events use:

- Exchange: `minimall.payment.exchange`
- Routing key: `payment.success`
- Order queue: `minimall.order.payment-success.queue`
- Notification queue: `minimall.notification.payment-success.queue`

`payment-service` is the only producer. `order-service` and
`notification-service` are idempotent consumers keyed by `eventId`. The payload
contract and versioning rules are in `docs/messaging/payment-success-event.md`.

## Observability

Prometheus and Grafana are part of the default Compose stack:

```text
Prometheus: http://127.0.0.1:${PROMETHEUS_PORT}
Grafana:    http://127.0.0.1:${GRAFANA_PORT}
```

Prometheus scrapes Java service containers by service name:

- `api-gateway:8080`
- `user-service:8101`
- `product-service:8102`
- `inventory-service:8103`
- `order-service:8104`
- `payment-service:8105`
- `notification-service:8106`

Validate Prometheus configuration:

```bash
docker compose --env-file .env exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
```

Grafana provisions the `MiniMall Observability` dashboard from
`infra/grafana/dashboards/minimall-observability.json`. Detailed checks and
Docker Desktop + WSL network notes are in `docs/observability.md`.

## Pressure Test Entry Point

The k6 script is:

```text
pressure/mini-mall-gateway.js
```

It must target the gateway:

```bash
BASE_URL=http://127.0.0.1:8080 USERNAME=load-user PASSWORD=local-password PRODUCT_ID=SKU-001 VUS=1 DURATION=10s RAMP_UP=1s RAMP_DOWN=1s k6 run pressure/mini-mall-gateway.js
```

Before a real run, seed or create:

- A user that can log in through `POST /api/user/users/login`
- At least one `ON_SHELF` product
- Inventory for the selected product

Detailed k6 variables, thresholds, expected business failures, and bottleneck
notes are in `pressure/README.md`.

## Stop And Cleanup

Stop containers without deleting data:

```bash
docker compose down
```

Delete local data volumes:

```bash
docker compose down -v
```

Use volume deletion only for disposable local environments.
