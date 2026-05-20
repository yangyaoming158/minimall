# Phase 0 API Contract Scope

Date: 2026-05-19

This document records the Task 1 audit result for the `phase0-api-polish` TaskMaster tag. It locks the canonical browser API path strategy before gateway implementation work starts.

## Decision

Phase 0 will use the canonical resource paths below as the only browser-facing API contract:

| Area | Canonical browser path | Downstream controller path | Current gateway path before Phase 0 |
| --- | --- | --- | --- |
| Users | `/api/users/**` | `/api/users/**` | `/api/user/users/**` |
| Products | `/api/products/**` | `/api/products/**` | `/api/product/products/**` |
| Inventories | `/api/inventories/**` | `/api/inventories/**` | `/api/inventory/inventories/**` |
| Orders | `/api/orders/**` | `/api/orders/**` | `/api/order/orders/**` |
| Payments | `/api/payments/**` | `/api/payments/**` | `/api/payment/payments/**` |

Legacy route strategy: remove the legacy gateway service-prefix paths instead of keeping deprecated aliases. The backend MVP has no committed frontend dependency on the legacy paths, and removing them avoids two public path contracts before frontend work begins.

## Public Customer API

| Capability | Method and canonical path | Notes |
| --- | --- | --- |
| Register | `POST /api/users/register` | Public endpoint, no JWT required. |
| Login | `POST /api/users/login` | Public endpoint, returns Bearer JWT data. |
| Current user | `GET /api/users/me` | Requires gateway JWT auth. |
| Product list | `GET /api/products` | Supports `status`, `page`, and `size`. |
| Product detail | `GET /api/products/{productId}` | Read-only customer product detail. |
| Inventory detail | `GET /api/inventories/{productId}` | Read-only stock snapshot. |
| Create order | `POST /api/orders` | Requires `idempotencyKey`. |
| My orders | `GET /api/orders/my` | Current user's order page. |
| Order detail | `GET /api/orders/{orderNo}` | Current user's order detail. |
| Cancel order | `POST /api/orders/{orderNo}/cancel` | Current user's cancellable order. |
| Pay order | `POST /api/payments/{orderNo}/pay` | Requires payment idempotency. |
| Payment detail | `GET /api/payments/{orderNo}` | Current user's payment detail. |

## Internal Boundary

No `/internal/**` route is part of the browser contract.

Current internal service APIs remain service-to-service only:

| Internal path | Current caller | Browser contract |
| --- | --- | --- |
| `GET /internal/products/{productId}` | order-service | Not exposed by gateway. |
| `POST /internal/inventories/deduct` | order-service | Not exposed by gateway. |
| `POST /internal/inventories/release` | order-service | Not exposed by gateway. |

Task 2 and Task 3 must keep `/internal/**` unrouted by `api-gateway`, and public docs or browser-like scripts must not recommend internal paths.

## Audited Files

Source files reviewed:

- `api-gateway/src/main/resources/application.yml`
- `api-gateway/src/main/java/com/minimall/gateway/security/GatewayAuthenticationFilter.java`
- `user-service/src/main/java/com/minimall/user/web/UserAuthController.java`
- `product-service/src/main/java/com/minimall/product/web/ProductController.java`
- `inventory-service/src/main/java/com/minimall/inventory/web/InventoryController.java`
- `order-service/src/main/java/com/minimall/order/web/OrderController.java`
- `payment-service/src/main/java/com/minimall/payment/web/PaymentController.java`
- `product-service/src/main/java/com/minimall/product/web/InternalProductController.java`
- `inventory-service/src/main/java/com/minimall/inventory/web/InternalInventoryController.java`

Documentation and script files reviewed:

- `.taskmaster/docs/phase0-api-contract-polish-prd.txt`
- `docs/api-gateway-contract.md`
- `docs/api-contract-review.md`
- `docs/frontend-integration.md`
- `pressure/README.md`
- `pressure/mini-mall-gateway.js`

## Follow-up Task Boundaries

Task 2 should implement the canonical gateway routes and public auth bypass paths for `/api/users/register` and `/api/users/login`.

Task 3 should verify protected canonical routes, trusted header stripping/injection, rate-limit errors, CORS preflight, and non-exposure of `/internal/**`.

Task 6 and Task 7 should update documentation and browser-like scripts so recommended examples use only canonical paths.

Task 5 records the product status mutation policy: customer product APIs remain read-only, current product write endpoints are not admin-safe without RBAC, and the future admin direction is `PUT /api/admin/products/{productId}/status`.
