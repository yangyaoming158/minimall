# Claude Code Instructions for MiniMall

## Current state

All six phases are COMPLETE: master backend MVP, phase0-api-polish,
phase1-customer-frontend, phase2-admin-platform,
phase2-5-ai-inventory-readiness, and phase3-ai-inventory-assistant
(14/14 tasks; acceptance: docs/phase3-acceptance.md, 2026-06-11; real-provider
smoke fixes: docs/ai-assistant-smoke-findings-2026-06-12.md).

There is NO active phase. The project is in maintenance mode: new features
need a new PRD or explicit user direction. Nothing may regress:

- Phase 1 customer purchase flow (`frontend`).
- Phase 2 admin platform (`admin-frontend` + admin APIs).
- Phase 2.5 stock-mutation foundation (inbound orders, suggestion review
  lifecycle) — do not reopen.
- Phase 3 AI assistant and its locked contract.

Key references:

- Current API endpoint index (authoritative for browser-facing paths):
  docs/api-gateway-contract.md
- Locked AI API contract (authoritative for AI boundaries):
  docs/phase3-ai-inventory-contract.md
- Architecture & AI review (2026-06-10):
  docs/architecture-ai-review-2026-06-10.md — its P0/P1 roadmap is fully
  done (the README known-limitations section M1–M4 landed 2026-06-12).
- Real-LLM smoke findings and prompt-v2 fixes:
  docs/ai-assistant-smoke-findings-2026-06-12.md
- Phase 3 PRD: .taskmaster/docs/phase3-ai-inventory-assistant-prd.txt
- TaskMaster tag for any Phase 3 follow-up: phase3-ai-inventory-assistant

## Mandatory workflow

- TaskMaster is the source of truth for tracked work. For any new tracked
  work, create or use the appropriate tag; Phase 3 follow-ups stay under
  `phase3-ai-inventory-assistant`.
- Before coding, read: README.md, docs/phase3-ai-inventory-contract.md,
  docs/api-gateway-contract.md, and the relevant acceptance/review docs.
- Useful commands: `task-master list --tag=<tag>`, `task-master next
  --tag=<tag>`, `task-master show <task-id>` (after use-tag; see AGENTS.md
  CLI nuances).
- Implement only one task or subtask at a time. Plan before editing. Do not
  implement future tasks early.
- If a change alters API behavior, update the matching contract document in
  the SAME task — contracts must never drift from the implementation. (This
  happened once: low-stock-analysis was POST in code but GET in the
  contract; it has since been fixed, both now POST. Don't repeat it.)

## Hard scope boundaries (from the locked contract)

Allowed:

- AI endpoints only under `/api/admin/ai/**`, owned by inventory-service,
  returning `ApiResponse`, enforcing ADMIN locally via `AdminAccess`.
- Provider adapters behind the `AiProvider` interface (DeepSeek, MiniMax,
  MOCK); config is env-driven (`AI_PROVIDER`, `AI_MODEL`, `AI_BASE_URL`,
  `AI_API_KEY`, `AI_REQUEST_TIMEOUT_MS`, `AI_TEMPERATURE`,
  `AI_MODEL_STRICT_JSON`, `AI_MOCK_ENABLED`), default MOCK.
- Versioned prompt templates under
  inventory-service/src/main/resources/ai/prompts (currently v2;
  promptVersion + outputSchemaVersion). Prompt changes bump the version.
- Suggestion generation persists ONE validated `ai_operation_suggestion`
  with status `PENDING_REVIEW`, reviewed via the existing Phase 2.5 APIs
  (`/api/admin/ai-suggestions/**`, `/api/admin/inbound-orders/**`).
- Dev/test-only deterministic demo data generator (disabled by default).

Forbidden:

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
- Everything forbidden since Phase 2: enterprise RBAC, multi-tenancy, file
  upload, real payment/refund, notification resend, product delete, admin
  order status mutation, K8s/CI-CD, micro-frontends.

## AI implementation rules

- Every model output must pass `AiModelOutputValidator` before it is
  returned as analysis or persisted as a suggestion. Validation and provider
  failures map to controlled `ApiResponse` errors (`AiProviderException`,
  `AiOutputValidationException` extend `BusinessException`).
- The validator's anti-hallucination checks (productId whitelist, numeric
  facts equal to the input snapshot, suggestedQuantity snapshot-derived cap,
  date whitelist, SQL/internal/stock-claim patterns) are a core feature.
  Never weaken them to make a model output pass; tightening is allowed.
- The only stock-related flow stays:
  analysis → validated suggestion (PENDING_REVIEW) → admin review →
  convert-inbound-draft → inbound DRAFT → admin confirm
  (requestId-idempotent) → inventory transaction + records + audit →
  suggestion APPLIED.
- `MockAiProvider` derives deterministic items from the input snapshot, so
  the MOCK path demos the full loop offline and always passes validation.
- Real providers (DeepSeek/MiniMax) are nondeterministic: controlled
  validation failures or empty results are expected behavior, not bugs —
  the validator doing its job is not a defect. Known real-model issues and
  their prompt-v2 fixes: docs/ai-assistant-smoke-findings-2026-06-12.md.
- docker-compose passes the `AI_*` variables to inventory-service only (not
  the shared anchor — the API key must not leak into other services).
  Defaults keep MOCK. Real providers send inventory/sales snapshots to the
  external vendor (M4); demos default to MOCK.

## Known issues registry (do not "fix" silently)

From docs/architecture-ai-review-2026-06-10.md, now also published in
README "Known Limitations". Address only with explicit user approval —
never as drive-by changes:

- M1: `UserContextFilter` trusts propagation headers when
  `minimall.auth.internal.secret` (`MINIMALL_AUTH_INTERNAL_SECRET`) is
  unset (legacy fallback). Compose makes it mandatory via
  `MINIMALL_INTERNAL_TOKEN`.
- M2: order creation deducts inventory remotely before the local order
  insert (saga gap; orphaned locked stock possible).
- M3: payment-service maps the `orders` table directly instead of calling
  order-service.
- M4: real providers send inventory/sales data to external LLM vendors;
  documented in README, demos default to MOCK.
- lockedStock is never consumed after payment (modeling simplification, by
  design; explain, don't refactor).

(M5, the low-stock-analysis GET/POST contract drift, was fixed: contract
and code are both POST.)

## API rules

- Browsers (customer and admin) must call api-gateway only.
- Canonical customer paths stay stable: /api/users/**, /api/products/**,
  /api/inventories/**, /api/orders/**, /api/payments/**.
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
  validation. (X-Request-Id on inbound confirmation is an idempotency key,
  not an identity header.)
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

Customer frontend (`frontend`, dev port 5173):

- Stays the Phase 1 storefront. No admin or AI routes, menus, or clients.

Admin frontend (`admin-frontend`, dev port 5174):

- Routes: /login, / (→ /products), /products, /inventories, /orders,
  /payments, /notifications, /audit-logs, /ai-inventory, /ai-suggestions,
  /inbound-orders.
- Review actions use the existing `/api/admin/ai-suggestions/**` and
  `/api/admin/inbound-orders/**` APIs; never invent parallel review
  endpoints.
- AI UI rules: never show AI as having executed stock changes; stock changes
  only after inbound confirmation; always display the data time range; show
  structured evidence beside the narrative; distinguish missing data /
  unsupported question / model failure / validation failure.
- Must only call the gateway; no service ports, no /internal/**, no spoofed
  trusted headers.

## UI guidance

- Customer storefront stays clean and modern, not an admin dashboard.
- Admin frontend is an operations console: clear tables, filters, status
  tags, loading/empty/error states.
- UI design may be improved freely within existing scope; no business
  features without a PRD. PC first, basic responsive support.

## Verification

Before marking any change done:

- Run the smallest relevant check for the changed scope.
- For frontend changes, run `npm run build` in the affected frontend.
- HARD RULE: if any backend file changed, also run the full-reactor
  `mvn clean package -DskipTests` before committing — a single-module test
  pass is not sufficient.
- When frontend files changed, run the gateway-contract audit.
- Record changed files, commands, results, and remaining risks in
  docs/dev-log.md.
- Each git commit needs a fresh per-commit authorization right before it.
