# Phase 2 Admin Platform — Acceptance

- Date: 2026-05-31
- TaskMaster tag: `phase2-admin-platform`, task 15
- Branch: `codex/phase2-admin-platform`
- Scope: final integration regression + acceptance record for Phase 2 (tasks 1–14).

This document records the verification commands run, their results, the changed
scope, known limitations, and the final acceptance checklist mapping each
contract area (`docs/phase2-admin-api-contract.md` §11) to evidence.

## 1. Verification commands & results

| # | Command | Result |
| --- | --- | --- |
| 1 | `mvn clean package` (full reactor, **with tests**) | **BUILD SUCCESS** — 10/10 modules, **349 tests, 0 failures, 0 errors, 0 skipped**, 1:11 min |
| 2 | `docker compose config -q` | Valid (exit 0) |
| 3 | `cd admin-frontend && npm run test` | **41/41 passed** (9 spec files) |
| 4 | `cd admin-frontend && npm run build` (`vue-tsc --noEmit && vite build`) | Success (only the expected Element Plus >500 kB chunk-size warning) |
| 5 | `cd frontend && npm run test` | **36/36 passed** (Phase 1 customer flow unchanged) |
| 6 | `cd frontend && npm run build` | Success |
| 7 | gateway-contract audit (`X-User-Id/X-Username`, `/internal/`, ports `8101-8106`) over `frontend/src` + `admin-frontend/src` | **PASS** (0/0/0 on both) |

Per-module backend test rollups (sum = 349, all green): common-core, common-auth,
api-gateway, user-service, product-service, inventory-service, order-service (96),
payment-service (32), notification-service (20).

## 2. Acceptance checklist (contract §11 → evidence)

| Area | Minimum check | Evidence | Status |
| --- | --- | --- | --- |
| Gateway | 401 unauthenticated admin, 403 USER admin, ADMIN success, forged header stripping | api-gateway module tests + downstream `AdminAccess.requireAdmin()` enforced in every admin controller (all green in #1) | ✅ |
| Auth | JWT `role` claim gen/parse, `/api/users/me` role, `/api/admin/me` ADMIN-only | user-service module tests (#1) | ✅ |
| Product | `imageUrl` stored/returned, admin CRUD/status, legacy write ADMIN-gated, audit | product-service tests (#1); admin-frontend `ProductsView` specs (#3); customer `ProductCover` fallback specs (#5) | ✅ |
| Inventory | initialize conflict, adjust idempotency by `requestId`, no negative stock, records, audit | `AdminInventoryControllerTest` (#1); admin-frontend `InventoriesView` + `InventoryAdjustDialog` specs incl. fresh-requestId + in-flight guard + 40900 surfacing (#3) | ✅ |
| Order | admin filters, detail, event timeline, **no** status mutation | `AdminOrderControllerTest` (#1); admin-frontend `OrdersView` specs (#3) | ✅ |
| Payment | admin filters, detail, order lookup, **no** refund/reconciliation | `AdminPaymentControllerTest` (#1); admin-frontend `PaymentsView` specs (#3) | ✅ |
| Notification | filters, detail, **no** resend | `AdminNotificationControllerTest` (#1); admin-frontend `NotificationsView` specs (#3) | ✅ |
| Audit | filters, pagination, ADMIN protection, traceability fields | user-service audit module tests (#1); admin-frontend `AuditLogsView` specs surfacing sourceType/referenceNo/requestId + before/after snapshots (#3) | ✅ |
| Admin frontend | no service ports, no `/internal/**`, no spoofed trusted headers, build | gateway audit #7 PASS; build #4 | ✅ |
| Customer frontend | no admin routes, `imageUrl` fallback, Phase 1 flow regression | customer specs #5 (36/36); build #6; audit #7 | ✅ |

## 3. Changed scope (Phase 2)

- **Backend**: `users.role` + JWT `role` claim (user-service, common-auth); gateway
  `/api/admin/**` routing + ADMIN/role-header enforcement (api-gateway); admin-safe
  read/write APIs in product/inventory/order/payment/notification services; admin
  audit logging + query API (user-service) with shared enums/DTOs in common-core;
  `products.image_url`, `admin_operation_logs`, inventory record admin fields.
- **Admin frontend** (`admin-frontend/`): independent Vite/Vue/Pinia app — login,
  layout/router, products, inventories (list/init/adjust/records), orders, payments,
  notifications, audit-logs; gateway-only API client.
- **Customer frontend** (`frontend/`): single Phase 2 regression — `product.imageUrl`
  with placeholder fallback; otherwise unchanged Phase 1 storefront.

## 4. Known limitations

1. **Live end-to-end click-through not executed in this environment.** The full
   stack was not started (`docker compose up`) and no browser session drove the
   admin/customer apps. `docker compose config` was validated (#2), and all
   backend behaviours behind the manual checks (auth 401/403, forged-header
   stripping, inventory idempotency, no-mutation guarantees, Phase 1
   register→login→browse→order→pay→PAID) are covered by automated module + view
   tests. A manual smoke run is recorded below for an operator to execute.
2. **Frontend teleported components** (`el-select`, `el-date-picker`, drawer/dialog
   bodies) are unit-tested at the filter-param / API-call level and, where needed,
   via Teleport stubs; not full live-DOM click-through (happy-dom teleport
   brittleness). Full UI click-through is the manual smoke run.
3. **Audit list `page` param** is sent 0-based by the admin app to match the other
   admin lists; the endpoint takes manual `Integer page/size` rather than Spring
   `Pageable`. Confirm pagination offset during the manual smoke run.
4. **`safetyStock` is set only at inventory initialization and cannot be changed
   afterwards.** The locked contract has `POST /api/admin/inventories` (initialize,
   which conflicts 40900 if the inventory already exists) set `safetyStock`, while
   `POST /{productId}/adjust` only moves available stock (`delta`) and never touches
   `safetyStock`; there is no update-safety-stock endpoint. Inventories created
   outside admin initialization (e.g. seeded / created by the order flow) therefore
   keep `safetyStock = 0` with no in-product way to raise it. Found during the smoke
   test; accepted as a Phase 2 boundary. This is **not** an open gap — it is an
   explicitly planned next-phase item: Phase 2.5 "AI Inventory Readiness" **Task 3
   (Add Safety Stock And Low-Stock Query)** adds an admin API to set/update the
   threshold plus a `GET /api/admin/inventories/low-stock` endpoint (see
   `.taskmaster/docs/phase2-5-ai-inventory-readiness-prd.txt`). To exercise low-stock
   flagging in Phase 2, use a freshly admin-initialized product with `safetyStock > 0`.

## 5. Manual smoke run (for an operator)

```
docker compose up -d           # bring up gateway + services + infra
cd admin-frontend && npm run dev   # admin console
cd frontend && npm run dev         # customer storefront
```
Then verify: admin login (ADMIN ok / USER → 403); product create/edit incl.
imageUrl; inventory initialize (conflict on re-init), adjust ±, duplicate-submit
guard, records timeline; orders/payments/notifications/audit list + filter +
detail; and the Phase 1 customer flow register → login → browse → detail →
checkout → order → mock pay → order becomes PAID.

## 6. Forward-looking

AI inventory assistant boundaries and the Phase 2.5 backlog are recorded
separately in `docs/phase2.5-ai-inventory-backlog.md` (task 15.1).

## 7. Verdict

All automated backend + frontend verification passes; Phase 1 customer flow
regression is green; gateway-contract audit is clean. Phase 2 is **accepted**,
with the live click-through smoke run (§5) recommended before any deployment.
