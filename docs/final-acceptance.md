# Final Acceptance Record

## Task 20.4 - Containerized Runtime E2E Acceptance

- Date: 2026-05-18
- Runtime: Docker Compose `services` profile with MySQL, Redis, RabbitMQ, Prometheus, Grafana, api-gateway, user-service, product-service, inventory-service, order-service, payment-service, and notification-service.
- Scope: Final delivery acceptance through the gateway and containerized runtime.

## Startup Verification

Verified the documented startup path against the local Compose project without
deleting existing Docker volumes:

```bash
docker compose --env-file .env.example --profile services config --quiet
mvn clean package -DskipTests
docker compose --env-file .env.example --profile services up -d --build
docker compose --env-file .env.example --profile services ps
```

Result:

- Maven full reactor package succeeded.
- Compose rebuilt all seven Java service images.
- Compose started all infrastructure and Java services.
- `docker compose ps` showed every container healthy.
- The checked-in database migration executed successfully against the Compose MySQL database.

## Acceptance Data

Seeded test data:

- Product: `TASK20-E2E-20260518203053`
- User: `task20_user_20260518203053`
- Order idempotency key: `task20-order-20260518203053`
- Payment idempotency key: `task20-pay-20260518203053`

Created runtime records:

- User ID: `3`
- Order number: `ORD20260518203432160FADDF00B`
- Payment number: `PAY20260518203446513627FC201`
- Notification event ID: `974025e7-dfc0-483a-a6f9-d35c9153cf95`

## Gateway Flow

All public API calls used `http://127.0.0.1:8080` through `api-gateway`.

Phase 0 API contract polish later moved browser-facing examples to canonical
gateway paths. The accepted runtime behavior is unchanged; current examples are:

| Step | Endpoint | Result |
| --- | --- | --- |
| Register | `POST /api/users/register` | `ApiResponse success`, user created. |
| Login | `POST /api/users/login` | `ApiResponse success`, Bearer JWT returned. |
| Current user | `GET /api/users/me` | `ApiResponse success`, user ID `3`. |
| Product detail | `GET /api/products/TASK20-E2E-20260518203053` | `ApiResponse success`, product `ON_SHELF`. |
| Product list | `GET /api/products?status=ON_SHELF&page=0&size=5` | `ApiResponse success`, seeded product present. |
| Inventory detail | `GET /api/inventories/TASK20-E2E-20260518203053` | `ApiResponse success`, stock initially available. |
| Create order | `POST /api/orders` | `ApiResponse success`, order `PENDING_PAYMENT`. |
| Pay order | `POST /api/payments/{orderNo}/pay` | `ApiResponse success`, payment `SUCCESS`. |
| Order detail | `GET /api/orders/{orderNo}` | `ApiResponse success`, order `PAID`. |
| Payment detail | `GET /api/payments/{orderNo}` | `ApiResponse success`, payment `SUCCESS`. |

## Database And Event Verification

Post-payment database checks:

| Check | Result |
| --- | --- |
| `orders.status` | `PAID` for `ORD20260518203432160FADDF00B`. |
| `payments.status` | `SUCCESS` for `PAY20260518203446513627FC201`. |
| `notification_logs.status` | `SENT` for payment success event `974025e7-dfc0-483a-a6f9-d35c9153cf95`. |
| `notification_logs.notification_type` | `PAYMENT_SUCCESS`. |
| `inventory` | `available_stock=18`, `locked_stock=2` after ordering quantity `2`. |

This confirms the payment success RabbitMQ event was consumed by both
`order-service` and `notification-service`.

## Observability Verification

Prometheus checks:

```bash
docker compose --env-file .env.example exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
docker compose --env-file .env.example exec -T prometheus promtool query instant http://127.0.0.1:9090 up
docker compose --env-file .env.example exec -T prometheus promtool query instant http://127.0.0.1:9090 'up{job="minimall-services",service="api-gateway"}'
docker compose --env-file .env.example exec -T prometheus promtool query instant http://127.0.0.1:9090 'jvm_memory_used_bytes{service="api-gateway"}'
```

Result:

- Prometheus config validation succeeded.
- `up=1` for `api-gateway`, `user-service`, `product-service`,
  `inventory-service`, `order-service`, `payment-service`, and
  `notification-service`.
- JVM memory metrics were present for `api-gateway`.

Grafana checks:

```bash
curl --noproxy '*' -fsS -u admin:change-me-grafana http://127.0.0.1:3000/api/search?query=MiniMall
curl --noproxy '*' -fsS -u admin:change-me-grafana http://127.0.0.1:3000/api/datasources/uid/minimall-prometheus
curl --noproxy '*' -fsS -u admin:change-me-grafana http://127.0.0.1:3000/api/dashboards/uid/minimall-observability
```

Result:

- Grafana returned the `MiniMall Observability` dashboard with UID
  `minimall-observability`.
- Grafana returned the Prometheus datasource with UID `minimall-prometheus`
  and URL `http://prometheus:9090`.
- Dashboard panels use the `minimall-prometheus` datasource and service metrics
  queries, including target status, HTTP rate, latency, JVM heap, JDBC
  connections, and CPU usage.

## Notes

- Host `curl` to `127.0.0.1` required elevated execution in this sandbox; Compose
  network checks from the Prometheus container succeeded without host networking.
- MySQL CLI printed the standard password warning during local verification.
- Existing Docker volumes were not deleted during this acceptance run. Unique
  test identifiers were used so the run remains traceable without disrupting
  previous task data.

## Result

Task 20.4 acceptance passed. No blocker remains for the documented local
containerized backend delivery path.
