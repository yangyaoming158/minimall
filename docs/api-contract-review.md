# API Contract Review

## Scope

This document tracks the remaining API contract issues after the P1 DTO naming cleanup.

## Phase 0 canonical path decision

Task 1 in the `phase0-api-polish` tree selected canonical browser paths as the target API contract before gateway implementation:

| Area | Canonical browser prefix | Current legacy gateway prefix |
| --- | --- | --- |
| Users | `/api/users/**` | `/api/user/users/**` |
| Products | `/api/products/**` | `/api/product/products/**` |
| Inventories | `/api/inventories/**` | `/api/inventory/inventories/**` |
| Orders | `/api/orders/**` | `/api/order/orders/**` |
| Payments | `/api/payments/**` | `/api/payment/payments/**` |

Legacy strategy: remove the current legacy gateway paths rather than keep deprecated aliases. See `docs/phase0-api-contract-scope.md` for the full route table, audited files, and internal API boundary.

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

## Resolved in Phase 0 Task 4.2

| Area | Result |
| --- | --- |
| Pagination response contract | Product and order paginated APIs now use `common-core` `PageResponse` with stable JSON fields `content`, `page`, `size`, `totalElements`, and `totalPages`. |

## Resolved in Phase 0 Task 4.3

| Area | Result |
| --- | --- |
| User public DTO contract | Register, login, and current-user responses expose `userId`, avoid naked `id`, and keep user status as enum-string output where present. |
| Product public DTO contract | Product public responses expose `productId`, avoid naked `id`, and keep `status` as enum-string output. |
| Inventory public DTO contract | Inventory public responses expose `productId`, avoid naked `id`, and keep `stockState` as enum-string output. |

## Remaining issues

| Priority | Issue | Recommended follow-up |
| --- | --- | --- |
| P1 | Order and payment public DTO contracts still need the final Phase 0 pass. | Complete Task 4.4 using `docs/phase0-dto-pagination-audit.md` as the source for order/payment identifier exposure, enum status assertions, and focused controller tests. |
| P1 | Final API documentation must choose one product status mutation style. Current implementation uses `/api/products/{productId}/on-shelf` and `/api/products/{productId}/off-shelf`. | Keep these paths or deliberately replace them with one stable status update endpoint before frontend integration. |

## Current DTO exposure rule

Frontend-facing response DTOs should expose stable business identifiers such as `userId`, `productId`, `orderNo`, and `paymentNo`. They should not expose a naked `id` field unless the API is explicitly internal or admin-only and the field is documented.
