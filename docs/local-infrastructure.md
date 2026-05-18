# Local Infrastructure

Task 2 provisions local development dependencies with Docker Compose:

- MySQL: `127.0.0.1:${MYSQL_PORT}`
- Redis: `127.0.0.1:${REDIS_PORT}`
- RabbitMQ AMQP: `127.0.0.1:${RABBITMQ_AMQP_PORT}`
- RabbitMQ management UI: `http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT}`
- Prometheus: `http://127.0.0.1:${PROMETHEUS_PORT}`
- Grafana: `http://127.0.0.1:${GRAFANA_PORT}`

For the full zero-to-running-services path, including database migration,
service container startup, health checks, and pressure-test entrypoints, see
`docs/deployment.md`.

Copy `.env.example` to `.env` before starting Docker Compose, then replace all `change-me-*` values locally. The real `.env` file is ignored by Git.

## Compose Variables

Docker Compose reads these variables from `.env`:

- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `RABBITMQ_AMQP_PORT`
- `RABBITMQ_MANAGEMENT_PORT`
- `RABBITMQ_DEFAULT_USER`
- `RABBITMQ_DEFAULT_PASS`
- `PROMETHEUS_IMAGE`
- `PROMETHEUS_PORT`
- `GRAFANA_IMAGE`
- `GRAFANA_PORT`
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`
- `GRAFANA_USERS_ALLOW_SIGN_UP`
- `GRAFANA_PROMETHEUS_URL`

## Spring Service Variables

Spring services should use these environment variable names when database, cache, and message queue integration is implemented:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `SPRING_DATA_REDIS_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`

For services running directly from WSL or the host, use `127.0.0.1` with the published ports. For services later running inside the Compose network, use service names `mysql`, `redis`, and `rabbitmq` with container ports `3306`, `6379`, and `5672`.

Observability setup and Docker Desktop + WSL scrape-target notes are documented in `docs/observability.md`.

Java service containers are available through the Compose `services` profile.
Build the Maven jars first, then run:

```bash
docker compose --env-file .env --profile services up -d --build
```

The service containers use Compose network names for dependencies:

- MySQL: `mysql:3306`
- Redis: `redis:6379`
- RabbitMQ: `rabbitmq:5672`
- Gateway downstream service URLs: `http://user-service:8101`,
  `http://product-service:8102`, `http://inventory-service:8103`,
  `http://order-service:8104`, and `http://payment-service:8105`

## Commands

Validate configuration:

```bash
docker compose --env-file .env.example config --quiet
```

Start local infrastructure:

```bash
docker compose --env-file .env up -d
```

Stop local infrastructure without deleting data:

```bash
docker compose down
```

Delete local infrastructure data volumes:

```bash
docker compose down -v
```
