# API Gateway Contract

This is the single current browser/API endpoint index for MiniMall Order.
Phase PRDs and phase-specific contracts may record historical decisions,
implementation boundaries, or task scope, but current browser-facing endpoint
ownership is maintained here. If an implementation change adds, removes, or
renames a browser-facing API, update this document in the same change.

Sources of truth for this index:

- `api-gateway/src/main/resources/application.yml`
- `api-gateway/src/main/java/com/minimall/gateway/security/GatewayAuthenticationFilter.java`
- Current `*Controller.java` mappings in each service
- `frontend/src/api/**` and `admin-frontend/src/api/**`

Request and response field details remain owned by the corresponding backend
DTOs and frontend types. This document owns path, method, route owner,
authentication, pagination/envelope conventions, and cross-service boundaries.

## 1. Gateway Route Ownership

The gateway exposes canonical browser paths and forwards requests to downstream
services without path rewrites.

| Browser prefix | Downstream service |
| --- | --- |
| `/api/users/**` | `user-service` |
| `/api/products/**` | `product-service` |
| `/api/inventories/**` | `inventory-service` |
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |
| `/api/admin/login` | `user-service` |
| `/api/admin/me` | `user-service` |
| `/api/admin/audit-logs/**` | `user-service` |
| `/api/admin/products/**` | `product-service` |
| `/api/admin/inventories/**` | `inventory-service` |
| `/api/admin/operation-stats/inventory-trends` | `inventory-service` |
| `/api/admin/inbound-orders/**` | `inventory-service` |
| `/api/admin/ai-suggestions/**` | `inventory-service` |
| `/api/admin/ai/**` | `inventory-service` |
| `/api/admin/orders/**` | `order-service` |
| `/api/admin/operation-stats/sales-by-product` | `order-service` |
| `/api/admin/payments/**` | `payment-service` |
| `/api/admin/notifications/**` | `notification-service` |

Downstream service base URLs are configured with `USER_SERVICE_BASE_URL`,
`PRODUCT_SERVICE_BASE_URL`, `INVENTORY_SERVICE_BASE_URL`,
`ORDER_SERVICE_BASE_URL`, `PAYMENT_SERVICE_BASE_URL`, and
`NOTIFICATION_SERVICE_BASE_URL`. Local defaults are development-only values.

Legacy service-prefix routes are removed rather than kept as deprecated
aliases. No `/internal/**` route is configured at the gateway.

## 2. Authentication And Trust Boundary

The gateway authenticates browser-facing `/api/**` requests with
`Authorization: Bearer <jwt>`, with these exceptions:

| Endpoint class | Auth behavior |
| --- | --- |
| `POST /api/users/register` | Public |
| `POST /api/users/login` | Public |
| `POST /api/admin/login` | Public admin credential exchange |
| `GET /api/products` and `GET /api/products/{productId}` | Public catalog read |
| `GET /api/inventories/{productId}` | Public stock read |
| `OPTIONS /api/**` | Public CORS preflight |

All `/api/admin/**` routes require an ADMIN JWT except `POST /api/admin/login`
and CORS preflight. Valid USER tokens receive HTTP 403.

Incoming browser-supplied `X-User-Id`, `X-Username`, `X-User-Role`, and
`X-Internal-Token` headers are always removed. For authenticated requests, the
gateway injects trusted identity headers parsed from the JWT and the configured
internal token before forwarding downstream. Browser code must send only the
Bearer token, never trusted propagation headers.

Authentication failures return `ApiResponse` JSON with stable `UNAUTHORIZED`
or `FORBIDDEN` codes.

## 3. Response And Pagination Rules

Every REST API response uses `ApiResponse<T>`:

```json
{
  "success": true,
  "code": "0",
  "message": "success",
  "data": {}
}
```

Paginated list endpoints return `ApiResponse<PageResponse<T>>`. Pagination is
Spring pageable based: `page` is 0-based, `size` controls page size, and `sort`
may be accepted where the controller uses `Pageable`.

Common error codes:

| Code | Meaning |
| --- | --- |
| `40000` | Bad request |
| `40001` | Validation failed |
| `40100` | Unauthorized |
| `40300` | Forbidden |
| `40400` | Resource not found |
| `40900` | Conflict |
| `40901` | Order has been cancelled |
| `40902` | Order status does not allow payment |
| `40903` | Payment already successful |
| `42900` | Too many requests |
| `50000` | Internal server error |

## 4. Current Endpoint Index

Auth labels:

| Label | Meaning |
| --- | --- |
| Public | No JWT required at gateway |
| Bearer | Any valid USER or ADMIN JWT unless the service applies stricter rules |
| ADMIN | ADMIN JWT required by gateway or downstream controller |

### 4.1 Customer And Public APIs

| Method | Gateway path | Service | Auth | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/api/users/register` | user-service | Public | Create customer user. |
| `POST` | `/api/users/login` | user-service | Public | Return Bearer JWT. |
| `GET` | `/api/users/me` | user-service | Bearer | Current authenticated user. |
| `GET` | `/api/products` | product-service | Public | Product page; supports optional `status` plus pageable params. |
| `GET` | `/api/products/{productId}` | product-service | Public | Product detail. |
| `GET` | `/api/inventories/{productId}` | inventory-service | Public | Product stock snapshot. |
| `POST` | `/api/orders` | order-service | Bearer | Create current user's order; request requires `idempotencyKey`. |
| `GET` | `/api/orders/my` | order-service | Bearer | Current user's order page. |
| `GET` | `/api/orders/{orderNo}` | order-service | Bearer | Current user's order detail. |
| `POST` | `/api/orders/{orderNo}/cancel` | order-service | Bearer | Cancel current user's cancellable order. |
| `POST` | `/api/payments/{orderNo}/pay` | payment-service | Bearer | Mock payment; frontend should send a stable pay `idempotencyKey`. |
| `GET` | `/api/payments/{orderNo}` | payment-service | Bearer | Current user's payment detail by order number. |

### 4.2 Legacy Product Write Surface

These endpoints are still routed because `/api/products/**` is routed to
`product-service`. They require downstream ADMIN checks, but current browser
and admin frontend code should prefer `/api/admin/products/**` for product
management.

| Method | Gateway path | Service | Auth | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/api/products` | product-service | ADMIN | Legacy product create path; prefer `POST /api/admin/products`. |
| `PUT` | `/api/products/{productId}` | product-service | ADMIN | Legacy product update path; prefer admin path. |
| `POST` | `/api/products/{productId}/on-shelf` | product-service | ADMIN | Legacy status action; prefer `PUT /api/admin/products/{productId}/status`. |
| `POST` | `/api/products/{productId}/off-shelf` | product-service | ADMIN | Legacy status action; prefer `PUT /api/admin/products/{productId}/status`. |

### 4.3 Admin Core APIs

| Method | Gateway path | Service | Auth | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/api/admin/login` | user-service | Public | Admin credential exchange; rejects non-admin accounts. |
| `GET` | `/api/admin/me` | user-service | ADMIN | Current admin identity. |
| `GET` | `/api/admin/audit-logs` | user-service | ADMIN | Audit-log page; filters include admin user, action, resource, request id, source, reference, and created time range. |
| `GET` | `/api/admin/products` | product-service | ADMIN | Product page; supports `keyword`, `status`, and pageable params. |
| `GET` | `/api/admin/products/{productId}` | product-service | ADMIN | Product detail. |
| `POST` | `/api/admin/products` | product-service | ADMIN | Product create. |
| `PUT` | `/api/admin/products/{productId}` | product-service | ADMIN | Product update. |
| `PUT` | `/api/admin/products/{productId}/status` | product-service | ADMIN | Product status update. |
| `GET` | `/api/admin/inventories` | inventory-service | ADMIN | Inventory page; supports `keyword`, `stockState`, `lowStock`, and pageable params. |
| `GET` | `/api/admin/inventories/low-stock` | inventory-service | ADMIN | Low-stock inventory page. |
| `GET` | `/api/admin/inventories/{productId}` | inventory-service | ADMIN | Inventory detail. |
| `POST` | `/api/admin/inventories` | inventory-service | ADMIN | Initialize inventory for a product. |
| `POST` | `/api/admin/inventories/{productId}/adjust` | inventory-service | ADMIN | Manual inventory adjustment; never called by AI code. |
| `PATCH` | `/api/admin/inventories/{productId}/safety-stock` | inventory-service | ADMIN | Update safety stock. |
| `GET` | `/api/admin/inventories/{productId}/records` | inventory-service | ADMIN | Inventory record timeline. |
| `GET` | `/api/admin/operation-stats/inventory-trends` | inventory-service | ADMIN | Inventory trend page; supports `createdFrom`, `createdTo`, pageable params. |
| `POST` | `/api/admin/inbound-orders/drafts` | inventory-service | ADMIN | Create inbound draft; no stock change. |
| `GET` | `/api/admin/inbound-orders` | inventory-service | ADMIN | Inbound order page; supports `status` and pageable params. |
| `GET` | `/api/admin/inbound-orders/{inboundNo}` | inventory-service | ADMIN | Inbound order detail. |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/cancel` | inventory-service | ADMIN | Cancel draft; no stock change. |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/confirm` | inventory-service | ADMIN | The only formal inbound stock-increase API. |
| `GET` | `/api/admin/ai-suggestions` | inventory-service | ADMIN | AI suggestion page; supports `status` and pageable params. |
| `GET` | `/api/admin/ai-suggestions/{suggestionNo}` | inventory-service | ADMIN | AI suggestion detail. |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/reject` | inventory-service | ADMIN | Reject pending suggestion; no stock change. |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft` | inventory-service | ADMIN | Convert suggestion to inbound draft; no stock change. |
| `GET` | `/api/admin/orders` | order-service | ADMIN | Order page; supports order, user, status, product, created time, pageable filters. |
| `GET` | `/api/admin/orders/product-sales` | order-service | ADMIN | Product-sales aggregation from order service. |
| `GET` | `/api/admin/orders/{orderNo}` | order-service | ADMIN | Order detail. |
| `GET` | `/api/admin/orders/{orderNo}/events` | order-service | ADMIN | Order event timeline. |
| `GET` | `/api/admin/operation-stats/sales-by-product` | order-service | ADMIN | Sales-by-product operations stats; supports product/time/page filters. |
| `GET` | `/api/admin/payments` | payment-service | ADMIN | Payment page; supports payment/order/status/paid time/page filters. |
| `GET` | `/api/admin/payments/order/{orderNo}` | payment-service | ADMIN | Payment detail by order number. |
| `GET` | `/api/admin/payments/{paymentNo}` | payment-service | ADMIN | Payment detail by payment number. |
| `GET` | `/api/admin/notifications` | notification-service | ADMIN | Notification page; supports event/order/status/channel/created time/page filters. |
| `GET` | `/api/admin/notifications/{id}` | notification-service | ADMIN | Notification detail. |

### 4.4 Admin AI Inventory Assistant APIs

All endpoints in this section are owned by `inventory-service`, require ADMIN,
and must enter through the gateway under `/api/admin/**`. AI endpoints may read
structured data and create validated suggestions, but must not mutate
`inventory` or `inventory_records`, call `/internal/**`, confirm inbound
orders, or call the manual inventory adjust API.

| Method | Gateway path | Service | Auth | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/api/admin/ai/inventory/ask` | inventory-service | ADMIN | Read-only inventory Q&A. |
| `POST` | `/api/admin/ai/inventory/low-stock-analysis` | inventory-service | ADMIN | AI low-stock analysis; request body carries limits/context. |
| `POST` | `/api/admin/ai/inventory/hot-products-analysis` | inventory-service | ADMIN | AI hot-products analysis; request body carries `days`/limits/context. |
| `POST` | `/api/admin/ai/replenishment-suggestions/generate` | inventory-service | ADMIN | Generate one validated `PENDING_REVIEW` suggestion; no stock change. |
| `GET` | `/api/admin/ai/reports/daily` | inventory-service | ADMIN | Daily inventory AI report. |
| `GET` | `/api/admin/ai/inventory/evidence/current/{productId}` | inventory-service | ADMIN | Read-only current-inventory evidence; supports `recordLimit`. |
| `GET` | `/api/admin/ai/inventory/evidence/low-stock-candidates` | inventory-service | ADMIN | Read-only low-stock evidence; supports `limit` and `recordLimit`. |
| `GET` | `/api/admin/ai/inventory/evidence/low-stock-analysis` | inventory-service | ADMIN | Read-only low-stock sales evidence; supports `limit` and `recordLimit`. |
| `GET` | `/api/admin/ai/inventory/evidence/hot-products` | inventory-service | ADMIN | Read-only hot-products evidence; supports `days`, `limit`, and `recordLimit`. |

AI review and execution deliberately reuse the Phase 2.5 admin APIs:

| Purpose | Endpoint |
| --- | --- |
| Review suggestions | `/api/admin/ai-suggestions/**` |
| Convert suggestion to inbound draft | `POST /api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft` |
| Confirm stock increase | `POST /api/admin/inbound-orders/{inboundNo}/confirm` |

## 5. Non-Browser Contracts

`/internal/**` endpoints are service-to-service only. They are not routed by
the gateway, browser code must not call them, and AI code must not call them.
Examples include:

| Internal path | Owner | Purpose |
| --- | --- | --- |
| `GET /internal/products/{productId}` | product-service | Order-service product lookup. |
| `POST /internal/inventories/deduct` | inventory-service | Order-service inventory deduction. |
| `POST /internal/inventories/release` | inventory-service | Order-service inventory release. |

RabbitMQ events, such as `payment.success`, are also internal contracts and are
not browser-facing APIs.

## 6. Browser Support, Rate Limiting, And Logging

CORS applies to `/api/**` and is configured with
`MINIMALL_GATEWAY_CORS_*` environment variables. Preflight requests are
completed before route, authentication, or rate-limit processing.

The gateway applies Redis-backed token-bucket rate limiting to `/api/**`
requests except preflight requests. Authenticated requests are keyed by trusted
user ID. Public requests fall back to client IP data. Rate-limit denials return
an `ApiResponse` JSON body with `TOO_MANY_REQUESTS` and HTTP 429.

Gateway request logs include method, browser-facing path, status, and duration.
They do not log `Authorization`, `X-User-Id`, `X-Username`, or other request
headers.
