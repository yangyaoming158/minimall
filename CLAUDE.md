# Claude Code Instructions for MiniMall

## Current phase

We are working on Phase 1: Customer Frontend.

Phase 1 PRD:
.taskmaster/docs/phase1-customer-frontend-prd.txt

TaskMaster tag:
phase1-customer-frontend

## Mandatory workflow

- TaskMaster is the source of truth.
- Always use tag `phase1-customer-frontend` for Phase 1 work.
- Before coding, read:
  - README.md
  - docs/frontend-integration.md
  - docs/api-gateway-contract.md
  - docs/phase0-acceptance.md
  - .taskmaster/docs/phase1-customer-frontend-prd.txt
- Run:
  - task-master list --tag=phase1-customer-frontend
  - task-master next --tag=phase1-customer-frontend
  - task-master show <task-id> --tag=phase1-customer-frontend
- Implement only one task or subtask at a time.
- Plan before editing.
- Do not implement future tasks early.
- Do not modify backend business logic unless explicitly requested.

## Frontend boundaries

Phase 1 is customer frontend only.

Allowed:
- Login
- Register
- Product list
- Product detail
- Inventory display
- Checkout
- My orders
- Order detail
- Cancel order
- Mock payment
- Payment result

Forbidden:
- Admin console
- RBAC
- Shopping cart
- Address management
- Coupons
- Real payment
- Refund
- Reconciliation
- Backend lifecycle expansion

## API rules

- Frontend must call api-gateway only.
- Use canonical API paths only:
  - /api/users/**
  - /api/products/**
  - /api/inventories/**
  - /api/orders/**
  - /api/payments/**
- Do not call service ports directly.
- Do not call /internal/**.
- Do not send X-User-Id or X-Username.
- Handle ApiResponse centrally in the API client.
- Handle 401 by clearing token and redirecting to /login.
- Handle 429 with a friendly rate-limit message.

## UI guidance

- Build a clean modern customer storefront, not an admin dashboard.
- UI design may be improved freely within the PRD scope.
- Do not add business features outside the PRD.
- PC first, basic responsive support.
- Use clear product cards, status tags, loading/empty/error states.

## Verification

Before marking a task done:
- Run the smallest relevant check.
- For frontend tasks, run npm run build when applicable.
- Record changed files, commands, results, and remaining risks.
