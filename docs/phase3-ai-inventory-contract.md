# Phase 3 AI Inventory Assistant Contract

- Date: 2026-06-06
- TaskMaster tag: `phase3-ai-inventory-assistant`
- Scope: Task 1, `Lock Phase 3 AI Contract`

This document locks the Phase 3 AI inventory assistant contract before model
integration begins. It builds on the completed Phase 2.5 readiness gate without
reopening the stock-mutation foundation.

Phase 3 adds AI analysis, model-provider integration, suggestion generation, and
admin frontend workflows. The assistant is advisory: it may read structured
backend data and create validated suggestions, but formal inventory changes
remain owned by existing administrator-confirmed inventory workflows.

## 1. Inputs And Authority

This contract is based on:

- `docs/phase2-5-ai-inventory-contract.md`
- `docs/phase2-5-ai-inventory-readiness-acceptance.md`
- `.taskmaster/docs/phase3-ai-inventory-assistant-prd.txt`
- Existing Phase 2.5 code in `inventory-service`, `order-service`,
  `api-gateway`, and `admin-frontend`

If this document conflicts with completed Phase 2.5 backend behavior, the
implemented Phase 2.5 service contracts win and this document must be corrected
before implementation proceeds. If a later Phase 3 task needs a contract change,
that task must update this document explicitly.

## 2. Locked Phase 3 Boundary

AI code, AI-facing APIs, browser clients, prompt templates, and model provider
adapters must not:

1. Directly update `inventory` rows.
2. Directly insert, update, or delete `inventory_records`.
3. Execute SQL or receive SQL execution capability.
4. Call `/internal/**`.
5. Bypass application services or call service ports directly from browser or
   AI-facing code.
6. Send or spoof `X-User-Id`, `X-Username`, `X-User-Role`, or
   `X-Internal-Token`.
7. Confirm inbound orders.
8. Call `POST /api/admin/inventories/{productId}/adjust`.
9. Claim that stock changed before the existing inbound confirmation workflow
   has applied the change.
10. Persist model output as a valid suggestion before backend validation passes.

Every new REST API must return `ApiResponse`. Paginated list responses must use
`PageResponse`. Business errors must use `BusinessException`. Status fields must
use stable enum names.

All AI-facing and browser-facing calls must enter through `api-gateway` under
`/api/admin/**`.

## 3. Allowed Execution Flow

The only allowed P0 stock-related AI flow is:

```text
Admin frontend
  -> api-gateway /api/admin/**
  -> /api/admin/ai/** analysis endpoint
  -> backend-owned structured data facade
  -> configured LLM provider
  -> backend JSON parsing and validation
  -> ai_operation_suggestion PENDING_REVIEW
  -> administrator review
  -> existing convert-inbound-draft API
  -> inbound_order DRAFT
  -> existing administrator inbound confirmation
  -> inventory-service transaction
  -> inventory_records + admin_operation_logs
  -> suggestion APPLIED synchronization
```

Creating an AI analysis response, AI suggestion, or inbound-order draft must not
change inventory. Converting a suggestion into an inbound draft must not change
inventory. Only confirming an inbound order may increase inventory, and that
operation remains an `inventory-service` administrator action.

## 4. Existing Status Model

Phase 3 must use the current enum names from `inventory-service`.

AI suggestion statuses:

| Status | Meaning |
| --- | --- |
| `PENDING_REVIEW` | Backend-validated AI suggestion awaiting admin decision. |
| `CONVERTED_TO_DRAFT` | Admin converted the suggestion into an inbound order draft. |
| `REJECTED` | Admin rejected the suggestion. |
| `APPLIED` | Linked inbound order was formally applied. |

Inbound order statuses:

| Status | Meaning |
| --- | --- |
| `DRAFT` | Reviewable inbound order draft; no inventory change. |
| `CONFIRMED` | Transitional persisted confirmation state used by existing service flow. |
| `APPLIED` | Inventory increase was applied by `inventory-service`. |
| `CANCELLED` | Draft was cancelled before application. |

Do not introduce alternative suggestion statuses such as `DRAFT`, `CONFIRMED`,
or `EXECUTED` in Phase 3.

## 5. Gateway And Trust Boundary

Phase 3 inherits the existing gateway trust model:

| Header | Browser sends? | Gateway behavior | Downstream rule |
| --- | --- | --- | --- |
| `X-User-Id` | Never | Strip inbound value and inject authenticated user id | Trust only with valid `X-Internal-Token` |
| `X-Username` | Never | Strip inbound value and inject authenticated username | Trust only with valid `X-Internal-Token` |
| `X-User-Role` | Never | Strip inbound value and inject authenticated role | Trust only with valid `X-Internal-Token` |
| `X-Internal-Token` | Never | Strip inbound value and inject configured gateway secret | Required for trusted propagation and `/internal/**` |

All new `/api/admin/ai/**` routes must preserve:

1. Unauthenticated requests return HTTP 401 with `ApiResponse`.
2. Valid `USER` tokens return HTTP 403 with `ApiResponse`.
3. Valid `ADMIN` tokens are forwarded with trusted headers.
4. `OPTIONS /api/admin/**` remains CORS preflight.
5. Downstream controllers still enforce ADMIN locally.

For P0, `/api/admin/ai/**` is owned by `inventory-service` and must be routed
there by `api-gateway`.

## 6. Existing APIs To Reuse

Phase 3 must not duplicate these Phase 2.5 APIs or recreate their tables.

Structured read APIs:

| Method | Path | Owner | AI use |
| --- | --- | --- | --- |
| `GET` | `/api/admin/products` | `product-service` | Product candidates and metadata. |
| `GET` | `/api/admin/products/{productId}` | `product-service` | Product detail context. |
| `GET` | `/api/admin/inventories` | `inventory-service` | Current stock list and filters. |
| `GET` | `/api/admin/inventories/{productId}` | `inventory-service` | Current stock state for one product. |
| `GET` | `/api/admin/inventories/low-stock` | `inventory-service` | Backend-computed low-stock candidates. |
| `GET` | `/api/admin/inventories/{productId}/records` | `inventory-service` | Stock movement explanation and traceability. |
| `GET` | `/api/admin/operation-stats/sales-by-product` | `order-service` | Read-only sales aggregation. |
| `GET` | `/api/admin/operation-stats/inventory-trends` | `inventory-service` | Read-only inventory trend aggregation. |
| `GET` | `/api/admin/audit-logs` | `user-service` | Administrative traceability when needed. |
| `GET` | `/api/admin/inbound-orders` | `inventory-service` | Draft and applied inbound order list. |
| `GET` | `/api/admin/inbound-orders/{inboundNo}` | `inventory-service` | Inbound order detail and execution state. |
| `GET` | `/api/admin/ai-suggestions` | `inventory-service` | AI suggestion review list. |
| `GET` | `/api/admin/ai-suggestions/{suggestionNo}` | `inventory-service` | Suggestion detail, evidence, and links. |

Controlled write APIs:

| Method | Path | Owner | Mutation boundary |
| --- | --- | --- | --- |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/reject` | `inventory-service` | Rejects suggestion only; no stock change. |
| `POST` | `/api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft` | `inventory-service` | Creates linked inbound draft only; no stock change. |
| `POST` | `/api/admin/inbound-orders/drafts` | `inventory-service` | Creates draft only; no stock change. |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/cancel` | `inventory-service` | Cancels draft only; no stock change. |
| `POST` | `/api/admin/inbound-orders/{inboundNo}/confirm` | `inventory-service` | Admin-only formal inventory increase. |

The admin frontend must use the existing suggestion review APIs above. P0 must
not add duplicate review endpoints under `/api/admin/ai/suggestions/**`.

## 7. New Phase 3 API Contract

All new P0 endpoints live under `/api/admin/ai/**`, are gateway-routed to
`inventory-service`, enforce ADMIN access, and return `ApiResponse`.

| Method | Path | Owner | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/admin/ai/inventory/ask` | `inventory-service` | Inventory Q&A through whitelisted read tools. |
| `GET` | `/api/admin/ai/inventory/low-stock-analysis?days=7` | `inventory-service` | LLM-assisted low-stock analysis. |
| `GET` | `/api/admin/ai/inventory/hot-products?days=7` | `inventory-service` | LLM-assisted 7-day hot-product analysis. |
| `GET` | `/api/admin/ai/inventory/hot-products?days=30` | `inventory-service` | LLM-assisted 30-day hot-product analysis. |
| `POST` | `/api/admin/ai/replenishment-suggestions/generate` | `inventory-service` | Generate and persist one validated replenishment suggestion. |

P1 endpoints:

| Method | Path | Owner | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/admin/ai/reports/daily` | `inventory-service` | Daily inventory operations report. |
| `GET` | `/api/admin/ai/inventory/slow-moving?days=30` | `inventory-service` | Slow-moving product analysis. |

P0 `POST /api/admin/ai/replenishment-suggestions/generate` may create a valid
`ai_operation_suggestion` with status `PENDING_REVIEW`. It must not create an
inbound draft automatically and must not change inventory.

## 8. Provider Strategy

Phase 3 uses a provider-adapter design:

| Adapter | Role |
| --- | --- |
| `AiProvider` | Internal interface used by inventory-service AI workflows. |
| `DeepSeekAiProvider` | Default external provider adapter. |
| `MiniMaxAiProvider` | Optional external provider adapter. |
| `MockAiProvider` | Unit tests, local development without network, and demo fallback. |

Provider calls must be server-side only. Browser code must never call model
providers directly.

Configuration must come from environment variables or external secret
configuration:

| Setting | Purpose |
| --- | --- |
| `AI_PROVIDER` | Provider selector, including `MOCK`. |
| `AI_MODEL` | Model name. |
| `AI_BASE_URL` | Provider base URL. |
| `AI_API_KEY` | Provider API key. |
| `AI_REQUEST_TIMEOUT_MS` | Provider request timeout. |
| `AI_MODEL_STRICT_JSON` | Whether strict JSON mode is requested. |
| `AI_MOCK_ENABLED` | Local/test mock-provider enablement. |

Do not hardcode API keys, hostnames, model IDs, or provider secrets in source
code. External model names are operational configuration and must be checked
against the provider's current official model list when provider implementation
starts.

Provider failures must map to controlled `ApiResponse` errors through
`BusinessException` or a local exception mapped by global error handling.

## 9. Data Sources And Logical Tools

Phase 3 AI workflows may use only backend-owned structured data. The LLM may
recommend a tool, but backend code decides which tool executes.

Read tools allowed during Q&A and analysis:

| Logical tool | Backing data source | Allowed result |
| --- | --- | --- |
| `getInventory(productId)` | Inventory application service or `/api/admin/inventories/{productId}` equivalent | Read evidence only. |
| `listLowStockProducts()` | Inventory low-stock query | Read evidence only. |
| `getSalesStats(days)` | `/api/admin/operation-stats/sales-by-product` equivalent | Read evidence only. |
| `getHotProducts(days)` | Sales stats joined with inventory evidence | Read evidence only. |
| `getInventoryRecords(productId)` | Inventory records query | Read evidence only. |
| `getOperationSummary(days)` | Sales and inventory trend aggregations | Read evidence only. |

Write actions allowed only from explicit admin-triggered API workflows:

| Logical action | Backing behavior | Limit |
| --- | --- | --- |
| `createSuggestionRecord(payload)` | Validated `ai_operation_suggestion` persistence | Creates reviewable suggestion only. |
| `convertSuggestionToInboundDraft(suggestionNo)` | Existing convert-inbound-draft service/API | Creates inbound draft only. |

Q&A endpoints must never execute write actions. No logical tool may confirm an
inbound order.

## 10. Prompt Constraints

System prompts and task prompts must require the model to:

1. Use only provided JSON data.
2. Avoid inventing product, order, sales, inventory, supplier, or date facts.
3. State limitations when data is missing or insufficient.
4. Avoid claiming inventory changed.
5. Avoid SQL output.
6. Avoid mentioning internal service endpoints or implementation-only headers.
7. Return structured JSON when the backend requests structured output.
8. Keep admin-facing text advisory and review-oriented.

Prompt templates must be stored under backend resources with an explicit
`promptVersion`. Structured output contracts must carry an
`outputSchemaVersion`.

## 11. Output Validation Rules

Backend code must validate model output before returning it as trusted analysis
or persisting it as a suggestion.

Required validation:

1. JSON parses successfully when structured output is expected.
2. `analysisType` maps to an enum.
3. `riskLevel` is one of `LOW`, `MEDIUM`, or `HIGH`.
4. Every `productId` exists in the input snapshot.
5. Product names, sales numbers, stock values, and dates are present in the
   input snapshot before the output may cite them.
6. Numeric fields are non-negative.
7. Replenishment `suggestedQuantity` is a positive integer.
8. Output does not claim inventory has already changed.
9. Output does not contain SQL.
10. Output does not mention `/internal/**`, service ports, trusted headers, or
    provider secrets.

Invalid output returns a controlled `ApiResponse` error and is not saved as a
valid suggestion. P0 should prefer not saving invalid suggestions; if a later
debugging task stores invalid artifacts, they must be marked with explicit
validation failure metadata and must not enter the review queue as valid
suggestions.

Expected structured output shape:

```json
{
  "summary": "string",
  "analysisType": "LOW_STOCK",
  "timeRange": {
    "from": "string",
    "to": "string"
  },
  "items": [
    {
      "productId": "string",
      "productName": "string",
      "availableStock": 0,
      "lockedStock": 0,
      "safetyStock": 0,
      "soldQuantityLast7Days": 0,
      "suggestedQuantity": 1,
      "riskLevel": "LOW",
      "reason": "string"
    }
  ],
  "limitations": ["string"]
}
```

## 12. Admin Frontend Rules

The admin frontend may add an AI inventory assistant page, but browser code
must call only gateway admin APIs.

Frontend rules:

1. Never show AI as having executed inventory changes.
2. State that stock changes occur only after inbound order confirmation.
3. Display the data time range for every analysis result.
4. Show structured evidence beside natural-language analysis.
5. Distinguish missing data, unsupported questions, model failure, and backend
   validation failure.
6. Use existing suggestion history/detail APIs for review and conversion.
7. Never send trusted identity headers.
8. Never call model providers directly.

## 13. Acceptance Checklist

Every Phase 3 implementation task must preserve these checks:

| Area | Required check |
| --- | --- |
| Gateway | `/api/admin/ai/**` routes to `inventory-service`; existing concrete operation-stat routes remain owner-specific. |
| Security | 401 unauthenticated, 403 USER, ADMIN success, forged trusted headers stripped. |
| Downstream security | Owning controllers enforce ADMIN locally. |
| Response shape | All endpoints return `ApiResponse`; lists use `PageResponse`. |
| Errors | Business conflicts, provider failures, and validation failures return controlled errors through `BusinessException` or global handling. |
| Status fields | Suggestion and inbound order statuses use existing enums. |
| AI boundaries | No direct inventory mutation, SQL execution, `/internal/**`, service-port bypass, or inbound confirmation by AI. |
| Data sources | AI analysis uses backend-owned structured data and records explicit time ranges. |
| Provider config | Provider settings and secrets are externalized. |
| Prompt/output | Prompt version and output schema version are tracked; invalid output is rejected. |
| Traceability | Valid suggestions link evidence, prompt/schema metadata, and later inbound draft or applied references. |
| Regression | Phase 1 customer and Phase 2 admin flows remain unchanged. |

Task 1 is complete when this contract can be reviewed against the Phase 2.5
contract, the Phase 2.5 readiness acceptance report, and the Phase 3 PRD with
all AI authority boundaries, reused APIs, new APIs, provider rules, prompt
constraints, data sources, and output validation rules explicit.
