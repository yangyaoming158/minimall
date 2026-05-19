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

## Remaining issues

| Priority | Issue | Recommended follow-up |
| --- | --- | --- |
| P1 | Public DTO and pagination contracts need a final Phase 0 pass after canonical gateway routing. | Use `docs/phase0-dto-pagination-audit.md` as the Task 4 source for pagination ownership, identifier exposure, enum status assertions, and focused controller tests. |
| P1 | Final API documentation must choose one product status mutation style. Current implementation uses `/api/products/{productId}/on-shelf` and `/api/products/{productId}/off-shelf`. | Keep these paths or deliberately replace them with one stable status update endpoint before frontend integration. |
| P2 | `PageResponse` is duplicated in product-service and order-service. | Move the shared pagination wrapper to `common-core` in Phase 0 Task 4.2 and keep the JSON fields `content`, `page`, `size`, `totalElements`, and `totalPages`. |

## Current DTO exposure rule

Frontend-facing response DTOs should expose stable business identifiers such as `userId`, `productId`, `orderNo`, and `paymentNo`. They should not expose a naked `id` field unless the API is explicitly internal or admin-only and the field is documented.
