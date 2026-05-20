# API Gateway Contract

Phase 0 establishes the browser-facing gateway contract for MiniMall backend services.

## Routes

The gateway exposes these canonical frontend prefixes and forwards each request
to the matching downstream service while preserving the request path:

| Frontend prefix | Downstream service |
| --- | --- |
| `/api/users/**` | user-service |
| `/api/products/**` | product-service |
| `/api/inventories/**` | inventory-service |
| `/api/orders/**` | order-service |
| `/api/payments/**` | payment-service |

Downstream service base URLs are configured with `USER_SERVICE_BASE_URL`, `PRODUCT_SERVICE_BASE_URL`, `INVENTORY_SERVICE_BASE_URL`, `ORDER_SERVICE_BASE_URL`, and `PAYMENT_SERVICE_BASE_URL`. Local defaults are development-only values.

Legacy service-prefix routes are removed rather than kept as deprecated aliases.
No `/internal/**` route is configured at the gateway.

## Authentication

The gateway authenticates browser-facing `/api/**` requests with an `Authorization: Bearer <jwt>` header. `POST /api/users/login`, `POST /api/users/register`, and CORS preflight `OPTIONS` requests bypass JWT validation.

Incoming browser-supplied `X-User-Id` and `X-Username` headers are always removed. For authenticated requests, the gateway injects trusted `X-User-Id` and `X-Username` values parsed from the JWT before forwarding downstream.

Authentication failures return `ApiResponse` JSON with stable `UNAUTHORIZED` or `FORBIDDEN` codes.

## Browser Support

CORS applies to `/api/**` and is configured with `MINIMALL_GATEWAY_CORS_*` environment variables. Preflight requests are completed before route, authentication, or rate-limit processing.

## Rate Limiting And Logging

The gateway applies Redis-backed token-bucket rate limiting to `/api/**` requests except preflight requests. Authenticated requests are keyed by trusted user ID. Public requests fall back to client IP data.

Rate-limit denials return an `ApiResponse` JSON body with `TOO_MANY_REQUESTS` and HTTP 429.

Gateway request logs include method, browser-facing path, status, and duration. They do not log `Authorization`, `X-User-Id`, `X-Username`, or other request headers.
