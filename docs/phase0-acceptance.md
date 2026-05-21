# Phase 0 API Contract Polish Acceptance

- Date: 2026-05-21
- TaskMaster tag: `phase0-api-polish`
- Scope: Final acceptance for canonical browser-facing API contract polish.

## Result

Phase 0 acceptance passed.

The `phase0-api-polish` task tree completed the canonical gateway route,
security boundary, public DTO, pagination, product mutation policy,
documentation, and browser-like script updates. Verification completed against
the current source tree and the running local Docker Compose services profile.

The stable browser entry point is `api-gateway`. Frontend callers must use only
these canonical prefixes:

| Area | Canonical browser prefix |
| --- | --- |
| Users | `/api/users/**` |
| Products | `/api/products/**` |
| Inventories | `/api/inventories/**` |
| Orders | `/api/orders/**` |
| Payments | `/api/payments/**` |

Legacy service-prefix gateway routes remain removed. `/internal/**` remains
service-to-service only and is explicitly rejected at the gateway.

## Commands And Results

| Check | Command | Result |
| --- | --- | --- |
| Focused gateway tests | `mvn -pl api-gateway -am test` | Passed. common-core 24 tests, common-auth 26 tests, api-gateway 26 tests. |
| Focused public API contract tests | `mvn -pl user-service,product-service,inventory-service,order-service,payment-service -am test` | Passed. common-core 24 tests, common-auth 26 tests, user-service 10 tests, product-service 13 tests, inventory-service 17 tests, order-service 68 tests, payment-service 17 tests. |
| Full test suite | `mvn test` | Passed for all 10 modules. |
| Full package | `mvn clean package -DskipTests` | Passed for all 10 modules. |
| Compose infra config | `docker compose --env-file .env.example config --quiet` | Passed. |
| Compose services config | `docker compose --env-file .env.example --profile services config --quiet` | Passed. |
| Pressure script syntax | `node --check pressure/mini-mall-gateway.js` | Passed. |
| Runtime availability | `docker compose --env-file .env.example --profile services ps` | All infrastructure and Java services were healthy. |

Notes:

- The local `task-master` binary is not on PATH, so the project-local CLI was
  used through `node node_modules/task-master-ai/dist/task-master.js`.
- This TaskMaster CLI version rejects `show --tag=phase0-api-polish`, so the
  current tag was explicitly switched to and verified as `phase0-api-polish`
  before `show` and `set-status` commands were run.
- Gateway tests printed the known Netty network-interface
  `Operation not permitted` warning in this sandbox, but the Maven test runs
  exited successfully.
- H2 duplicate-key warnings in repository tests were expected assertions for
  uniqueness and idempotency constraints.

## Canonical Runtime Smoke

The running Compose services profile was available and healthy, so a live
gateway smoke was run against `http://127.0.0.1:8080` using canonical paths.
Host `curl` to localhost required elevated execution in this sandbox.

Seeded data:

| Field | Value |
| --- | --- |
| Product ID | `PHASE0-SMOKE-20260521` |
| Username | `phase0_smoke_20260521_1959` |
| User ID | `5` |
| Order idempotency key | `phase0-smoke-order-20260521-1959` |
| Payment idempotency key | `phase0-smoke-pay-20260521-1959` |

Runtime flow:

| Step | Endpoint | Result |
| --- | --- | --- |
| Gateway health | `GET /actuator/health` | `UP` |
| Register | `POST /api/users/register` | `ApiResponse success`, user ID `5`. |
| Login | `POST /api/users/login` | `ApiResponse success`, Bearer JWT returned. |
| Current user | `GET /api/users/me` | `ApiResponse success`, user ID `5`. |
| Product list | `GET /api/products?status=ON_SHELF&page=0&size=5` | `ApiResponse success`, canonical `PageResponse`, seeded product present. |
| Product detail | `GET /api/products/PHASE0-SMOKE-20260521` | `ApiResponse success`, status `ON_SHELF`. |
| Inventory detail | `GET /api/inventories/PHASE0-SMOKE-20260521` | `ApiResponse success`, stock state `IN_STOCK`. |
| Create order | `POST /api/orders` | `ApiResponse success`, order `ORD20260521200213512ACA1F809`, status `PENDING_PAYMENT`. |
| Pay order | `POST /api/payments/ORD20260521200213512ACA1F809/pay` | `ApiResponse success`, payment `PAY20260521200228441001D43A9`, status `SUCCESS`. |
| Payment detail | `GET /api/payments/ORD20260521200213512ACA1F809` | `ApiResponse success`, status `SUCCESS`. |
| Order detail | `GET /api/orders/ORD20260521200213512ACA1F809` | `ApiResponse success`, status `PAID`. |
| Internal boundary | `GET /internal/products/PHASE0-SMOKE-20260521` | HTTP 403 `ApiResponse`, code `40300`, message `Internal API is not exposed`. |

Database verification:

| Check | Result |
| --- | --- |
| `orders` | `ORD20260521200213512ACA1F809` is `PAID` for user `5` and product `PHASE0-SMOKE-20260521`. |
| `payments` | `PAY20260521200228441001D43A9` is `SUCCESS` with channel `MOCK`. |
| `inventory` | `PHASE0-SMOKE-20260521` has `available_stock=49`, `locked_stock=1`, status `ACTIVE` after ordering quantity `1`. |

The host `k6` binary was not installed, so this final acceptance used
`node --check` for script syntax plus a live canonical `curl` smoke. Task 7
already verified the Docker k6 smoke path after rebuilding the services profile.

## Legacy Strategy

Legacy service-prefix paths are not supported as browser contracts:

- Removed: `/api/user/users/**`
- Removed: `/api/product/products/**`
- Removed: `/api/inventory/inventories/**`
- Removed: `/api/order/orders/**`
- Removed: `/api/payment/payments/**`

The accepted strategy is to keep one public browser contract before frontend
work begins. Existing frontend-facing docs and the pressure script now recommend
canonical paths only.

## Remaining Risks

- Real admin APIs are still out of scope. Admin routes need a Phase 2 PRD that
  defines RBAC, administrator identity, authorization rules, and audit policy.
- Product write/status mutation endpoints are not customer frontend APIs and are
  not admin-safe until RBAC exists. Future admin status mutation should use a
  dedicated `/api/admin/**` contract.
- Validation errors currently return one message string. A future frontend may
  need field-level error structures after UI requirements are known.
- The live smoke used existing local Docker volumes and unique test identifiers;
  it was non-destructive but not a clean-environment load or migration replay.
- Host localhost access from this Codex sandbox required elevated `curl`
  execution. Docker Compose network checks remain the preferred non-host path
  for deeper container-only verification.

## Follow-up Items

Phase 1 frontend:

- Use `api-gateway` as the only browser API base URL.
- Use canonical `/api/**` paths only.
- Send `Authorization: Bearer <jwt>` for protected endpoints.
- Do not send `X-User-Id` or `X-Username`; the gateway owns trusted header
  injection.
- Treat every response as `ApiResponse`.
- Treat paginated responses as `PageResponse` with `content`, `page`, `size`,
  `totalElements`, and `totalPages`.
- Do not call service ports or `/internal/**` paths from browser code.

Phase 2 admin:

- Define RBAC and admin identity before exposing `/api/admin/**`.
- Define product status mutation, audit logging, and permission boundaries.
- Decide whether admin DTOs may expose database identifiers or must keep the
  same business-identifier-only rule as customer APIs.
