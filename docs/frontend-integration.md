# Frontend Integration Guide

Phase 0 recorded the initial browser-facing backend contract. The current
project has two shipped frontends (`frontend` and `admin-frontend`) and the
same rule still applies: browser code must use `api-gateway` as the only API
entrypoint and must not call individual service ports such as `8101`, `8102`,
`8103`, `8104`, `8105`, or `8106`.

This guide is not the endpoint index. The single current browser/API endpoint
overview is `docs/api-gateway-contract.md`; keep endpoint additions, removals,
and renames there.

Related source documents:

- `docs/api-gateway-contract.md`
- `docs/phase2-admin-api-contract.md`
- `docs/phase3-ai-inventory-contract.md`
- `docs/api-contract-review.md`
- `pressure/README.md`
- `docs/local-infrastructure.md`

## API Base URL

Local frontend development should point to the gateway:

```text
http://127.0.0.1:8080
```

Both shipped Vite frontends use the same environment variable:

```text
VITE_API_BASE_URL=http://127.0.0.1:8080
```

Do not expose backend service variables such as `USER_SERVICE_BASE_URL` or
`SPRING_DATASOURCE_URL` to browser code.

## Canonical Endpoint Index

Use `docs/api-gateway-contract.md` as the only current endpoint overview. It
records gateway prefixes, concrete endpoint paths, route owners, authentication
class, pagination conventions, admin/AI boundaries, and non-browser internal
contracts.

Frontend documentation and code reviews should link to that file instead of
copying endpoint tables into this guide. Legacy service-prefix aliases remain
out of contract, and `/internal/**` remains service-to-service only.

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

Public browser endpoint classes are listed in `docs/api-gateway-contract.md`.
They include user login/register, admin login, public catalog reads, public
stock reads, and CORS preflight.

Authenticated endpoints require:

```text
Authorization: Bearer <jwt>
```

Login returns `data.tokenType` as `Bearer` and `data.token` as the JWT. The
frontend should join them as `<tokenType> <token>` for later requests.

The frontend must not send `X-User-Id`, `X-Username`, `X-User-Role`, or
`X-Internal-Token`. The gateway strips any browser-supplied values and injects
trusted values parsed from the JWT plus the configured internal token before
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

## Frontend Usage Notes

The shipped frontends already wrap API calls in typed modules under
`frontend/src/api/**` and `admin-frontend/src/api/**`. New frontend code should
extend those modules instead of inlining paths inside views.

Customer flows use public catalog reads plus authenticated order/payment calls.
Order creation and payment attempts must use stable idempotency keys for the
same user action.

Admin flows use `/api/admin/**` endpoints with ADMIN JWTs. AI endpoints may
analyze inventory data and generate validated `PENDING_REVIEW` suggestions, but
only administrator confirmation of an inbound order formally changes stock.

## Browser Acceptance Checklist

Before a frontend PR claims backend readiness, verify:

1. Browser configuration uses only the gateway base URL.
2. Browser network traffic contains no requests to service ports `8101` through
   `8106`.
3. CORS preflight succeeds for the frontend origin.
4. Register or login returns `ApiResponse.success` and a JWT.
5. Authenticated requests send `Authorization: Bearer <jwt>`.
6. The browser does not send `X-User-Id`, `X-Username`, `X-User-Role`, or
   `X-Internal-Token`.
7. Product list, product detail, and inventory detail use gateway paths.
8. Order creation uses a unique `idempotencyKey` per checkout attempt.
9. Payment uses a unique `idempotencyKey` per pay attempt and handles
   `40901`, `40902`, and `40903` as state-refresh cases.
10. Admin and AI pages use `/api/admin/**` gateway paths only.
11. All success and error handling reads `success`, `code`, `message`, and
    `data` from the `ApiResponse` envelope.

The k6 gateway script in `pressure/mini-mall-gateway.js` is the backend-side
smoke target for the gateway contract and should track the canonical paths in
`docs/api-gateway-contract.md`.
