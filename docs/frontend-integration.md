# Frontend Integration Guide

Task 20.1 records the browser-facing backend contract for a future MiniMall
frontend or admin console. The frontend must use `api-gateway` as the only API
entrypoint and must not call individual service ports such as `8101`, `8102`,
`8103`, `8104`, or `8105`.

Related source documents:

- `docs/api-gateway-contract.md`
- `docs/api-contract-review.md`
- `pressure/README.md`
- `docs/local-infrastructure.md`

## API Base URL

Local frontend development should point to the gateway:

```text
http://127.0.0.1:8080
```

Recommended frontend environment variables depend on the future frontend stack:

```text
VITE_MINIMALL_API_BASE_URL=http://127.0.0.1:8080
NEXT_PUBLIC_MINIMALL_API_BASE_URL=http://127.0.0.1:8080
```

Only one frontend-specific variable should be used by the actual frontend
project. Do not expose backend service variables such as `USER_SERVICE_BASE_URL`
or `SPRING_DATASOURCE_URL` to browser code.

## Gateway Routes

The gateway keeps browser-facing service namespaces stable and rewrites them to
the downstream service controller paths.

| Browser prefix | Downstream service | Rewrite result |
| --- | --- | --- |
| `/api/user/**` | `user-service` | `/api/**` |
| `/api/product/**` | `product-service` | `/api/**` |
| `/api/inventory/**` | `inventory-service` | `/api/**` |
| `/api/order/**` | `order-service` | `/api/**` |
| `/api/payment/**` | `payment-service` | `/api/**` |

No `/internal/**` route is configured at the gateway. Internal product,
inventory, message, and consumer contracts are service-to-service only.

## CORS

Gateway CORS applies to `/api/**`. Local defaults allow browser development from
localhost origins:

```text
MINIMALL_GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*
MINIMALL_GATEWAY_CORS_ALLOWED_METHODS=GET,POST,PUT,PATCH,DELETE,OPTIONS
MINIMALL_GATEWAY_CORS_ALLOWED_HEADERS=*
MINIMALL_GATEWAY_CORS_ALLOW_CREDENTIALS=false
MINIMALL_GATEWAY_CORS_MAX_AGE=1800
```

Preflight `OPTIONS` requests bypass authentication and rate limiting.

## Authentication Flow

Public browser endpoints:

- `POST /api/user/users/register`
- `POST /api/user/users/login`

All other `/api/**` endpoints require:

```text
Authorization: Bearer <jwt>
```

Login returns `data.tokenType` as `Bearer` and `data.token` as the JWT. The
frontend should join them as `<tokenType> <token>` for later requests.

The frontend must not send `X-User-Id` or `X-Username`. The gateway strips any
browser-supplied values and injects trusted values parsed from the JWT before
forwarding requests to downstream services.

## ApiResponse Contract

Every REST API response uses this envelope:

```json
{
  "success": true,
  "code": "0",
  "message": "success",
  "data": {}
}
```

Common error codes:

| Code | Meaning | Frontend handling |
| --- | --- | --- |
| `40000` | Bad request | Show request-level validation or action error. |
| `40001` | Validation failed | Display field or form validation message. |
| `40100` | Unauthorized | Clear auth state and send the user to login. |
| `40300` | Forbidden | Show permission denied. |
| `40400` | Resource not found | Show empty or not-found state. |
| `40900` | Conflict | Show business conflict, such as duplicate work or insufficient stock. |
| `40901` | Order has been cancelled | Refresh order state and block payment. |
| `40902` | Order status does not allow payment | Refresh order state and block payment. |
| `40903` | Payment already successful | Refresh payment or order detail. |
| `42900` | Too many requests | Back off and allow retry later. |
| `50000` | Internal server error | Show generic failure and preserve request context for support. |

Validation errors currently return a single `message` string, often with
`field: message` pairs separated by `; `.

## Stable Browser API Examples

### Register

```http
POST /api/user/users/register
Content-Type: application/json

{
  "username": "alice",
  "password": "local-password",
  "email": "alice@example.com",
  "phone": "13800000000"
}
```

Response `data` fields:

```json
{
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "phone": "13800000000",
  "status": "ACTIVE"
}
```

### Login

```http
POST /api/user/users/login
Content-Type: application/json

{
  "username": "alice",
  "password": "local-password"
}
```

Response `data` fields:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "alice"
}
```

### Current User

```http
GET /api/user/users/me
Authorization: Bearer <jwt>
```

Response `data` fields:

```json
{
  "userId": 1,
  "username": "alice"
}
```

### Product List And Detail

```http
GET /api/product/products?status=ON_SHELF&page=0&size=10
Authorization: Bearer <jwt>
```

`status` values: `ON_SHELF`, `OFF_SHELF`.

Page response fields:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0
}
```

```http
GET /api/product/products/{productId}
Authorization: Bearer <jwt>
```

Product `data` fields:

```json
{
  "productId": "SKU-001",
  "name": "Demo Product",
  "description": "Example",
  "price": 199.90,
  "status": "ON_SHELF",
  "createdAt": "2026-05-18T10:00:00",
  "updatedAt": "2026-05-18T10:00:00"
}
```

### Inventory Detail

```http
GET /api/inventory/inventories/{productId}
Authorization: Bearer <jwt>
```

`stockState` values: `IN_STOCK`, `OUT_OF_STOCK`, `INACTIVE`.

Response `data` fields:

```json
{
  "productId": "SKU-001",
  "availableStock": 100,
  "lockedStock": 0,
  "stockState": "IN_STOCK"
}
```

### Order Flow

```http
POST /api/order/orders
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "productId": "SKU-001",
  "quantity": 1,
  "idempotencyKey": "web-cart-20260518-0001"
}
```

Response `data` fields:

```json
{
  "orderNo": "ORD20260518000001",
  "status": "PENDING_PAYMENT",
  "expireAt": "2026-05-18T10:15:00",
  "totalAmount": 199.90,
  "productId": "SKU-001",
  "quantity": 1
}
```

```http
GET /api/order/orders/my?page=0&size=10
Authorization: Bearer <jwt>
```

```http
GET /api/order/orders/{orderNo}
Authorization: Bearer <jwt>
```

```http
POST /api/order/orders/{orderNo}/cancel
Authorization: Bearer <jwt>
```

Order `status` values: `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `CLOSED`,
`REFUNDED`.

### Payment Flow

```http
POST /api/payment/payments/{orderNo}/pay
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "channel": "MOCK",
  "idempotencyKey": "web-pay-20260518-0001"
}
```

`channel` currently supports `MOCK`.

```http
GET /api/payment/payments/{orderNo}
Authorization: Bearer <jwt>
```

Payment `data` fields:

```json
{
  "paymentNo": "PAY20260518000001",
  "orderNo": "ORD20260518000001",
  "status": "SUCCESS",
  "amount": 199.90,
  "channel": "MOCK",
  "paidAt": "2026-05-18T10:05:00"
}
```

Payment `status` values: `PENDING`, `SUCCESS`, `FAILED`.

## Admin And Backoffice Contracts

The following product management endpoints are reachable through the gateway and
are contract-ready for a future admin UI, but role-based admin authorization is
not implemented yet. Any real admin frontend must wait for an explicit RBAC
backend task or isolate access operationally.

| Purpose | Gateway endpoint |
| --- | --- |
| Create product | `POST /api/product/products` |
| Update product | `PUT /api/product/products/{productId}` |
| Put product on shelf | `POST /api/product/products/{productId}/on-shelf` |
| Take product off shelf | `POST /api/product/products/{productId}/off-shelf` |

Not yet exposed for admin/backoffice UI:

- Inventory create, stock adjustment, and stock audit APIs.
- Notification log query and resend APIs.
- User management, disable/enable user, and role management APIs.
- Payment callback, refund, and reconciliation APIs.
- Order management across all users.

## Readiness Checklist

| Area | Endpoint or contract | Status | Notes |
| --- | --- | --- | --- |
| User frontend | `POST /api/user/users/register` | Frontend UI ready | Public endpoint. |
| User frontend | `POST /api/user/users/login` | Frontend UI ready | Public endpoint; returns JWT. |
| User frontend | `GET /api/user/users/me` | Frontend UI ready | Requires gateway JWT auth. |
| Product frontend | `GET /api/product/products` | Frontend UI ready | Supports `status`, `page`, and `size`. |
| Product frontend | `GET /api/product/products/{productId}` | Frontend UI ready | Uses Redis cache-aside internally. |
| Inventory frontend | `GET /api/inventory/inventories/{productId}` | Frontend UI ready | Read-only stock snapshot. |
| Order frontend | `POST /api/order/orders` | Frontend UI ready | Requires `idempotencyKey`. |
| Order frontend | `GET /api/order/orders/my` | Frontend UI ready | Current user's order page. |
| Order frontend | `GET /api/order/orders/{orderNo}` | Frontend UI ready | Current user's order detail. |
| Order frontend | `POST /api/order/orders/{orderNo}/cancel` | Frontend UI ready | Current user's cancellable order. |
| Payment frontend | `POST /api/payment/payments/{orderNo}/pay` | Frontend UI ready | Requires idempotency for repeat clicks. |
| Payment frontend | `GET /api/payment/payments/{orderNo}` | Frontend UI ready | Current user's payment detail. |
| Product admin | `POST /api/product/products` | Admin UI contract ready | No RBAC yet. |
| Product admin | `PUT /api/product/products/{productId}` | Admin UI contract ready | No RBAC yet. |
| Product admin | `POST /api/product/products/{productId}/on-shelf` | Admin UI contract ready | No RBAC yet. |
| Product admin | `POST /api/product/products/{productId}/off-shelf` | Admin UI contract ready | No RBAC yet. |
| Product internal | `GET /internal/products/{productId}` | Internal only | Used by order-service, not routed by gateway. |
| Inventory internal | `POST /internal/inventories/deduct` | Internal only | Used by order-service, not routed by gateway. |
| Inventory internal | `POST /internal/inventories/release` | Internal only | Used by order-service, not routed by gateway. |
| Payment event | `payment.success` RabbitMQ event | Internal only | Consumed by order-service and notification-service. |
| Notification UI | Notification log query | Not yet exposed | Backend consumer writes logs, but no browser API exists. |
| Inventory admin | Stock adjustment and audit | Not yet exposed | Internal write APIs are not admin-safe. |
| Admin security | Role and permission checks | Not yet exposed | Gateway validates JWT only. |

## Browser Acceptance Checklist

Before a frontend PR claims backend readiness, verify:

1. Browser configuration uses only the gateway base URL.
2. Browser network traffic contains no requests to service ports `8101` through
   `8105`.
3. CORS preflight succeeds for the frontend origin.
4. Register or login returns `ApiResponse.success` and a JWT.
5. Authenticated requests send `Authorization: Bearer <jwt>`.
6. The browser does not send `X-User-Id` or `X-Username`.
7. Product list, product detail, and inventory detail use gateway paths.
8. Order creation uses a unique `idempotencyKey` per checkout attempt.
9. Payment uses a unique `idempotencyKey` per pay attempt and handles
   `40901`, `40902`, and `40903` as state-refresh cases.
10. All success and error handling reads `success`, `code`, `message`, and
    `data` from the `ApiResponse` envelope.

The existing k6 gateway script in `pressure/mini-mall-gateway.js` is the closest
backend-side smoke check for the browser-facing contract.
