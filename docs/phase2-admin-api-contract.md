# Phase 2 Admin API Contract

- Date: 2026-05-26
- TaskMaster tag: `phase2-admin-platform`
- Scope: Task 1, "Review admin API contract and current capabilities"

This document locks the Phase 2 admin-safe API boundary before implementation.
It records the current system baseline, the required `/api/admin/**` contract,
the independent `admin-frontend` boundary, and the regression rules that later
Phase 2 tasks must preserve.

Current endpoint index note: this file preserves the Phase 2 boundary and
historical contract decisions. The single current browser/API endpoint overview
is `docs/api-gateway-contract.md`.

## 1. Current Baseline

Phase 0 and Phase 1 established a working customer-facing flow through
`api-gateway`:

| Area | Current browser prefix | Downstream owner | Current state |
| --- | --- | --- | --- |
| Users | `/api/users/**` | `user-service` | Register, login, current user |
| Products | `/api/products/**` | `product-service` | Public list/detail plus existing write/status endpoints |
| Inventories | `/api/inventories/**` | `inventory-service` | Public stock detail |
| Orders | `/api/orders/**` | `order-service` | Create, cancel, my orders, detail |
| Payments | `/api/payments/**` | `payment-service` | Mock payment and payment query |

The gateway currently strips browser-supplied `X-User-Id` and `X-Username`,
injects trusted values after JWT validation, applies CORS/rate limiting to
`/api/**`, and blocks `/internal/**` at the gateway. It does not yet know
`X-User-Role`, `/api/admin/**`, or ADMIN authorization.

The Phase 1 customer frontend is intentionally customer-only. Its shipped routes
are catalog, login/register, checkout, orders, and payment. It must remain free
of admin login, admin menus, admin routes, and admin API clients.

## 2. Phase 2 Goals

Phase 2 adds a lightweight MiniMall admin platform:

1. Simplified role model based on `users.role = USER | ADMIN`.
2. Admin-safe backend APIs under `/api/admin/**`.
3. Gateway and downstream ADMIN enforcement.
4. Product management including `imageUrl`.
5. Inventory initialization, adjustment, and record query with idempotency.
6. Admin query APIs for orders, payments, notifications, and audit logs.
7. Basic admin operation audit logging.
8. A fully independent `admin-frontend` project.

Phase 2 must preserve the Phase 1 customer purchase flow:
register -> login -> browse products -> detail -> checkout -> order -> mock pay
-> order becomes `PAID`.

## 3. Role And Authentication Contract

### 3.1 User Role

Phase 2 uses a simple role field:

| Field | Values | Owner |
| --- | --- | --- |
| `users.role` | `USER`, `ADMIN` | `user-service` |

Rules:

1. Existing and newly registered customer users default to `USER`.
2. Admin accounts must have role `ADMIN`.
3. Java code must represent roles as an enum, not scattered string literals.
4. No menu-level, button-level, tenant-level, or resource-level permission
   model is introduced in Phase 2.

### 3.2 JWT Claims

JWTs issued after Phase 2 must include:

| Claim | Type | Notes |
| --- | --- | --- |
| `userId` | number | Existing trusted identity claim |
| `username` | string | Existing trusted identity claim |
| `role` | string enum | New; must be `USER` or `ADMIN` |

Existing tokens without `role` may be treated as invalid for admin access.
Customer-only endpoints may continue to reject missing or invalid claims with
`UNAUTHORIZED`.

### 3.3 Trusted Headers

The gateway owns trusted user propagation:

| Header | Injected by browser? | Injected by gateway? | Downstream use |
| --- | --- | --- | --- |
| `X-User-Id` | Never | Yes | User identity |
| `X-Username` | Never | Yes | Display/audit identity |
| `X-User-Role` | Never | Yes | ADMIN enforcement |

Incoming browser-supplied `X-User-Id`, `X-Username`, and `X-User-Role` must be
removed before authentication. Downstream services must trust only gateway-
injected values and must still verify ADMIN on admin APIs.

## 4. Gateway Contract

### 4.1 Public And Customer Routes

The existing customer routes remain stable:

| Prefix | Owner |
| --- | --- |
| `/api/users/**` | `user-service` |
| `/api/products/**` | `product-service` |
| `/api/inventories/**` | `inventory-service` |
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |

`GET /api/products/**` and `GET /api/inventories/**` remain public catalog
reads. Customer order and payment endpoints continue to require a valid JWT.

### 4.2 Admin Route Ownership

The gateway must add these admin route predicates and forward paths unchanged:

| Admin prefix | Downstream owner |
| --- | --- |
| `/api/admin/login` | `user-service` |
| `/api/admin/me` | `user-service` |
| `/api/admin/products/**` | `product-service` |
| `/api/admin/inventories/**` | `inventory-service` |
| `/api/admin/orders/**` | `order-service` |
| `/api/admin/payments/**` | `payment-service` |
| `/api/admin/notifications/**` | `notification-service` |
| `/api/admin/audit-logs/**` | `user-service` |

No `admin-service` is introduced in Phase 2.

### 4.3 Admin Authentication Behavior

| Request | Expected result |
| --- | --- |
| `OPTIONS /api/admin/**` | CORS preflight bypasses auth and rate limit |
| `POST /api/admin/login` without JWT | Allowed to reach `user-service`; credentials decide result |
| Any other `/api/admin/**` without JWT | HTTP 401, `ApiResponse` code `40100` |
| `/api/admin/**` with valid `USER` JWT | HTTP 403, `ApiResponse` code `40300` |
| `/api/admin/**` with valid `ADMIN` JWT | Forward to downstream with trusted user headers |
| Any browser-forged trusted header | Header stripped before downstream forwarding |
| `/internal/**` | HTTP 403; never routed to downstream through gateway |

Admin login is not a customer login variant. A valid `USER` credential posted to
`POST /api/admin/login` must return HTTP 403 `ApiResponse` and must not create an
admin-usable session.

## 5. Response And Error Contract

All REST APIs, including admin APIs, return `ApiResponse`.

Successful shape:

```json
{
  "success": true,
  "code": "0",
  "message": "success",
  "data": {}
}
```

Error shape:

```json
{
  "success": false,
  "code": "40300",
  "message": "Forbidden",
  "data": null
}
```

Admin APIs use existing `ErrorCode` values unless a later task has a concrete
reason to add an enum:

| Code | Meaning |
| --- | --- |
| `40000` | Bad request |
| `40001` | Validation failed |
| `40100` | Missing, invalid, or expired token |
| `40300` | Authenticated but not ADMIN, or internal path blocked |
| `40400` | Admin resource not found |
| `40900` | Business conflict, duplicate initialization, insufficient stock |
| `42900` | Gateway rate limit exceeded |
| `50000` | Unexpected server error |

Business failures must be raised as `BusinessException`. Status-like fields in
DTOs must use enums and serialize as stable enum names.

Paginated admin list endpoints use `PageResponse<T>`:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0
}
```

## 6. Admin API Surface

### 6.1 Admin Identity

| Method | Path | Owner | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/admin/login` | `user-service` | Login only if credentials belong to ADMIN |
| `GET` | `/api/admin/me` | `user-service` | Return current admin identity; ADMIN only |

Phase 2 does not define a server-side logout endpoint. The admin frontend clears
its local token.

### 6.2 Products

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/products` | Page, keyword, status filter |
| `GET` | `/api/admin/products/{productId}` | Admin product detail |
| `POST` | `/api/admin/products` | Create product |
| `PUT` | `/api/admin/products/{productId}` | Update name, description, price, `imageUrl` |
| `PUT` | `/api/admin/products/{productId}/status` | Unified status mutation |

Locked rules:

1. `ProductStatus` remains `ON_SHELF | OFF_SHELF`.
2. Product delete is out of scope.
3. `imageUrl` must be stored and returned by both admin and customer product
   responses.
4. Existing `/api/products` write/status endpoints must require ADMIN before
   they are removed or deprecated.
5. Customer product reads remain compatible with Phase 1.

### 6.3 Inventories

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/inventories` | Page, product keyword, stock state filter |
| `GET` | `/api/admin/inventories/{productId}` | Admin inventory detail |
| `POST` | `/api/admin/inventories` | Initialize inventory; conflict if already exists |
| `POST` | `/api/admin/inventories/{productId}/adjust` | Adjust available stock |
| `GET` | `/api/admin/inventories/{productId}/records` | Inventory record timeline |

Adjustment request fields:

| Field | Required | Notes |
| --- | --- | --- |
| `delta` | Yes | Positive or negative adjustment |
| `reason` | Yes | Human audit reason |
| `requestId` | Yes | Idempotency key |

Locked rules:

1. Available stock must never become negative.
2. Adjustment must be idempotent by `requestId`.
3. Adjustment must write `inventory_records` and admin audit logs.
4. Admin inventory APIs must not reuse `/internal/inventories/deduct` or
   `/internal/inventories/release` semantics as browser APIs.

### 6.4 Orders

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/orders` | Filters: orderNo, username/userId, status, productId, createdFrom, createdTo |
| `GET` | `/api/admin/orders/{orderNo}` | Admin order detail |
| `GET` | `/api/admin/orders/{orderNo}/events` | Timeline from `order_events` or minimal state history |

Phase 2 does not add admin order status mutation.

### 6.5 Payments

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/payments` | Filters: paymentNo, orderNo, status, paidFrom, paidTo |
| `GET` | `/api/admin/payments/{paymentNo}` | Payment detail |
| `GET` | `/api/admin/payments/order/{orderNo}` | Lookup by order |

Refunds, reconciliation, callbacks, and real payment providers are out of
scope.

### 6.6 Notifications

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/admin/notifications` | Filters: eventId, orderNo, status, channel, createdFrom, createdTo |
| `GET` | `/api/admin/notifications/{id}` | Notification log detail |

Notification resend is out of scope. Do not add `/resend` endpoints or UI.

### 6.7 Audit Logs

| Method | Path | Owner |
| --- | --- | --- |
| `GET` | `/api/admin/audit-logs` | `user-service` |

Filters:

- `adminUserId`
- `action`
- `resourceType`
- `resourceId`
- `createdFrom`
- `createdTo`
- `page`
- `size`

Audit log minimum fields:

| Field | Notes |
| --- | --- |
| `id` | Database identifier for audit detail only |
| `adminUserId` | Trusted user id |
| `adminUsername` | Trusted username |
| `action` | Enum |
| `resourceType` | Enum |
| `resourceId` | Business identifier when available |
| `summary` | Human-readable operation summary |
| `beforeSnapshot` | Required for update-like operations when practical |
| `afterSnapshot` | Required for create/update-like operations when practical |
| `ip` | Record when request context is available |
| `userAgent` | Record when request context is available |
| `createdAt` | Operation time |

Audit ownership is fixed:

1. `user-service` owns the `admin_operation_logs` table and query API.
2. Services performing admin write operations write audit records directly.
3. `common-core` may hold shared enums/DTOs/helpers.
4. No admin audit MQ pipeline is introduced in Phase 2.

## 7. Frontend Boundary

Phase 2 introduces a separate `admin-frontend` application.

| App | Directory | Purpose |
| --- | --- | --- |
| Customer frontend | `frontend` | Customer storefront only |
| Admin frontend | `admin-frontend` | Admin login and operations console |

Admin frontend rules:

1. It has its own `package.json`, Vite config, router, layout, API client,
   Pinia store, README, and build command.
2. Its internal routes are `/login`, `/`, `/products`, `/inventories`,
   `/orders`, `/payments`, `/notifications`, and `/audit-logs`.
3. `/` redirects to `/products`.
4. Protected routes call `/api/admin/me` to restore/check admin identity.
5. It must only call the gateway. It must not call service ports, `/internal/**`,
   or send `X-User-Id`, `X-Username`, or `X-User-Role`.

Customer frontend rules:

1. It remains the Phase 1 storefront.
2. It must not register admin routes, admin menus, admin login, or admin API
   clients.
3. Its only Phase 2 UI regression is showing `product.imageUrl` with the
   existing placeholder fallback.

## 8. Database And Migration Boundary

Phase 2 may add migrations for:

1. `users.role`
2. `products.image_url`
3. `admin_operation_logs`
4. Additional admin fields on `inventory_records`, if needed
5. Query indexes for admin list filters

Do not drop existing tables, remove existing constraints, or change completed
customer flow semantics. New migration work must preserve existing local data
where possible.

## 9. Non-Goals

Phase 2 does not include:

1. Full enterprise RBAC.
2. Multi-tenancy or organizations.
3. File upload, local static upload, or object storage.
4. Real third-party payment.
5. Refund, reconciliation, or payment callback.
6. Shipping, logistics, after-sales, coupons, promotions, or BI dashboards.
7. Notification resend.
8. Product delete.
9. Admin order status mutation.
10. Kubernetes, CI/CD, or production deployment.
11. Micro-frontend architecture or shared frontend package extraction.

## 10. Regression Boundaries

Later Phase 2 tasks must verify:

1. `master`, `phase0-api-polish`, and `phase1-customer-frontend` task trees are
   not modified for Phase 2 implementation.
2. Phase 1 customer routes and purchase flow continue to work.
3. Existing customer API paths remain canonical and gateway-only.
4. `/internal/**` remains blocked at the gateway.
5. Browser-supplied trusted headers are stripped.
6. Every admin response uses `ApiResponse`.
7. Admin list endpoints use `PageResponse`.
8. All status fields remain enum-backed.
9. Inventory/admin write paths are idempotent where required.
10. Admin frontend and customer frontend build independently.

## 11. Verification Checklist For Future Tasks

Each implementing task should include focused tests for its boundary:

| Area | Minimum check |
| --- | --- |
| Gateway | 401 unauthenticated admin, 403 USER admin, ADMIN success, forged header stripping |
| Auth | JWT role claim generation/parsing, `/api/users/me` role, `/api/admin/me` ADMIN only |
| Product | `imageUrl`, admin CRUD/status, legacy write ADMIN enforcement, audit |
| Inventory | initialize conflict, adjustment idempotency, no negative stock, records, audit |
| Order | admin filters, detail, timeline, no admin status mutation |
| Payment | admin filters, detail, order lookup, no refund/reconciliation |
| Notification | filters, detail, no resend |
| Audit | filters, pagination, ADMIN protection |
| Admin frontend | no service ports, no `/internal/**`, no trusted header spoofing, build |
| Customer frontend | no admin routes, image display fallback, Phase 1 flow regression |

Final Phase 2 acceptance must be recorded in `docs/phase2-admin-acceptance.md`.
