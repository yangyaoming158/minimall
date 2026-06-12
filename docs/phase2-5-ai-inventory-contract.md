# Phase 2.5 AI Inventory Integration Contract

- Date: 2026-06-01
- TaskMaster tag: `phase2-5-ai-inventory-readiness`
- Scope: Task 1, "Define the AI Inventory Integration Contract"

This document locks the Phase 2.5 boundary before implementation. It extends the
Phase 2 admin API contract without changing the completed admin platform or
starting Phase 3 model integration.

Phase 2.5 prepares structured backend contracts for a later AI inventory
assistant. The assistant remains advisory: it may use backend-provided
structured data to create suggestions or drafts, but confirmed inventory changes
must still be performed by `inventory-service` after an administrator confirms
the operation.

## 1. Inputs And Authority

This contract is based on:

- `docs/phase2-admin-api-contract.md`
- `docs/phase2-admin-acceptance.md`
- `docs/phase2.5-ai-inventory-backlog.md`
- `.taskmaster/docs/phase2-5-ai-inventory-readiness-prd.txt`
- `.taskmaster/docs/phase3-ai-inventory-assistant-prd.txt`

If this document conflicts with the Phase 2 admin API contract on existing
Phase 2 behavior, the Phase 2 contract wins. If it conflicts with a later
approved Phase 3 AI PRD on AI-only behavior, Phase 3 must explicitly update this
contract before implementation.

## 2. Locked Boundary

Phase 2.5 is backend-readiness work. It does not add model calls, prompt
templates, chat UI, AI sidebars, vector search, RAG, autonomous agents, supplier
management, ERP workflows, refunds, shipping, promotions, or expanded order
lifecycle behavior.

All browser-facing and future AI-facing API calls must enter through
`api-gateway` under `/api/admin/**`. No caller may use service ports directly,
and `/internal/**` remains unavailable to browser clients and AI clients.

Every REST API added in this phase must return `ApiResponse`. Paginated list
responses must use `PageResponse`. Business failures must use
`BusinessException`; status fields must use enums serialized as stable enum
names.

## 3. Allowed Flow

The only allowed inventory-assistant execution flow is:

1. Structured data query.
2. Suggestion or inbound-order draft creation.
3. Administrator review and confirmation.
4. `inventory-service` applies the inventory change.
5. `inventory_records` records the stock movement.
6. `admin_operation_logs` records the administrative operation.
7. Phase 3 AI reads the resulting structured state and traceability records.

The flow is intentionally indirect:

```text
Phase 3 AI backend
  -> structured admin APIs
  -> ai_operation_suggestion or inbound_order draft
  -> administrator action
  -> inventory-service transaction
  -> inventory_records + admin_operation_logs
```

Creating an AI suggestion or inbound-order draft must not change inventory.
Converting a suggestion into an inbound-order draft must not change inventory.
Only confirming an inbound order can increase inventory, and that confirmation
belongs to `inventory-service`.

## 4. Forbidden Behavior

AI code, AI-facing APIs, browser clients, and admin frontend code must not:

1. Directly update `inventory` rows.
2. Directly insert or mutate `inventory_records`.
3. Execute SQL.
4. Bypass application services or call service ports directly.
5. Call `/internal/**`.
6. Send or spoof `X-User-Id`, `X-Username`, `X-User-Role`, or
   `X-Internal-Token`.
7. Confirm inbound orders.
8. Call `POST /api/admin/inventories/{productId}/adjust`.
9. Claim that inventory was changed before an administrator confirmation
   actually executes.
10. Persist model output before backend validation proves referenced products,
    quantities, statuses, and risk levels are valid.

The AI assistant may produce recommendations and drafts. It may not execute
formal inventory operations.

## 5. Gateway And Trust Boundary

The existing gateway trust model carries forward:

| Header | Browser sends? | Gateway behavior | Downstream rule |
| --- | --- | --- | --- |
| `X-User-Id` | Never | Strip inbound value, inject authenticated user id | Trust only with valid `X-Internal-Token` |
| `X-Username` | Never | Strip inbound value, inject authenticated username | Trust only with valid `X-Internal-Token` |
| `X-User-Role` | Never | Strip inbound value, inject authenticated role | Trust only with valid `X-Internal-Token` |
| `X-Internal-Token` | Never | Strip inbound value, inject configured gateway secret | Required for trusted propagation and `/internal/**` |

All new `/api/admin/**` routes must keep the same behavior:

1. Unauthenticated requests return HTTP 401 with `ApiResponse`.
2. Valid `USER` tokens return HTTP 403 with `ApiResponse`.
3. Valid `ADMIN` tokens are forwarded with trusted headers.
4. `OPTIONS /api/admin/**` remains CORS preflight.
5. Downstream controllers still call their local ADMIN guard, even when the
   gateway has already checked the role.

## 6. Routing And Ownership Decisions

Phase 2.5 adds three new admin path families. Each family needs gateway routing
and downstream ADMIN enforcement.

| Path family | Downstream owner | Gateway rule | Notes |
| --- | --- | --- | --- |
| `/api/admin/operation-stats/sales-by-product` | `order-service` | Add an exact or concrete Path predicate to the order route | Read-only sales aggregation from orders |
| `/api/admin/operation-stats/inventory-trends` | `inventory-service` | Add an exact or concrete Path predicate to the inventory route | Read-only inventory trend from inventory records |
| `/api/admin/inbound-orders/**` | `inventory-service` | Add this prefix to the inventory route | Draft, cancel, detail, confirm/apply |
| `/api/admin/ai-suggestions/**` | `inventory-service` | Add this prefix to the inventory route | Suggestion review, reject, convert-to-draft |

Decision: do not add an `admin-service` or gateway aggregator in Phase 2.5.
`/api/admin/operation-stats/**` spans `order-service` and `inventory-service`,
so the gateway must not route the whole prefix to one service. Use concrete
subpath predicates instead:

- `sales-by-product` stays in `order-service`.
- `inventory-trends` stays in `inventory-service`.

If a future phase wants a single fan-out owner, it must introduce an explicit
aggregator service or move all operation-stat endpoints to one downstream
owner. That is out of scope for Phase 2.5.

## 7. Data Ownership

| Data or behavior | Owner | Rationale |
| --- | --- | --- |
| `inventory` | `inventory-service` | Existing stock authority |
| `inventory_records` | `inventory-service` | Existing stock movement timeline |
| `inbound_order` | `inventory-service` | Confirmation must apply stock locally |
| `inbound_order_item` | `inventory-service` | Same transaction as inbound confirmation |
| `ai_operation_suggestion` | `inventory-service` | Suggestion conversion links to inbound drafts and inventory records |
| `admin_operation_logs` table/query API | `user-service` | Existing Phase 2 audit ownership |
| Audit writes for inventory operations | Calling service through `AdminAuditWriter` | Existing Phase 2 pattern: write audit at the service performing the admin write |

Inbound orders, the confirm/apply flow, `inventory_records`, and
`ai_operation_suggestion` live together in `inventory-service`. This keeps
admin confirmation as one local transaction and avoids cross-service distributed
transactions.

Confirming an inbound order must:

1. Verify the inbound order is confirmable.
2. Use an idempotency key, preferably `requestId`.
3. Increase the affected inventory rows.
4. Write one or more `inventory_records` linked to `inboundNo`.
5. Write an `admin_operation_logs` record linked to the same business reference.
6. Commit all changes together or roll all of them back.

## 8. Phase 2.5 API Contract

These APIs are the backend contract Phase 2.5 implementation tasks should
converge on. Exact request/response DTO fields can be refined in the owning
task, but the route ownership and mutation boundaries are fixed here.

### 8.1 Existing Structured Inputs

| Method | Path | Owner | AI use |
| --- | --- | --- | --- |
| `GET` | `/api/admin/products` | `product-service` | Product candidates and product metadata |
| `GET` | `/api/admin/products/{productId}` | `product-service` | Product detail for analysis context |
| `GET` | `/api/admin/inventories` | `inventory-service` | Current stock list and filters |
| `GET` | `/api/admin/inventories/{productId}` | `inventory-service` | Current stock state for one product |
| `GET` | `/api/admin/inventories/{productId}/records` | `inventory-service` | Stock movement explanation and traceability |
| `GET` | `/api/admin/orders/product-sales` | `order-service` | Existing Phase 2 product sales aggregation |
| `GET` | `/api/admin/audit-logs` | `user-service` | Administrative traceability when needed |

Phase 3 should prefer the Phase 2.5 operation-stat endpoints once they exist,
but the current Phase 2 sales aggregation remains valid existing context.

### 8.2 Phase 2.5 Structured Inputs

| Method | Path | Owner | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/admin/inventories/low-stock` | `inventory-service` | Low-stock candidates; backend-computed |
| `PATCH` | `/api/admin/inventories/{productId}/safety-stock` | `inventory-service` | Admin threshold update; audit required |
| `GET` | `/api/admin/operation-stats/sales-by-product` | `order-service` | Read-only sales aggregation with time filters |
| `GET` | `/api/admin/operation-stats/inventory-trends` | `inventory-service` | Read-only inbound/outbound/adjustment trend |
| `GET` | `/api/admin/inbound-orders` | `inventory-service` | Draft and applied inbound order list |
| `GET` | `/api/admin/inbound-orders/{inboundNo}` | `inventory-service` | Inbound order detail and execution state |
| `GET` | `/api/admin/ai-suggestions` | `inventory-service` | Suggestion review list |
| `GET` | `/api/admin/ai-suggestions/{suggestionNo}` | `inventory-service` | Suggestion detail, input summary, links |

### 8.3 Phase 2.5 Controlled Write APIs

| Method | Path | Owner | Mutation boundary |
| --- | --- | --- | --- |
| `POST` | `/api/admin/inbound-orders/drafts` | `inventory-service` | Creates a draft only; no stock change |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/cancel` | `inventory-service` | Cancels a draft only; no stock change |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/confirm` | `inventory-service` | Admin-only formal inventory increase |
| `POST` | `/api/admin/ai-suggestions` | `inventory-service` | Stores a backend-validated suggestion only; no stock change |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/reject` | `inventory-service` | Rejects suggestion only; no stock change |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft` | `inventory-service` | Creates linked inbound draft only; no stock change |

Phase 3 AI backend may generate a validated suggestion record, but human
administrators own rejection, conversion, and inbound confirmation decisions.
Even if Phase 3 exposes convenience AI endpoints under `/api/admin/ai/**`, those
endpoints must delegate to the same service boundaries above.

## 9. Phase 3 AI Consumption Rules

Phase 3 AI may consume structured data only through backend-owned logical tools
or admin-safe APIs. Allowed logical tools map to these structured capabilities:

| Logical capability | Backing structured API or service | Allowed result |
| --- | --- | --- |
| Current inventory lookup | `/api/admin/inventories`, `/api/admin/inventories/{productId}` | Read evidence |
| Low-stock candidate lookup | `/api/admin/inventories/low-stock` | Read evidence |
| Inventory movement explanation | `/api/admin/inventories/{productId}/records` | Read evidence |
| Product context lookup | `/api/admin/products`, `/api/admin/products/{productId}` | Read evidence |
| Sales aggregation | `/api/admin/operation-stats/sales-by-product` | Read evidence |
| Inventory trend analysis | `/api/admin/operation-stats/inventory-trends` | Read evidence |
| Suggestion persistence | `ai_operation_suggestion` through validated backend service | Creates reviewable suggestion only |
| Draft creation from suggestion | `/api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft` or service equivalent | Creates inbound draft only |

Phase 3 AI must not receive database credentials, internal service URLs, or raw
SQL execution capability. Model output must be validated by backend code before
it becomes a suggestion or draft.

## 10. Task Outputs And Phase 3 Handoff

| Phase 2.5 task | Output | Phase 3 use |
| --- | --- | --- |
| Task 1 | This integration contract | AI system boundary and route ownership |
| Task 2 | Traceable inventory records | Evidence for explanations and execution history |
| Task 3 | Safety-stock update and low-stock query | Replenishment candidate input |
| Task 4 | Operation statistics APIs | Sales/trend evidence for AI analysis |
| Task 5 | Inbound-order draft model and APIs | Safe target for accepted suggestions |
| Task 6 | Idempotent inbound confirmation | Human-approved formal inventory execution |
| Task 7 | AI suggestion records | Review queue and suggestion lifecycle |
| Task 8 | Acceptance/regression report | Readiness gate for Phase 3 |

Phase 3 starts only after Task 8 proves:

1. Low-stock and operation-stat inputs are available through admin APIs.
2. Suggestion records cannot mutate inventory.
3. Inbound drafts cannot mutate inventory.
4. Inbound confirmation is idempotent and transactional.
5. Inventory records and audit logs link back to business references.
6. `/internal/**` and trusted headers remain protected.
7. Phase 1 customer and Phase 2 admin regressions still pass.

## 11. Acceptance Checklist

Every implementing task after this one must preserve these checks:

| Area | Required check |
| --- | --- |
| Gateway | New paths route to the fixed owner; no single `/api/admin/operation-stats/**` fan-out route |
| Gateway security | 401 unauthenticated, 403 USER, ADMIN success, forged trusted headers stripped |
| Downstream security | Owning controller still enforces ADMIN through local guard |
| Response shape | All endpoints return `ApiResponse`; lists use `PageResponse` |
| Errors | Business conflicts and validation failures use `BusinessException` |
| Status fields | Inbound order and suggestion statuses use enums |
| Idempotency | Confirm/apply paths cannot double-increase inventory |
| Traceability | Inventory records include source, reference, operator, reason, request id, and time where applicable |
| Audit | Admin writes create `admin_operation_logs` entries with business references |
| Non-goals | No model provider calls, prompt templates, chat UI, SQL execution, or direct inventory mutation |

This document is complete when it can be reviewed against the Phase 2 admin API
contract and the Phase 2.5 PRD with all hard boundaries and non-goals explicit.
