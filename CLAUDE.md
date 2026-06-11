# Claude Code Instructions for MiniMall

## Current phase

We are working on Phase 3: AI Inventory Assistant (6/13 tasks done).

- PRD: .taskmaster/docs/phase3-ai-inventory-assistant-prd.txt
- Locked API contract (authoritative for AI boundaries):
  docs/phase3-ai-inventory-contract.md
- TaskMaster tag: phase3-ai-inventory-assistant
- Architecture & AI midterm review (2026-06-10):
  docs/architecture-ai-review-2026-06-10.md — read it before planning new
  work; its P0 list is the agreed priority order.

Current position: Tasks 1–6 and 7.1 are done. Remaining order:
7.2 (hot-product analysis endpoint) → 7.3 (analysis security/gateway
regressions) → 8 (replenishment suggestion generation, persists
ai_operation_suggestion) → 9 (APPLIED status sync) → 10 (admin AI page)
→ 11–13 (P1 report, demo data, final regression).

All previous phases are complete and must keep working: master backend MVP,
phase0-api-polish, phase1-customer-frontend, phase2-admin-platform,
phase2-5-ai-inventory-readiness. Do not regress the Phase 1 customer purchase
flow or the Phase 2 admin platform. Do not reopen the Phase 2.5 stock-mutation
foundation (inbound orders, suggestion review lifecycle).

## Mandatory workflow

- TaskMaster is the source of truth.
- Always use tag `phase3-ai-inventory-assistant` for Phase 3 work.
- Before coding, read:
  - README.md
  - docs/phase3-ai-inventory-contract.md
  - docs/api-gateway-contract.md
  - .taskmaster/docs/phase3-ai-inventory-assistant-prd.txt
  - docs/architecture-ai-review-2026-06-10.md (known issues + priorities)
- Run:
  - task-master list --tag=phase3-ai-inventory-assistant
  - task-master next --tag=phase3-ai-inventory-assistant
  - task-master show <task-id> (after use-tag; see AGENTS.md CLI nuances)
- Implement only one task or subtask at a time.
- Plan before editing.
- Do not implement future tasks early.
- If a task needs a contract change, update
  docs/phase3-ai-inventory-contract.md in that same task — the contract must
  never silently drift from the implementation (this already happened once:
  low-stock-analysis is POST in code but GET in the contract; fix the document
  in the next task that touches that area).

## Phase 3 scope boundaries

Allowed:
- New AI endpoints only under `/api/admin/ai/**`, owned by inventory-service,
  returning `ApiResponse`, enforcing ADMIN locally via `AdminAccess`.
- Provider adapters behind the `AiProvider` interface (DeepSeek, MiniMax,
  MOCK); config is env-driven (`AI_PROVIDER`, `AI_MODEL`, `AI_BASE_URL`,
  `AI_API_KEY`, `AI_REQUEST_TIMEOUT_MS`, `AI_MODEL_STRICT_JSON`,
  `AI_MOCK_ENABLED`), default MOCK.
- Versioned prompt templates under
  inventory-service/src/main/resources/ai/prompts (promptVersion +
  outputSchemaVersion).
- Replenishment suggestion generation that persists ONE validated
  `ai_operation_suggestion` with status `PENDING_REVIEW` (Task 8).
- Reusing the existing Phase 2.5 review/conversion APIs
  (`/api/admin/ai-suggestions/**`, `/api/admin/inbound-orders/**`).
- Admin-frontend `/ai-inventory` page (Task 10) plus the missing
  inbound-order and suggestion review views it needs.
- Dev/test-only deterministic demo data generator (Task 12, disabled by
  default).

Forbidden (from the locked contract — these are hard boundaries):
- AI code directly updating `inventory` or `inventory_records`.
- AI code executing SQL or receiving SQL execution capability.
- AI or browser code calling `/internal/**` or service ports directly.
- AI APIs confirming inbound orders or calling
  `POST /api/admin/inventories/{productId}/adjust`.
- Persisting model output before backend validation passes.
- Duplicate review endpoints under `/api/admin/ai/suggestions/**`.
- Alternative suggestion statuses (only `PENDING_REVIEW`,
  `CONVERTED_TO_DRAFT`, `REJECTED`, `APPLIED`; inbound orders only `DRAFT`,
  `CONFIRMED`, `APPLIED`, `CANCELLED`).
- Autonomous/multi-agent loops, a separate ai-assistant-service, RAG/vector
  stores, scheduled AI jobs.
- Hardcoded API keys, model IDs, provider hostnames, or secrets.
- Everything still forbidden from Phase 2: enterprise RBAC, multi-tenancy,
  file upload, real payment/refund, notification resend, product delete,
  admin order status mutation, K8s/CI-CD, micro-frontends.

## AI implementation rules

- Every model output must pass `AiModelOutputValidator` before it is returned
  as analysis or persisted as a suggestion. Validation failures and provider
  failures map to controlled `ApiResponse` errors (`AiProviderException` and
  `AiOutputValidationException` extend `BusinessException`).
- The validator's anti-hallucination checks (productId whitelist, numeric
  facts equal to the input snapshot, date whitelist, SQL/internal/stock-claim
  patterns) are a core feature. Never weaken them to make a model output pass.
- The only allowed stock-related flow stays:
  analysis → validated suggestion (PENDING_REVIEW) → admin review →
  convert-inbound-draft → inbound DRAFT → admin confirm (requestId-idempotent)
  → inventory transaction + records + audit → suggestion APPLIED.
- `MockAiProvider` derives deterministic items from the input snapshot
  (Task 8.1), so the MOCK path demos suggestion generation end-to-end without
  network access.
- docker-compose passes the `AI_*` variables to inventory-service only (not
  the shared anchor — it is the sole consumer and the API key must not leak
  into other services). Defaults keep MOCK; set the variables in `.env` to
  demo a real provider, remembering that real providers send inventory/sales
  snapshots to the external vendor (M4).

## Known issues registry (do not "fix" silently)

From docs/architecture-ai-review-2026-06-10.md. Address these only via the
review roadmap or with explicit user approval — never as drive-by changes in
unrelated tasks:

- M1: `UserContextFilter` trusts propagation headers when
  `MINIMALL_AUTH_INTERNAL_SECRET` is unset (legacy fallback).
- M2: order creation deducts inventory remotely before the local order insert
  (saga gap; orphaned locked stock possible).
- M3: payment-service maps the `orders` table directly instead of calling
  order-service.
- M4: real providers send inventory/sales data to external LLM vendors —
  README should state this; demos default to MOCK.
- M5: contract drift on low-stock-analysis HTTP method (see workflow section).
- lockedStock is never consumed after payment (modeling simplification, by
  design; explain, don't refactor).

## API rules

- Browsers (customer and admin) must call api-gateway only.
- Canonical customer paths stay stable:
  - /api/users/**, /api/products/**, /api/inventories/**, /api/orders/**,
    /api/payments/**
- Admin paths route through the gateway to their owners:
  - /api/admin/login, /api/admin/me, /api/admin/audit-logs/** → user-service
  - /api/admin/products/** → product-service
  - /api/admin/inventories/**, /api/admin/inbound-orders/**,
    /api/admin/ai-suggestions/**, /api/admin/ai/**,
    /api/admin/operation-stats/inventory-trends → inventory-service
  - /api/admin/orders/**,
    /api/admin/operation-stats/sales-by-product → order-service
  - /api/admin/payments/** → payment-service
  - /api/admin/notifications/** → notification-service
- Do not call service ports directly. Do not call /internal/**.
- Do not send X-User-Id, X-Username, X-User-Role, or X-Internal-Token; the
  gateway strips browser-forged values and injects trusted ones after JWT
  validation.
- Downstream services must still verify ADMIN locally on admin APIs.
- All REST APIs return `ApiResponse`; admin list endpoints use
  `PageResponse<T>`.
- Status-like fields must be enum-backed and serialize as stable enum names.
- Business failures raise `BusinessException`; reuse existing `ErrorCode`
  values (40100 unauthenticated, 40300 forbidden, 40400 not found,
  40900 conflict, ...).
- Inventory/admin write paths must be idempotent where required (requestId,
  unique constraints as backstop).
- Handle 401 by clearing token and redirecting to /login.
- Handle 429 with a friendly rate-limit message.

## Frontend boundaries

Customer frontend (`frontend`):
- Stays the Phase 1 storefront. No admin or AI routes, menus, or clients.

Admin frontend (`admin-frontend`):
- Existing routes: /login, / (→ /products), /products, /inventories, /orders,
  /payments, /notifications, /audit-logs.
- Task 10 adds: /ai-inventory (Q&A, low-stock analysis, hot-product analysis,
  suggestion generation) plus the suggestion review and inbound-order views
  needed to complete the loop. Use the existing
  `/api/admin/ai-suggestions/**` and `/api/admin/inbound-orders/**` APIs;
  do not invent parallel review endpoints.
- AI UI rules: never show AI as having executed stock changes; state that
  stock changes only after inbound confirmation; always display the data time
  range; show structured evidence beside the narrative; distinguish missing
  data / unsupported question / model failure / validation failure.
- Must only call the gateway; no service ports, no /internal/**, no spoofed
  trusted headers.

## UI guidance

- Customer storefront stays clean and modern, not an admin dashboard.
- Admin frontend is an operations console: clear tables, filters, status tags,
  loading/empty/error states.
- UI design may be improved freely within PRD scope; no business features
  outside the PRD. PC first, basic responsive support.

## Verification

Before marking a task done:
- Run the smallest relevant check for the changed scope.
- For frontend tasks, run npm run build when applicable.
- HARD RULE: if any backend file changed, also run the full-reactor
  `mvn clean package -DskipTests` before committing — a single-module test
  pass is not sufficient.
- When frontend files changed, run the gateway-contract audit.
- Record changed files, commands, results, and remaining risks in
  docs/dev-log.md.
- Each git commit needs a fresh per-commit authorization right before it.
