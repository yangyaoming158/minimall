# Claude Code Instructions for MiniMall

## Current phase

We are working on Phase 2: Admin Platform.

Phase 2 PRD:
.taskmaster/docs/phase2-admin-platform-prd.txt

Locked API contract:
docs/phase2-admin-api-contract.md

TaskMaster tag:
phase2-admin-platform

Phase 1 (customer frontend) is complete and must keep working. Its instructions
are preserved in git history; do not regress the Phase 1 customer purchase flow.

## Mandatory workflow

- TaskMaster is the source of truth.
- Always use tag `phase2-admin-platform` for Phase 2 work.
- Before coding, read:
  - README.md
  - docs/phase2-admin-api-contract.md
  - docs/api-gateway-contract.md
  - .taskmaster/docs/phase2-admin-platform-prd.txt
- Run:
  - task-master list --tag=phase2-admin-platform
  - task-master next --tag=phase2-admin-platform
  - task-master show <task-id> --tag=phase2-admin-platform
- Implement only one task or subtask at a time.
- Plan before editing.
- Do not implement future tasks early.
- Phase 2 adds admin-safe backend APIs; do not change completed Phase 1 customer
  flow semantics while doing so.

## Scope boundaries

Phase 2 builds a lightweight admin platform.

Allowed:
- Simple role model (`users.role = USER | ADMIN`, enum-backed)
- Admin-safe backend APIs under `/api/admin/**`
- Gateway and downstream ADMIN enforcement
- Product management including `imageUrl`
- Inventory initialize / adjust / records with idempotency
- Admin query APIs for orders, payments, notifications, audit logs
- Basic admin operation audit logging
- A separate, independent `admin-frontend` project

Forbidden:
- Full enterprise RBAC, menu/button/tenant/resource permissions
- Multi-tenancy or organizations
- File upload / object storage
- Real payment, refund, reconciliation, payment callbacks
- Notification resend
- Product delete
- Admin order status mutation
- Kubernetes, CI/CD, production deployment
- Micro-frontend / shared frontend package extraction

## API rules

- Browsers (customer and admin) must call api-gateway only.
- Canonical customer paths stay stable:
  - /api/users/**
  - /api/products/**
  - /api/inventories/**
  - /api/orders/**
  - /api/payments/**
- Admin paths route through the gateway to their owners:
  - /api/admin/login, /api/admin/me, /api/admin/audit-logs/** → user-service
  - /api/admin/products/** → product-service
  - /api/admin/inventories/** → inventory-service
  - /api/admin/orders/** → order-service
  - /api/admin/payments/** → payment-service
  - /api/admin/notifications/** → notification-service
- Do not call service ports directly.
- Do not call /internal/**.
- Do not send X-User-Id, X-Username, or X-User-Role; the gateway injects trusted
  values after JWT validation and strips browser-forged ones.
- Downstream services must still verify ADMIN on admin APIs.
- All REST APIs return `ApiResponse`; admin list endpoints use `PageResponse<T>`.
- Status-like fields must be enum-backed and serialize as stable enum names.
- Business failures raise `BusinessException`; reuse existing `ErrorCode` values
  (40100 unauthenticated, 40300 forbidden, 40400 not found, 40900 conflict, ...).
- Inventory/admin write paths must be idempotent where required (e.g. requestId).
- Handle 401 by clearing token and redirecting to /login.
- Handle 429 with a friendly rate-limit message.

## Frontend boundaries

There are two independent frontends.

Customer frontend (`frontend`):
- Stays the Phase 1 storefront.
- Must not register admin routes, menus, login, or admin API clients.
- Only Phase 2 UI regression: show `product.imageUrl` with placeholder fallback.

Admin frontend (`admin-frontend`):
- Its own package.json, Vite config, router, layout, API client, store, README,
  build command.
- Routes: /login, / (redirects to /products), /products, /inventories, /orders,
  /payments, /notifications, /audit-logs.
- Protected routes call /api/admin/me to restore/check admin identity.
- Must only call the gateway; no service ports, no /internal/**, no spoofed
  trusted headers.

## UI guidance

- Customer storefront stays clean and modern, not an admin dashboard.
- Admin frontend is an operations console: clear tables, filters, status tags,
  loading/empty/error states.
- UI design may be improved freely within PRD scope.
- Do not add business features outside the PRD.
- PC first, basic responsive support.

## Verification

Before marking a task done:
- Run the smallest relevant check for the changed scope.
- For frontend tasks, run npm run build when applicable.
- HARD RULE: if any backend file changed, also run the full-reactor
  `mvn clean package -DskipTests` before committing — a single-module test pass
  is not sufficient.
- When frontend files changed, run the gateway-contract audit.
- Record changed files, commands, results, and remaining risks in docs/dev-log.md.
- Each git commit needs a fresh per-commit authorization right before it.
