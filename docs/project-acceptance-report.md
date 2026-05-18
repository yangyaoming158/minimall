# MiniMall Order Project Acceptance Report

- Date: 2026-05-18
- Workspace: `/home/oslab/projects/mini-mall-order`
- Branch checked: `codex/checkpoint-current-work`
- Baseline commit checked before this report: `f435a25 docs: record final acceptance`
- Purpose: provide a concise current-state report that can be used as input for the next PRD and TaskMaster task tree.

## Acceptance Conclusion

MiniMall Order is accepted as a local backend MVP delivery.

The TaskMaster tree is fully complete: 20/20 top-level tasks are `done`, with no pending, in-progress, review, or blocked tasks. The codebase builds, the full Maven reactor test suite passes outside the restricted sandbox, the production packaging command passes, and Docker Compose configuration validates for both infrastructure-only and full service profiles.

The delivered scope is backend only. It provides stable browser-facing contracts through `api-gateway`, service-to-service internals, database schema, RabbitMQ event flow, observability assets, local deployment docs, pressure-test assets, and a recorded containerized runtime E2E acceptance run. It does not implement frontend pages, an admin console, RBAC, refund/reconciliation workflows, inventory admin operations, notification management APIs, or production CI/CD.

## Verification Snapshot

| Check | Command | Result |
| --- | --- | --- |
| TaskMaster next task | `node node_modules/task-master-ai/dist/task-master.js next` | Passed. No task available; all tasks completed or otherwise not actionable. |
| TaskMaster dashboard | `node node_modules/task-master-ai/dist/task-master.js list` | Passed. 20/20 top-level tasks done; 0 pending, 0 in progress, 0 review, 0 blocked. |
| Git working tree before report | `git status --short` | Clean. |
| Full test suite, restricted sandbox | `mvn test` | Failed in `user-service` because Mockito/Byte Buddy could not self-attach to the JVM in the restricted sandbox. This was an environment permission issue, not a business assertion failure. |
| Full test suite, unrestricted execution | `mvn test` | Passed. All 10 Maven reactor modules were `SUCCESS`. |
| Production package | `mvn clean package -DskipTests` | Passed. All modules built and Spring Boot service jars were repackaged. |
| Compose config, infrastructure | `docker compose --env-file .env.example config --quiet` | Passed. |
| Compose config, full services profile | `docker compose --env-file .env.example --profile services config --quiet` | Passed. |

Notes:

- The restricted sandbox failure was caused by `Could not initialize inline Byte Buddy mock maker` and `Could not self-attach to current VM`. The same `mvn test` command passed when rerun outside the sandbox.
- `mvn clean package -DskipTests` completed after the successful test run and regenerated service artifacts under ignored `target/` directories.

## Completed Task Tree

All TaskMaster top-level tasks are done:

1. 初始化 Maven 多模块项目与服务空壳
2. 配置本地基础设施 Docker Compose：MySQL、Redis、RabbitMQ
3. 建立数据库 migration/schema 基础结构
4. 实现 common-core：ApiResponse、ErrorCode、BusinessException、全局异常
5. 实现 common-auth：JwtUtils、UserContext、请求头约定
6. 实现 user-service：注册、登录、/me
7. 实现 product-service：商品 CRUD、上下架、详情缓存
8. 实现 inventory-service：库存查询、扣减、释放、流水、幂等
9. 实现 order-service 基础：订单模型、状态机、查询接口
10. 实现 order-service 下单链路：商品校验、库存扣减、防重复提交
11. 实现 order-service 取消链路：手动取消、库存释放、取消幂等
12. 定义 RabbitMQ 拓扑与事件契约
13. 实现 payment-service：支付单、幂等支付、发布 PaymentSuccessEvent
14. 实现 order-service 支付成功消费者：订单 PAID 流转、eventId 幂等
15. 实现 notification-service：通知日志、支付成功消费者、eventId 幂等
16. 实现 api-gateway：路由、JWT 鉴权、透传、CORS、日志、限流
17. 实现订单超时取消 scheduler
18. 接入基础可观测性：Actuator、Prometheus、Grafana
19. 编写 k6/JMeter 压测脚本与压测文档
20. 完善 README、部署文档、架构文档、最终验收

## Delivered Architecture

The project is a Java 17, Spring Boot 3.2.x, Spring Cloud, Maven multi-module backend system.

| Module | Delivered responsibility |
| --- | --- |
| `common-core` | Shared `ApiResponse`, `ErrorCode`, `BusinessException`, global exception handler, payment event DTO, and RabbitMQ topology declaration. |
| `common-auth` | JWT utility, JWT properties, user context holder, trusted propagation headers, and reusable request filter. |
| `api-gateway` | Browser-facing entrypoint, route rewrite, JWT validation, trusted user header injection, CORS, Redis-backed rate limiting, and request logging. |
| `user-service` | Registration, login, current-user lookup, password hashing, user persistence. |
| `product-service` | Product create/update/list/detail, on/off shelf operations, Redis cache-aside product detail cache, internal product detail API. |
| `inventory-service` | Public stock query, internal deduct/release APIs, inventory records, idempotent stock operations. |
| `order-service` | Order creation, query, cancellation, idempotency, product/inventory integration, timeout cancellation scheduler, payment success consumer. |
| `payment-service` | Pay/query flow, idempotent successful payment handling, payment success event publishing. |
| `notification-service` | Payment success event consumer, idempotent notification log persistence, notification status tracking. |

Runtime dependencies are MySQL, Redis, RabbitMQ, Prometheus, and Grafana. The intended browser entrypoint is only `api-gateway`.

## Stable Gateway Contracts

Browser-facing routes are routed through the gateway and rewritten to downstream `/api/**` controllers:

| Gateway prefix | Downstream service |
| --- | --- |
| `/api/user/**` | `user-service` |
| `/api/product/**` | `product-service` |
| `/api/inventory/**` | `inventory-service` |
| `/api/order/**` | `order-service` |
| `/api/payment/**` | `payment-service` |

Public unauthenticated routes:

- `POST /api/user/users/register`
- `POST /api/user/users/login`
- CORS preflight `OPTIONS` requests

Authenticated browser routes require `Authorization: Bearer <jwt>`.

Frontend-ready APIs:

| Area | Endpoint |
| --- | --- |
| User | `POST /api/user/users/register` |
| User | `POST /api/user/users/login` |
| User | `GET /api/user/users/me` |
| Product | `GET /api/product/products` |
| Product | `GET /api/product/products/{productId}` |
| Inventory | `GET /api/inventory/inventories/{productId}` |
| Order | `POST /api/order/orders` |
| Order | `GET /api/order/orders/my` |
| Order | `GET /api/order/orders/{orderNo}` |
| Order | `POST /api/order/orders/{orderNo}/cancel` |
| Payment | `POST /api/payment/payments/{orderNo}/pay` |
| Payment | `GET /api/payment/payments/{orderNo}` |

Product admin contract-ready endpoints exist but do not yet have RBAC:

- `POST /api/product/products`
- `PUT /api/product/products/{productId}`
- `POST /api/product/products/{productId}/on-shelf`
- `POST /api/product/products/{productId}/off-shelf`

Gateway security behavior:

- Browser-supplied `X-User-Id` and `X-Username` headers are stripped.
- Trusted user headers are injected after JWT validation.
- No `/internal/**` route is exposed through the gateway.
- Gateway errors return `ApiResponse`.
- Redis-backed rate limiting returns HTTP 429 with `ApiResponse`.

## Cross-Cutting Rules Confirmed

| Rule | Current state |
| --- | --- |
| REST APIs return `ApiResponse` | Controllers and gateway error writer use the shared envelope. |
| Business errors use `BusinessException` | Service-layer and auth/gateway error paths use `BusinessException` with stable `ErrorCode`s. |
| Status fields use enums | User, product, inventory, order, payment, notification, event, and channel statuses are enums. |
| Idempotency | Order creation/cancellation, inventory deduct/release, payment success, order payment-success consumer, and notification consumer implement idempotent behavior. |
| Secrets and ports | Runtime values are environment-driven via `.env.example`, Compose variables, and Spring configuration; no production secret should be committed. |
| Frontend scope | Backend contracts are frontend-ready; no frontend UI/build tooling is implemented. |

## Data And Event Contracts

Database baseline:

- Migration path: `docs/sql/migrations/V1__initial_schema.sql`
- Core tables: `users`, `products`, `inventory`, `inventory_records`, `orders`, `order_events`, `payments`, `notification_logs`
- Important uniqueness/idempotency constraints include username, product id, order number, payment number, payment order number, event id, notification event id, and inventory record order/change pair.

Payment success event:

- Exchange: `minimall.payment.exchange`
- Routing key: `payment.success`
- Order queue: `minimall.order.payment-success.queue`
- Notification queue: `minimall.notification.payment-success.queue`
- Producer: `payment-service`
- Consumers: `order-service`, `notification-service`
- Consumer deduplication key: `eventId`

Payload fields:

- `eventId`
- `orderNo`
- `paymentNo`
- `amount`
- `paidAt`
- `version`

## Recorded Runtime Acceptance

The checked-in `docs/final-acceptance.md` records a containerized runtime E2E acceptance run for Task 20.4 on 2026-05-18.

That run verified:

- Compose `services` profile startup with MySQL, Redis, RabbitMQ, Prometheus, Grafana, gateway, and all Java services.
- Full gateway flow: register, login, current user, product detail/list, inventory detail, create order, pay order, order detail, payment detail.
- Order moved to `PAID` after payment.
- Payment moved to `SUCCESS`.
- Notification log moved to `SENT`.
- Payment success RabbitMQ event was consumed by both `order-service` and `notification-service`.
- Prometheus scrape targets were up.
- Grafana datasource and dashboard were provisioned.

The checked-in `pressure/task19-live-result.md` records a live gateway pressure run:

- 5 VUs
- 60 seconds steady state
- about 40 QPS
- p95 response about 47 ms
- 0 transport failure rate
- 0 HTTP request failure rate
- 0 API business failure rate
- 0 API contract failure rate
- 0 negative inventory observations

## Documentation Assets

Primary handoff documents:

- `README.md`: quick start, module map, runtime entrypoints, verification commands.
- `docs/architecture.md`: topology, module boundaries, request flow, data model, event flow.
- `docs/deployment.md`: startup order, environment variables, health checks, migration, operations.
- `docs/frontend-integration.md`: gateway usage, `ApiResponse`, auth flow, stable frontend API examples.
- `docs/api-gateway-contract.md`: route/auth/CORS/rate-limit contract.
- `docs/api-contract-review.md`: resolved and remaining API contract notes.
- `docs/messaging/payment-success-event.md`: RabbitMQ topology and payload contract.
- `docs/observability.md`: Prometheus/Grafana setup and checks.
- `docs/final-acceptance.md`: containerized runtime E2E acceptance record.
- `pressure/README.md`: k6 pressure-test usage.
- `pressure/task19-live-result.md`: recorded pressure-test result.

## Known Boundaries For Next PRD

The following items are intentionally not complete in the current backend MVP and are good candidates for the next requirements document:

1. Frontend application: customer storefront, login/register screens, product browsing, checkout, order detail, payment status, cancellation UX.
2. Admin/backoffice: product management UI, inventory operations, order management, user management, notification log query/resend.
3. RBAC and permissions: admin role model, route-level authorization, service-side permission checks, gateway authorization policy.
4. Inventory admin APIs: stock create/adjustment/audit endpoints; current write APIs are internal service APIs, not admin-safe.
5. Payment lifecycle expansion: callback/webhook simulation, refund, reconciliation, failed payment states, payment channel abstraction beyond `MOCK`.
6. Order lifecycle expansion: refund/close workflows, shipment/fulfillment states, admin order search, order event timeline API.
7. Notification productization: query API, retry/resend, templates, provider abstraction, failure handling and alerting.
8. Production hardening: CI pipeline, image publishing, deployment environments, secrets management, migration tooling, backup/restore.
9. Observability expansion: structured logs, tracing, alert rules, SLO dashboards, business metrics.
10. API polish: decide final product status mutation style, consider moving shared `PageResponse` to `common-core` if multiple services need it.

## Suggested Next Task Tree Direction

Recommended next TaskMaster tree should start from explicit product goals rather than extending the old backend task tree blindly.

Suggested phases:

1. Define frontend or admin PRD scope and personas.
2. Add backend gaps needed by that UI, especially RBAC and admin-safe APIs.
3. Build the selected UI against `api-gateway` only.
4. Add integration tests and browser acceptance checks.
5. Add CI/CD and environment promotion if production-like delivery is required.

Do not expose service ports directly to frontend code. Do not route `/internal/**` through the gateway unless a future task explicitly redesigns the security model.
