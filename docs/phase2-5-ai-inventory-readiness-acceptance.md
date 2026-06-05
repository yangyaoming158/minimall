# Phase 2.5 AI Inventory Readiness Acceptance

- Date: 2026-06-05
- TaskMaster tag: `phase2-5-ai-inventory-readiness`
- Parent task: 8, `Phase 2.5 Acceptance And Regression Documentation`
- Current subtask: 8.2, `Document Phase 1 storefront regression`

This report records the Phase 2.5 readiness gate for Phase 3 AI inventory work.
Subtask 8.1 covers the focused backend inventory boundaries. Subtask 8.2 covers
the Phase 1 storefront regression. Phase 2 admin regression and final package
closeout remain pending for subtask 8.3.

## Verification Commands

| Command | Result |
| --- | --- |
| `mvn -pl inventory-service,api-gateway,common-auth -am test` | Passed. Reactor modules `common-core`, `common-auth`, `api-gateway`, and `inventory-service` all ended with `BUILD SUCCESS`. Test totals: `common-core` 39, `common-auth` 38, `api-gateway` 37, `inventory-service` 100. |
| `mvn -pl user-service,product-service,inventory-service,order-service,payment-service,api-gateway -am test` | Passed. Reactor modules `common-core`, `common-auth`, `api-gateway`, `user-service`, `product-service`, `inventory-service`, `order-service`, and `payment-service` all ended with `BUILD SUCCESS`. Test totals: `common-core` 39, `common-auth` 38, `api-gateway` 37, `user-service` 34, `product-service` 20, `inventory-service` 100, `order-service` 100, `payment-service` 32. |

Notes:

- The gateway test startup logged a Netty network-interface warning inside the
  sandbox, but the command completed successfully.
- H2 duplicate-key errors in repository tests are expected assertion inputs for
  uniqueness and idempotency checks; the Maven result was still successful.

## Phase 2.5 Focused Acceptance

| Area | Status | Evidence |
| --- | --- | --- |
| Inventory record traceability fields | Pass | `AdminInventoryControllerTest.recordsReturnTimelineNewestFirst` verifies admin record responses expose `requestId`, `changeType`, `sourceType`, `quantity`, `reason`, `adminUserId`, `adminUsername`, `referenceNo`, and `status`. `AdminInboundOrderControllerTest.confirmDraftAppliesMultiItemInventoryTransactionally` verifies inbound confirmation records use `sourceType=INBOUND_ORDER`, preserve `requestId`, link `referenceNo` to `inboundNo`, and keep operator fields. |
| Low-stock query works | Pass | `AdminInventoryControllerTest.adminListFiltersLowStock` and `lowStockReturnsBackendComputedCandidates` verify backend-computed low-stock filtering through `/api/admin/inventories` and `/api/admin/inventories/low-stock`. `updateSafetyStockUpdatesThresholdAndWritesAudit` verifies threshold updates are reflected in low-stock state. |
| Operation statistics are read-only | Pass | `AdminInventoryControllerTest.inventoryTrendsReturnsDailyBucketsAndDoesNotMutateInventory` verifies `/api/admin/operation-stats/inventory-trends` returns daily buckets, hides persistence ids, does not change `inventory_records`, does not change inventory stock, and does not write audit logs. |
| Inbound draft creation does not change inventory | Pass | `AdminInboundOrderControllerTest.createMultiItemDraftDoesNotMutateInventory` verifies draft creation writes only inbound draft data and audit evidence while preserving inventory row count, stock values, and inventory record count. |
| Inbound confirmation increases inventory with records and audit logs | Pass | `AdminInboundOrderControllerTest.confirmDraftAppliesMultiItemInventoryTransactionally` verifies confirmation moves an inbound order to `APPLIED`, increases affected inventory rows, writes one inventory record per item, preserves the request id and inbound reference, and writes one `INBOUND_ORDER_CONFIRM` audit log. |
| Reconfirmation is idempotent | Pass | `AdminInboundOrderControllerTest.confirmDuplicateRequestIdReturnsPriorResultAndRepeatedConfirmIsNoop` verifies repeat confirmation returns the prior applied result, does not double-increase stock, writes only one inventory record, and writes only one audit log. |
| Failed confirmation rolls back state and stock | Pass | `AdminInboundOrderControllerTest.confirmRollsBackStateAndStockWhenAnyItemFails` verifies a missing inventory item leaves the inbound order in `DRAFT`, leaves inventory unchanged, and does not write inventory records. |
| AI suggestions cannot directly change inventory | Pass | `AdminAiSuggestionControllerTest.rejectPendingSuggestionCapturesReasonAndDoesNotMutateInventory` verifies rejection changes only suggestion review state and audit evidence while preserving inventory and inventory records. `convertPendingSuggestionCreatesInboundDraftAndDoesNotMutateInventory` verifies conversion creates an inbound draft with `source=AI_SUGGESTION`, but does not change inventory or write inventory records. |
| AI suggestion repeat operations are safe | Pass | `AdminAiSuggestionControllerTest.rejectAlreadyRejectedSuggestionIsRepeatSafe` and `convertAlreadyConvertedSuggestionIsRepeatSafe` verify repeated suggestion review operations return existing state without duplicate audit writes or duplicate inbound drafts. |
| `/internal/**` remains unavailable to browser and AI clients | Pass | `GatewayIntegrationRegressionTest.internalPathsReturnForbiddenApiResponseBeforeRoutingOrRateLimiting` verifies gateway requests to `/internal/products/SKU-1` and `/internal/inventories/deduct` return forbidden `ApiResponse` before routing or rate limiting. `InternalAuthFilterTest` verifies downstream `/internal/**` access requires the internal token. |
| Trusted headers remain protected | Pass | `GatewayIntegrationRegressionTest.routesFrontendServicePrefixesAndPropagatesTrustedUserHeaders` and `routesAdminPrefixesToOwnersWithTrustedAdminRoleHeaders` verify forged `X-User-Id`, `X-Username`, and role headers are stripped and replaced with gateway-authenticated identity. |

## Phase 3 Boundary Conclusion For 8.1

The focused Phase 2.5 backend checks pass. Current backend behavior supports
Phase 3 AI inventory planning only as an advisory workflow:

- Structured inventory and low-stock data can be read through admin APIs.
- Operation statistics are read-only.
- AI suggestion review and conversion cannot directly mutate inventory.
- Inbound drafts cannot mutate inventory.
- Only inbound confirmation applies stock changes, with inventory records and
  admin audit traceability.
- Repeated confirmation does not double-apply stock.
- Browser and future AI clients cannot use `/internal/**` or forged trusted
  headers to bypass the gateway trust boundary.

## Phase 1 Storefront Regression

| Area | Status | Evidence |
| --- | --- | --- |
| Register still works | Pass | `UserAuthControllerTest.registerCreatesUserWithHashedPassword` verifies `POST /api/users/register` returns `ApiResponse`, creates a user, hides internal ids, preserves the public user id contract, and stores a BCrypt hash instead of the raw password. |
| Login still works | Pass | `UserAuthControllerTest.loginReturnsToken` verifies `POST /api/users/login` returns a bearer token, public user fields, and a stable `USER` role token. `missingUserAndWrongPasswordReturnSameUnauthorizedResponse` keeps the frontend-safe unauthorized response shape. |
| Public product browsing and detail still work | Pass | `ProductControllerTest.updateDetailAndListReturnPersistedFields` verifies public `GET /api/products` paging/filtering and `GET /api/products/{productId}` detail return stable `ApiResponse`/paged fields without internal ids. |
| Public inventory detail still works | Pass | `InventoryControllerTest.detailReturnsStableApiResponseForAvailableInventory`, `detailReturnsOutOfStockWhenActiveInventoryHasNoAvailableStock`, and `detailReturnsInactiveWhenInventoryIsInactive` verify product-detail inventory display states. |
| Gateway storefront routes still work | Pass | `GatewayIntegrationRegressionTest.routesFrontendServicePrefixesAndPropagatesTrustedUserHeaders` verifies gateway routes for `/api/users/login`, `/api/users/register`, `/api/products`, `/api/inventories/{productId}`, `/api/orders/my`, and `/api/payments/{orderNo}`. It also verifies spoofed trusted headers are stripped. |
| Order creation still works | Pass | `OrderControllerTest.createOrderValidatesProductDeductsInventoryAndPersistsPendingOrder` verifies `POST /api/orders` validates product state, deducts inventory through the internal client, returns `PENDING_PAYMENT`, computes totals, and persists the pending order. |
| Order creation retry remains idempotent | Pass | `OrderControllerTest.createOrderReplayReturnsExistingOrderWithoutDeductingAgain` verifies replaying the same idempotency key returns the existing order and does not repeat product validation or inventory deduction. |
| My orders and order detail still work | Pass | `OrderControllerTest.myOrdersReturnsPagedStableDto` and `detailReturnsStableDtoWithoutInternalFields` verify customer order list/detail responses remain stable and hide internal fields. |
| Mock payment still works | Pass | `PaymentControllerTest.payPendingOrderReturnsApiResponseAndPersistsSuccessPayment` verifies `POST /api/payments/{orderNo}/pay` returns a successful payment DTO, persists one `SUCCESS` payment, and publishes one `PaymentSuccessEvent`. `queryPaymentByOrderNoReturnsStableDto` verifies payment detail lookup. |
| Payment replay remains safe | Pass | `PaymentControllerTest.payReplayReturnsAlreadySuccessWithoutCreatingAnotherRecordOrRepublishingEvent` and `payExistingPendingPaymentMarksSuccessAndPublishesOneEvent` verify repeat payment behavior does not create duplicate records or duplicate success events. |
| Paid-order transition still works | Pass | `PaymentSuccessEventConsumerTest.pendingPaymentOrderTransitionsToPaidAndRecordsProcessedEvent` verifies the order-service consumer handles `PaymentSuccessEvent`, transitions the order from `PENDING_PAYMENT` to `PAID`, sets `paidAt`, and records the processed event. `duplicateEventIdDoesNotProcessAgain` verifies duplicate payment events are idempotent. |
| Customer auth boundaries still work | Pass | `OrderControllerTest.createOrderMissingAuthenticationReturnsUnauthorized`, `PaymentControllerTest.payMissingAuthenticationReturnsUnauthorized`, `OrderControllerTest.orderOwnedByAnotherUserReturnsNotFound`, and `PaymentControllerTest.payAnotherUsersOrderReturnsNotFound` verify unauthenticated access and cross-user reads/payments remain blocked with frontend-safe responses. |

## Pending Regression Sections

| Section | Status | Owner subtask |
| --- | --- | --- |
| Phase 2 admin login/product/inventory/audit basics | Pending | 8.3 |
| Final Maven package check and readiness closeout | Pending | 8.3 |
