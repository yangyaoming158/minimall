# MiniMall Gateway Pressure Test

This directory contains k6 assets for Task 19. The pressure test must use
`api-gateway` as the only entry point. Do not point `BASE_URL` at individual
service ports such as `8101`, `8102`, `8104`, or `8105`.

## Script

- `mini-mall-gateway.js`: k6 scenario for the browser-facing gateway contract.

The script covers:

- Login and current-user lookup.
- Product list and product detail.
- Inventory detail with a non-negative stock observation.
- Create order, order detail, cancel order, and post-cancel detail lookup.
- Create order for payment, payment creation, and payment detail lookup.

`payment-service` currently exposes payment creation and payment query endpoints
only. There is no external payment callback API in the current backend contract,
so the script does not call a callback path.

## Gateway Routes

The script uses frontend-facing gateway paths. Gateway route rewrites preserve
the downstream `/api/**` controller paths after the service prefix.

| Script path | Downstream controller path |
| --- | --- |
| `POST /api/user/users/login` | `POST /api/users/login` |
| `GET /api/user/users/me` | `GET /api/users/me` |
| `GET /api/product/products` | `GET /api/products` |
| `GET /api/product/products/{productId}` | `GET /api/products/{productId}` |
| `GET /api/inventory/inventories/{productId}` | `GET /api/inventories/{productId}` |
| `POST /api/order/orders` | `POST /api/orders` |
| `GET /api/order/orders/{orderNo}` | `GET /api/orders/{orderNo}` |
| `POST /api/order/orders/{orderNo}/cancel` | `POST /api/orders/{orderNo}/cancel` |
| `POST /api/payment/payments/{orderNo}/pay` | `POST /api/payments/{orderNo}/pay` |
| `GET /api/payment/payments/{orderNo}` | `GET /api/payments/{orderNo}` |

These paths match `docs/api-gateway-contract.md`.

## Prerequisites

Before running a real pressure test:

1. Copy `.env.example` to `.env` and replace local placeholder credentials.
2. Start MySQL, Redis, RabbitMQ, Prometheus, and Grafana as needed:

   ```bash
   docker compose --env-file .env up -d
   ```

3. Apply the database schema from `docs/sql/migrations/V1__initial_schema.sql`
   if the target database is empty.
4. Start `user-service`, `product-service`, `inventory-service`,
   `order-service`, `payment-service`, and `api-gateway` with matching
   datasource, Redis, RabbitMQ, JWT, and downstream service URL settings.
5. Ensure the test user exists and can log in through the gateway.
6. Ensure at least one product is `ON_SHELF` and has inventory available.

The script assumes the order and payment services can observe the created order
state through the current backend integration path. If MQ consumers or service
state sync are not running, payment requests can return expected business
failures such as `40400` or order-state conflict codes.

## Required Environment

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `BASE_URL` | yes | none | Gateway base URL, for example `http://127.0.0.1:8080`. |
| `USERNAME` | yes | none | Login username for the test user. |
| `PASSWORD` | yes | none | Login password for the test user. |
| `PRODUCT_ID` | no | discovered from list | Product to use for detail, order, inventory, and payment flows. |
| `PRODUCT_STATUS` | no | `ON_SHELF` | Product list status filter. |
| `PRODUCT_PAGE` | no | `0` | Product list page. |
| `PRODUCT_SIZE` | no | `10` | Product list size. |
| `QUANTITY` | no | `1` | Quantity used for each created order. |
| `PAYMENT_CHANNEL` | no | `MOCK` | Payment channel sent to `payment-service`. |
| `RUN_ID` | no | current timestamp | Prefix component for idempotency keys. |

## Load Shape And Thresholds

| Variable | Default | Meaning |
| --- | --- | --- |
| `VUS` | `5` | Target virtual users. |
| `RAMP_UP` | `15s` | Ramp-up duration. |
| `DURATION` | `1m` | Steady-state duration. |
| `RAMP_DOWN` | `15s` | Ramp-down duration. |
| `SLEEP_SECONDS` | `1` | Sleep after each iteration. |
| `P95_THRESHOLD_MS` | `1000` | k6 threshold for `http_req_duration` p95. |
| `TRANSPORT_ERROR_RATE_THRESHOLD` | `0.01` | Allowed rate of transport/server errors. |
| `API_CONTRACT_FAILURE_RATE_THRESHOLD` | `0.01` | Allowed rate of non-ApiResponse bodies. |
| `API_EXPECTED_FAILURE_RATE_THRESHOLD` | `0.05` | Allowed rate of unexpected ApiResponse results. |

## Flow Switches

| Variable | Default | Meaning |
| --- | --- | --- |
| `ENABLE_ORDER_FLOW` | `true` | Enable order creation flows. |
| `ENABLE_CANCEL_FLOW` | `true` | Enable cancel-order flow. |
| `ENABLE_PAYMENT_FLOW` | `true` | Enable payment flow. |
| `ENABLE_INVENTORY_CHECK` | `true` | Enable inventory detail check and negative-stock observation. |
| `PAYMENT_RETRY_ATTEMPTS` | `5` | Retry payment when payment-service has not observed the order yet. |
| `PAYMENT_RETRY_SLEEP_SECONDS` | `0.2` | Sleep between payment retries. |

## Path Overrides

The defaults follow the current gateway contract. Override these only when the
gateway contract changes:

- `LOGIN_PATH`
- `ME_PATH`
- `PRODUCT_LIST_PATH`
- `PRODUCT_DETAIL_PATH`
- `INVENTORY_DETAIL_PATH`
- `ORDER_CREATE_PATH`
- `ORDER_DETAIL_PATH`
- `ORDER_CANCEL_PATH`
- `PAYMENT_PAY_PATH`
- `PAYMENT_DETAIL_PATH`

## Token Strategy

`setup()` logs in once to validate credentials and discover `PRODUCT_ID` when it
is not provided. Each VU then logs in on its first iteration and caches the token
inside that VU. Later requests use:

```text
Authorization: <tokenType> <token>
```

The current login response returns `data.tokenType` as `Bearer`. If a request
returns ApiResponse code `40100`, the VU logs in again once and retries the
request.

Order and payment requests use idempotency keys built from `RUN_ID`, VU id, and
iteration number. Use a new `RUN_ID` for a distinct pressure-test run when you
need to avoid replaying previous order keys.

## ApiResponse Assertions

Every response must be JSON with this contract:

```json
{
  "success": true,
  "code": "0",
  "message": "success",
  "data": {}
}
```

The script records a contract failure when `success`, `code`, or `message` is
missing or has the wrong type.

Expected success:

- `success=true`
- `code="0"`
- `message="success"`

Typical expected business failures:

- `40001`: validation failed.
- `40100`: unauthorized; the script attempts one relogin and retry.
- `40400`: resource not found, often possible while payment-service has not
  observed order state yet.
- `40900`: conflict, for example insufficient stock or duplicate work.
- `40901`, `40902`, `40903`: order/payment state conflicts.

Transport errors, non-ApiResponse bodies, and unexpected business codes are
reported separately so business saturation can be distinguished from gateway or
service failures.

## Run With Local k6

```bash
BASE_URL=http://127.0.0.1:8080 USERNAME=load-user PASSWORD=local-password PRODUCT_ID=SKU-001 RUN_ID=local-001 k6 run pressure/mini-mall-gateway.js
```

Quick smoke run:

```bash
BASE_URL=http://127.0.0.1:8080 USERNAME=load-user PASSWORD=local-password PRODUCT_ID=SKU-001 VUS=1 DURATION=10s RAMP_UP=1s RAMP_DOWN=1s k6 run pressure/mini-mall-gateway.js
```

## Run With Docker k6

Use `host.docker.internal` when the gateway runs on the host or in WSL and the
k6 container can reach it through Docker Desktop:

```bash
docker run --rm -i -v /home/oslab/projects/mini-mall-order/pressure:/scripts -e BASE_URL=http://host.docker.internal:8080 -e USERNAME=load-user -e PASSWORD=local-password -e PRODUCT_ID=SKU-001 -e RUN_ID=local-001 grafana/k6 run /scripts/mini-mall-gateway.js
```

If Docker Desktop cannot reach the WSL-hosted gateway through
`host.docker.internal`, run k6 directly in WSL or use a local-only WSL IP for
manual verification. Do not commit a dynamic WSL IP into project configuration.

## Script Validation

Check JavaScript syntax with Node:

```bash
node --check pressure/mini-mall-gateway.js
```

Check that k6 can load the script and parse scenarios:

```bash
docker run --rm -v /home/oslab/projects/mini-mall-order/pressure:/scripts grafana/k6 inspect /scripts/mini-mall-gateway.js
```

## Summary Fields

The custom k6 summary prints:

- `qps`: request throughput from `http_reqs`.
- `averageResponseMs`: average `http_req_duration`.
- `p95ResponseMs`: p95 `http_req_duration`.
- `transportErrorRate`: request failures below the ApiResponse layer.
- `httpReqFailedRate`: k6 built-in failed request rate.
- `apiBusinessFailureRate`: rate of `success=false` ApiResponse bodies.
- `apiContractFailureRate`: non-ApiResponse or malformed ApiResponse rate.
- `apiExpectedResultFailureRate`: unexpected success/error result rate.
- `negativeInventoryObservedRate`: must remain `0`.
- Flow counters for order create, order cancel, payment success, and relogins.

Example shape:

```json
{
  "qps": 42.5,
  "averageResponseMs": 78.4,
  "p95ResponseMs": 210.7,
  "transportErrorRate": 0,
  "httpReqFailedRate": 0,
  "apiBusinessFailureRate": 0.02,
  "apiContractFailureRate": 0,
  "apiExpectedResultFailureRate": 0,
  "negativeInventoryObservedRate": 0,
  "totalRequests": 2550,
  "iterations": 300,
  "orderCreateSuccesses": 600,
  "orderCancelSuccesses": 300,
  "paymentSuccesses": 285,
  "reloginAttempts": 0
}
```

## Bottleneck Checklist

During a real run, record:

- Gateway p95 latency and 429 rate from rate limiting.
- User-service login latency and 401 relogin count.
- Product detail latency and Redis cache behavior.
- Inventory conflicts, stock-lock behavior, and `negativeInventoryObservedRate`.
- Order creation success rate, idempotency conflicts, and Redis lock latency.
- Payment success rate and order-state sync delay.
- MySQL CPU, slow queries, lock waits, and connection pool usage.
- Redis latency and error logs.
- RabbitMQ queue depth, consumer lag, and dead-letter count.
- JVM heap, GC pauses, process CPU, and service target health in Grafana.

## Observability Notes

Pressure-test correctness is determined by k6 request results. Prometheus and
Grafana are supporting signals.

When Java services run directly in WSL and Prometheus/Grafana run in Docker
Desktop, Prometheus targets using `host.docker.internal` can appear down because
Docker Desktop may resolve that hostname to the Windows/Docker host rather than
the WSL VM. This does not by itself mean the pressure script or business API is
failing.

For temporary local diagnosis, verify the service actuator endpoint inside WSL,
then test the same endpoint from the Prometheus container with the current WSL
IP. Do not commit the dynamic WSL IP. A stable Compose runtime for Java services
belongs to the later runtime delivery task.
