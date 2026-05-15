# API Contract Review

## Scope

This document tracks the remaining API contract issues after the P1 DTO naming cleanup.

## Resolved in current cleanup

| Area | Before | After |
| --- | --- | --- |
| User DTO | `UserResponse.id` | `UserResponse.userId` |
| Product DTO | `ProductResponse.id` and `ProductResponse.productId` | `ProductResponse.productId` only |

## Resolved in Task 16

| Area | Result |
| --- | --- |
| Gateway frontend entry point | `/api/user/**`, `/api/product/**`, `/api/inventory/**`, `/api/order/**`, and `/api/payment/**` are configured in api-gateway with environment-driven downstream URLs. |
| Gateway authentication | Browser-facing `/api/**` requests require JWT except login, register, and preflight requests; trusted user headers are injected by the gateway. |
| Gateway browser support | CORS, `ApiResponse` gateway errors, request logging, and Redis-backed rate limiting are implemented and covered by gateway regression tests. |
| Internal path exposure | No `/internal/**` route is configured at the gateway. |

See `docs/api-gateway-contract.md` for the stable gateway contract.

## Remaining issues

| Priority | Issue | Recommended follow-up |
| --- | --- | --- |
| P1 | Resource route naming must stay plural inside each gateway service namespace. Current implemented controllers use `/api/users` and `/api/products`; future services should use `/api/orders`, `/api/payments`, and `/api/inventories` after gateway rewrite. | Avoid adding downstream resource aliases such as `/api/user`, `/api/product`, `/api/order`, `/api/payment`, or `/api/inventory`; keep the Task 16 gateway namespaces documented in `docs/api-gateway-contract.md`. |
| P1 | Final API documentation must choose one product status mutation style. Current implementation uses `/api/products/{productId}/on-shelf` and `/api/products/{productId}/off-shelf`. | Keep these paths or deliberately replace them with one stable status update endpoint before frontend integration. |
| P2 | `PageResponse` currently lives in product-service only. | Leave as-is for now; migrate to `common-core` only when multiple services need the same pagination wrapper. |

## Current DTO exposure rule

Frontend-facing response DTOs should expose stable business identifiers such as `userId`, `productId`, `orderNo`, and `paymentNo`. They should not expose a naked `id` field unless the API is explicitly internal or admin-only and the field is documented.
