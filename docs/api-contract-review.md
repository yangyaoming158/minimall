# API Contract Review

## Scope

This document tracks the remaining API contract issues after the P1 DTO naming cleanup.

## Resolved in current cleanup

| Area | Before | After |
| --- | --- | --- |
| User DTO | `UserResponse.id` | `UserResponse.userId` |
| Product DTO | `ProductResponse.id` and `ProductResponse.productId` | `ProductResponse.productId` only |

## Remaining issues

| Priority | Issue | Recommended follow-up |
| --- | --- | --- |
| P0 | API Gateway has no concrete route configuration yet, so the browser-facing single entry point is not enforced. | Implement Task 16 with `/api/**` gateway routing, JWT/CORS handling, and explicit blocking of `/internal/**`. |
| P0 | Internal API paths are separated by `/internal/**`, but external exposure is not blocked at the gateway yet. | Ensure gateway never routes `/internal/**` from browser traffic. |
| P1 | API route naming must stay plural across future services. Current implemented controllers use `/api/users` and `/api/products`; future services should use `/api/orders`, `/api/payments`, and `/api/inventories`. | Avoid adding singular aliases such as `/api/user`, `/api/product`, `/api/order`, `/api/payment`, or `/api/inventory` for browser-facing APIs. |
| P1 | Final API documentation must choose one product status mutation style. Current implementation uses `/api/products/{productId}/on-shelf` and `/api/products/{productId}/off-shelf`. | Keep these paths or deliberately replace them with one stable status update endpoint before frontend integration. |
| P2 | `PageResponse` currently lives in product-service only. | Leave as-is for now; migrate to `common-core` only when multiple services need the same pagination wrapper. |

## Current DTO exposure rule

Frontend-facing response DTOs should expose stable business identifiers such as `userId`, `productId`, `orderNo`, and `paymentNo`. They should not expose a naked `id` field unless the API is explicitly internal or admin-only and the field is documented.
