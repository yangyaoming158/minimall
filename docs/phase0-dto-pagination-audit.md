# Phase 0 DTO and Pagination Audit

Date: 2026-05-19

This document records Phase 0 Task 4.1 for the `phase0-api-polish` TaskMaster tag. It audits the current public customer API DTO shape before the code changes in Task 4.2 through Task 4.4.

## Decision Summary

Task 4 should remain split. The DTO and pagination contract work crosses common-core plus user, product, inventory, order, and payment service boundaries.

Pagination decision: move `PageResponse` to `common-core` in Task 4.2. The same response shape is now duplicated in product-service and order-service, so keeping service-local copies would create two public pagination contracts.

Identifier rule: public customer DTOs should use named business identifiers such as `userId`, `productId`, `orderNo`, and `paymentNo`. They should not expose a naked `id` field. Internal service-to-service DTOs may remain separate as long as they are not routed through api-gateway as browser contracts.

Enum rule: public status-like fields should serialize as enum strings. Current controller tests already assert strings such as `ON_SHELF`, `IN_STOCK`, `PENDING_PAYMENT`, and `SUCCESS` in several places.

## Current Public DTO Matrix

| Area | Public paths | Current response DTOs | Current identifier shape | Current test coverage | Task 4 follow-up |
| --- | --- | --- | --- | --- | --- |
| Users | `POST /api/users/register`, `POST /api/users/login`, `GET /api/users/me` | `UserResponse`, `LoginResponse`, `CurrentUserResponse` | Uses `userId`; no public `id` field. `UserResponse` maps the database id to `userId`. | Register and current-user tests assert `userId` and no `id`; login test asserts token, tokenType, and username but not `userId`. | Task 4.3 should add/keep tests for login `userId` and register `status` enum string. |
| Products | `POST /api/products`, `PUT /api/products/{productId}`, `GET /api/products`, `GET /api/products/{productId}`, status mutation paths | `ProductResponse`, service-local `PageResponse<ProductResponse>` | Uses `productId`; no public `id` field. | Product tests assert no `id`, `productId`, enum `status`, and list content. Product pagination currently asserts `totalElements` but not the full page field set. | Task 4.2 should centralize `PageResponse`; Task 4.3 should keep product DTO tests and add full pagination field assertions. |
| Inventories | `GET /api/inventories/{productId}` | `InventoryResponse` | Uses `productId`; no public `id` field. | Inventory tests assert no `id`, `productId`, stock counts, and enum `stockState` strings. | Task 4.3 should preserve this shape and add absence checks only if new inventory fields are introduced. |
| Orders | `POST /api/orders`, `GET /api/orders/my`, `GET /api/orders/{orderNo}`, `POST /api/orders/{orderNo}/cancel` | `CreateOrderResponse`, `OrderSummaryResponse`, `OrderDetailResponse`, `CancelOrderResponse`, service-local `PageResponse<OrderSummaryResponse>` | Uses `orderNo`; item data uses `productId`; no public `id`. Current tests intentionally assert `userId` is absent. | Order tests assert no `id`, no `idempotencyKey`, `orderNo`, `productId` inside items, enum `status`, and full pagination fields for `/api/orders/my`. | Task 4.2 should centralize `PageResponse`; Task 4.4 should decide and implement explicit `userId` exposure for order DTOs to satisfy the Task 4 contract, then update tests that currently assert it is absent. |
| Payments | `POST /api/payments/{orderNo}/pay`, `GET /api/payments/{orderNo}` | `PaymentResponse` | Uses `paymentNo` and `orderNo`; no public `id`. It does not currently expose `userId`; `productId` is not present in payment-service's local order snapshot. | Payment tests assert no `id`, no `idempotencyKey`, `paymentNo`, `orderNo`, enum `status`, enum `channel`, and amount. Tests currently assert `userId` is absent for pay responses. | Task 4.4 should add `userId` from the local payment-service order snapshot if the final public contract requires it. `productId` should stay an order DTO concern unless the payment-service schema is deliberately expanded. |

## Pagination Findings

The public pagination shape is already consistent at the JSON field level:

| Field | Meaning | Current copies |
| --- | --- | --- |
| `content` | Page content array | `product-service` and `order-service` |
| `page` | Zero-based page number | `product-service` and `order-service` |
| `size` | Requested page size | `product-service` and `order-service` |
| `totalElements` | Total matching records | `product-service` and `order-service` |
| `totalPages` | Total pages | `product-service` and `order-service` |

The implementation gap is ownership, not shape. Both `product-service/src/main/java/com/minimall/product/dto/PageResponse.java` and `order-service/src/main/java/com/minimall/order/dto/PageResponse.java` define the same public contract. Task 4.2 should add `com.minimall.common.core.response.PageResponse` and remove the duplicate service-local records.

## Exact Follow-up Boundaries

Task 4.2 should:

- Add the shared `PageResponse` record in `common-core`.
- Update product-service and order-service imports and remove duplicate local `PageResponse` files.
- Add or strengthen focused tests for `content`, `page`, `size`, `totalElements`, and `totalPages` under the `ApiResponse` envelope.

Task 4.3 should:

- Keep user, product, and inventory public responses free of naked `id`.
- Strengthen user login/register assertions for `userId` and enum status output.
- Preserve product `productId` and enum `status` output.
- Preserve inventory `productId` and enum `stockState` output.

Task 4.4 should:

- Update order DTOs and tests for the final `userId` decision required by Task 4.
- Keep order responses free of `id`, `username`, and `idempotencyKey`.
- Keep order item `productId` visible in summary/detail responses.
- Update payment DTOs and tests for `userId` if required by the final public contract.
- Keep payment responses free of `id` and `idempotencyKey`, while exposing `paymentNo`, `orderNo`, enum `status`, and enum `channel`.

## Audited Files

- `user-service/src/main/java/com/minimall/user/dto/UserResponse.java`
- `user-service/src/main/java/com/minimall/user/dto/LoginResponse.java`
- `user-service/src/main/java/com/minimall/user/dto/CurrentUserResponse.java`
- `user-service/src/main/java/com/minimall/user/web/UserAuthController.java`
- `product-service/src/main/java/com/minimall/product/dto/ProductResponse.java`
- `product-service/src/main/java/com/minimall/product/dto/PageResponse.java`
- `product-service/src/main/java/com/minimall/product/web/ProductController.java`
- `inventory-service/src/main/java/com/minimall/inventory/dto/InventoryResponse.java`
- `inventory-service/src/main/java/com/minimall/inventory/web/InventoryController.java`
- `order-service/src/main/java/com/minimall/order/dto/CreateOrderResponse.java`
- `order-service/src/main/java/com/minimall/order/dto/OrderSummaryResponse.java`
- `order-service/src/main/java/com/minimall/order/dto/OrderDetailResponse.java`
- `order-service/src/main/java/com/minimall/order/dto/CancelOrderResponse.java`
- `order-service/src/main/java/com/minimall/order/dto/PageResponse.java`
- `order-service/src/main/java/com/minimall/order/web/OrderController.java`
- `payment-service/src/main/java/com/minimall/payment/dto/PaymentResponse.java`
- `payment-service/src/main/java/com/minimall/payment/web/PaymentController.java`
- `user-service/src/test/java/com/minimall/user/web/UserAuthControllerTest.java`
- `product-service/src/test/java/com/minimall/product/web/ProductControllerTest.java`
- `inventory-service/src/test/java/com/minimall/inventory/web/InventoryControllerTest.java`
- `order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java`
- `payment-service/src/test/java/com/minimall/payment/web/PaymentControllerTest.java`
