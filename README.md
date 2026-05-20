# MiniMall Order

MiniMall Order is a Java 17, Spring Boot 3, and Spring Cloud microservice order
system. The local runtime is built around Docker Compose, MySQL, Redis,
RabbitMQ, Prometheus, Grafana, and seven Spring Boot service containers.

## Modules

| Module | Role |
| --- | --- |
| `common-core` | Shared `ApiResponse`, business exceptions, payment event DTOs, and RabbitMQ topology. |
| `common-auth` | JWT utilities, trusted user context, and servlet auth propagation support. |
| `api-gateway` | Browser-facing API entrypoint, canonical route forwarding, JWT validation, CORS, and rate limiting. |
| `user-service` | Registration, login, current-user lookup, and user persistence. |
| `product-service` | Product management APIs and Redis-backed product detail cache. |
| `inventory-service` | Inventory query, stock lock, release, deduct, and idempotent inventory records. |
| `order-service` | Order creation, cancellation, timeout handling, payment-success consumption, and downstream service integration. |
| `payment-service` | Payment creation/query, idempotent payment success, and payment-success event publishing. |
| `notification-service` | Payment-success notification consumer and notification log persistence. |

## Start From Zero

Prerequisites:

- JDK 17
- Maven
- Docker with Docker Compose v2

Create a local environment file and replace every `change-me-*` value:

```bash
cp .env.example .env
```

Start infrastructure first:

```bash
docker compose --env-file .env up -d mysql redis rabbitmq prometheus grafana
```

Apply the database schema when the MySQL volume is new or empty:

```bash
docker compose --env-file .env exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < docs/sql/migrations/V1__initial_schema.sql
```

Build the service jars:

```bash
mvn clean package -DskipTests
```

Start all Java services through the Compose `services` profile:

```bash
docker compose --env-file .env --profile services up -d --build
```

Check service health:

```bash
docker compose --env-file .env --profile services ps
curl --noproxy '*' -fsS http://127.0.0.1:8080/actuator/health
```

## Runtime Entrypoints

| Component | Local URL |
| --- | --- |
| API gateway | `http://127.0.0.1:${API_GATEWAY_PORT}` |
| RabbitMQ management | `http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT}` |
| Prometheus | `http://127.0.0.1:${PROMETHEUS_PORT}` |
| Grafana | `http://127.0.0.1:${GRAFANA_PORT}` |

The browser-facing API must use the gateway only. Future frontend or admin
clients should not call service ports such as `8101` through `8106` directly.

Canonical public API prefixes:

| Area | Gateway prefix |
| --- | --- |
| Users | `/api/users/**` |
| Products | `/api/products/**` |
| Inventories | `/api/inventories/**` |
| Orders | `/api/orders/**` |
| Payments | `/api/payments/**` |

Legacy service-prefix aliases are not frontend contracts.

## Documentation Map

- `docs/architecture.md`: module boundaries, request flow, data model, event flow, and API contract map.
- `docs/deployment.md`: local deployment, environment variables, startup order, health checks, migration, observability, and pressure testing.
- `docs/local-infrastructure.md`: Docker Compose dependency services and local command reference.
- `docs/api-gateway-contract.md`: stable gateway routes, authentication, CORS, and rate limiting contract.
- `docs/frontend-integration.md`: frontend-ready API usage guide and response envelope rules.
- `docs/sql/README.md`: schema migration layout and required table/constraint baseline.
- `docs/messaging/payment-success-event.md`: RabbitMQ payment success event topology and payload.
- `docs/observability.md`: Prometheus and Grafana provisioning and checks.
- `pressure/README.md`: k6 gateway pressure-test usage.

## Verification

Use these checks before handing over a local environment:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile services config --quiet
mvn clean package -DskipTests
```
