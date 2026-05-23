# Phase 1 — Customer Frontend Acceptance

This document is the closing acceptance record for **Phase 1: Customer
Frontend** of MiniMall Order. It captures the surface area that was actually
shipped, the audits that were run, and the manual verification flow used to
sign off the milestone. Scope is intentionally narrow: it does **not** restate
the backend contract (see `docs/api-gateway-contract.md` /
`docs/frontend-integration.md`) and does **not** redefine business behavior.

- Date: 2026-05-23
- Branch: `feature/phase1-customer-frontend`
- TaskMaster tag: `phase1-customer-frontend` (tasks 1–10 = done)
- Stack: Vue 3 + TypeScript + Vite 6 + Element Plus + Pinia + Vue Router 4 + Axios
- Backend dep: api-gateway on `http://localhost:8080` (Docker Compose `services` profile)

## 1. Scope

### In-scope (shipped)

- Register, Login (with `redirect=` round-trip)
- Product list (filter + paginate)
- Product detail with inventory display
- Checkout with idempotent order creation
- My orders list, order detail, cancel order
- Simulated payment flow (channel `MOCK`) + payment result
- Global route guards (`requiresAuth`, `guestOnly`) and HTTP envelope handling

### Explicitly out-of-scope (NOT shipped)

The PRD scope was held to the letter; the following were not introduced in
any form (no UI, no routes, no API client stubs, no state):

| Area | Status |
| --- | --- |
| Admin console | not shipped |
| RBAC / roles | not shipped |
| Shopping cart | not shipped |
| Address management | not shipped |
| Coupons / promotions | not shipped |
| Real payment integration | not shipped (only `MOCK` channel is used) |
| Refund | not shipped |
| Reconciliation | not shipped |
| Backend lifecycle expansion | no backend business logic was modified |

## 2. Pages & Routes

All routes are declared in `frontend/src/router/index.ts`. Guards apply on
every navigation via `router.beforeEach`.

| Path | Name | Component | Guard | Purpose |
| --- | --- | --- | --- | --- |
| `/` | — | redirect → `/products` | — | Root → catalog |
| `/login` | `login` | `LoginView.vue` | `guestOnly` | Login, honors `?redirect=` (safe, single-slash internal paths only) |
| `/register` | `register` | `RegisterView.vue` | `guestOnly` | Account creation (optional email / phone) |
| `/products` | `products` | `ProductListView.vue` | — | Public catalog; `?page=`, `?status=` URL-synced |
| `/products/:productId` | `product-detail` | `ProductDetailView.vue` | — | Public detail + inventory + quantity selector |
| `/checkout` | `checkout` | `CheckoutView.vue` | `requiresAuth` | Idempotent order creation, `?productId=`, `?quantity=` |
| `/orders` | `orders` | `OrdersView.vue` | `requiresAuth` | My orders list, `?page=` URL-synced |
| `/orders/:orderNo` | `order-detail` | `OrderDetailView.vue` | `requiresAuth` | Order detail + cancel |
| `/payments/:orderNo` | `payment` | `PaymentView.vue` | `requiresAuth` | Simulated payment + poll until PAID |
| `/403` | `forbidden` | `ForbiddenView.vue` | — | Reserved (not currently emitted; backend uses authorization-aware 404) |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView.vue` | — | Catch-all 404 |

## 3. State Matrix

Each customer page handles all relevant interaction states with explicit UI.
"—" means the state is not applicable to that page (e.g. a static form has no
"empty" payload).

| View | loading | empty | error | success | disabled | submitting | unauthorized |
| --- | --- | --- | --- | --- | --- | --- | --- |
| LoginView | el-form rule async validate | — | inline `ElMessage` from `ApiError.message` | `ElMessage.success` + `router.push(safeRedirect())` | el-form rules | `submitting` ref | `meta.guestOnly` (logged-in → `/products`) |
| RegisterView | el-form rule async validate | — | inline `ElMessage` from `ApiError.message` | `ElMessage.success` + nav to `/login` | el-form rules | `submitting` ref | `meta.guestOnly` |
| ProductListView | `v-loading` | `el-empty "暂无商品"` | `el-result` + 重试 | responsive card grid | filter + pager disabled while loading | — | public route |
| ProductDetailView | `v-loading` | — | `el-result` 商品不存在 / 加载失败; inline `el-alert` for inventory-only error | two-column detail | `立即下单` `:disabled` + `disabledHint` priority chain | — | `onOrder` → `/login?redirect=<fullPath>` |
| CheckoutView | `v-loading` | — | `el-result` 参数无效 / 商品不存在 / 加载失败; inline `el-alert` for inventory-only and submit errors | single summary card | submit `:disabled` + priority hint | `submitting` ref | `meta.requiresAuth` |
| OrdersView | `v-loading` | `el-empty "暂无订单"` + 去逛逛 CTA | `el-result` + 重试 | card list + pager | per-card `cancellingOrderNo` | `cancellingOrderNo` ref | `meta.requiresAuth` |
| OrderDetailView | `v-loading` | — | `el-result` 订单不存在 / 加载失败 | detail card + items | — | `cancelling` ref | `meta.requiresAuth` |
| PaymentView | `state === 'loading'` | — | `error-not-found` / `error-generic` / `not-payable` | `ready` / `success` / `success-pending` / `already-paid` | ViewState collapses the matrix | `submitting` state | `meta.requiresAuth` |

Status copy / tag color comes from `frontend/src/utils/order-status.ts` and is
shared between OrdersView, OrderDetailView, and PaymentView so the label and
color cannot drift between list and detail.

## 4. Security Audit (frontend)

Run from repo root, against `frontend/src/`:

```bash
grep -rn -E "X-User-Id|X-Username" frontend/src/
grep -rn "/internal/" frontend/src/
grep -rn -E ":(810[1-6])" frontend/src/
```

| Check | Expected | Observed |
| --- | --- | --- |
| `X-User-Id` / `X-Username` header sent from FE | 0 matches | **0** |
| Frontend calls `/internal/**` | 0 matches | **0** |
| Direct service-port URLs (`8101`–`8106`) | 0 matches | **0** |
| `baseURL` source | `import.meta.env.VITE_API_BASE_URL` only | confirmed (`frontend/src/api/http.ts:12`) |
| 401 handling | `clearToken()` + redirect to `/login` | confirmed (`http.ts:54-57`) |
| 429 handling | friendly `ElMessage.warning` | confirmed (`http.ts:61-63`) |
| 403 handling | global `ElMessage.error`; no business path expects 403 today | confirmed; backend uses authorization-aware 404 for cross-user reads |
| Token storage | `localStorage` key `minimall_token` only | confirmed (`utils/token.ts`) |

## 5. API Contract — endpoints actually called

Frontend never calls any URL outside this list. All go through
`VITE_API_BASE_URL` (defaults to `http://localhost:8080`, the api-gateway).

| Endpoint | Caller | Notes |
| --- | --- | --- |
| `POST /api/users/register` | `RegisterView` | optional `email` / `phone` dropped when blank |
| `POST /api/users/login` | `LoginView` | response carries `token` + identity, no extra `/me` round-trip on login |
| `GET /api/users/me` | router guard | only when token survived a refresh but `currentUser` is null |
| `GET /api/products` | `ProductListView` | `?page` (0-based), `?size`, `?status` |
| `GET /api/products/{productId}` | `ProductDetailView`, `CheckoutView` | |
| `GET /api/inventories/{productId}` | `ProductDetailView`, `CheckoutView` | |
| `POST /api/orders` | `CheckoutView` | `{ productId, quantity, idempotencyKey }` |
| `GET /api/orders/my` | `OrdersView` | paginated, `sort=createdAt,desc` so newest orders surface first |
| `GET /api/orders/{orderNo}` | `OrderDetailView`, `PaymentView` (poll) | authorization-aware 404 |
| `POST /api/orders/{orderNo}/cancel` | `OrdersView`, `OrderDetailView` | conflict codes `ORDER_INVALID_STATE` / `ORDER_CANCELLED` → toast + reload |
| `POST /api/payments/{orderNo}/pay` | `PaymentView` | `{ channel: 'MOCK', idempotencyKey }`; `PAYMENT_ALREADY_SUCCESS` → reload as already-paid |
| `GET /api/payments/{orderNo}` | `PaymentView` | tolerant of 404 while order projection lags |

## 6. Idempotency contract

`CheckoutView` and `PaymentView` each generate **one** `crypto.randomUUID()`
in `onMounted`, kept on a `ref`, and reuse it for every retry of the same
in-page attempt. The key is **not** regenerated on failure — retries must
land in the same idempotency window. The key is reset only when the
component remounts (i.e. the user re-enters the route fresh).

Fallback for the rare browser without `crypto.randomUUID`:
``${Date.now().toString(16)}-${Math.random()…}-${Math.random()…}``.

On submit success, `CheckoutView` uses `router.replace` (not `push`) to
the order detail page so the back button cannot return to a stale
checkout view and risk a double-submit.

## 7. Mock payment clarification

The "支付" surface in this phase is a **demo-only simulated channel**.

- `PaymentView` posts `channel: 'MOCK'` to `POST /api/payments/{orderNo}/pay`.
- A persistent `⚠ 模拟支付 … 不会发生真实扣款` banner is shown in every
  state of the payment page so the demo nature is unambiguous to the user.
- After `/pay` returns success, the order-service flips the order to `PAID`
  only after consuming the `PaymentSuccessEvent` from RabbitMQ. The frontend
  polls `GET /api/orders/{orderNo}` up to 5 × 800 ms to hide the async window;
  if the order has not flipped after the polls, the page enters the
  `success-pending` state and offers a manual `再次刷新订单` retry.
- No real payment integration, no refund flow, no reconciliation.

## 8. Verification commands

Build / type-check:

```bash
cd frontend
npm run build      # vue-tsc --noEmit && vite build
npm run type-check # type-check only, fast
```

Backend reachability:

```bash
docker compose --env-file .env.example --profile services ps
curl --noproxy '*' -fsS http://127.0.0.1:8080/actuator/health
```

Frontend dev server (manual smoke):

```bash
cd frontend
cp .env.example .env.local   # only first time, set VITE_API_BASE_URL
npm run dev                  # http://localhost:5173
```

## 9. E2E smoke checklist (manual)

Run from a fresh browser session, no token in localStorage.

1. `/` → redirects to `/products`. Catalog renders 4-up grid, 在售 tag, prices.
2. Click a card → `/products/:id` renders detail + inventory.
3. Click 立即下单 (logged out) → `/login?redirect=/products/...`. Bottom link → `/register?redirect=...`.
4. Register new user (unique username) → success → `/login?redirect=...` preserved.
5. Log in → redirected back to `/products/...` (or `/products` if no redirect).
6. Click 立即下单 (logged in) → `/checkout?productId=...&quantity=...`. Total = price × quantity.
7. Confirm 下单 → success → `/orders/:orderNo`. Status 待支付, items + 支付截止 visible.
8. Open `/orders` → new order at top of list, 待支付 tag, 取消 + 去支付 actions visible.
9. Click 去支付 → `/payments/:orderNo`. Banner ⚠ 模拟支付 visible. Click 确认支付 ¥X.XX.
10. Within ~4 s, status flips to PAID (poll loop). Receipt panel shows paymentNo / MOCK / amount / paidAt.
11. Back to `/orders` → status now 已支付; 查看详情 button only.
12. Try `/orders/some-other-users-order-no` → 订单不存在 (authorization-aware 404).
13. Log out → `/products` still browsable. Visit `/orders` → router guard redirects to `/login?redirect=/orders`.
14. Re-login → guard restores `/orders`.
15. (Optional) DevTools → application → localStorage → confirm only key `minimall_token`.

## 10. Build artifact summary

Production build (`npm run build`) emits lazy chunks per route. As of the
final build before sign-off:

- `ProductListView` ≈ 3.7 kB
- `ProductDetailView` ≈ 5.9 kB
- `CheckoutView` ≈ 6.2 kB
- `OrdersView` ≈ 4.6 kB
- `OrderDetailView` ≈ 5.8 kB
- `PaymentView` ≈ 8.9 kB
- `order-status` util ≈ 0.3 kB

The main bundle carries the Element Plus full import; the existing
> 500 kB warning is unchanged from Phase 0 and is out of scope here.

## 11. Known limits

- The `合计` shown on `CheckoutView` is computed client-side from
  `price × quantity` and is display-only; the backend recomputes the
  authoritative total. A small caption tells the user so.
- Idempotency keys reset on hard reload (new component instance, new key).
  This matches the Task 7 contract; a stricter cross-reload guarantee
  (e.g. sessionStorage keyed by `route.fullPath`) is a future hardening.
- Payment polling-timeout (`success-pending`) is covered by construction
  but was not exercised live end-to-end; the cleanest live-repro is to
  stop `order-service` after submitting payment so the `PaymentSuccessEvent`
  is never consumed.
- Cancel-conflict (`ORDER_INVALID_STATE` / `ORDER_CANCELLED`) was not
  exercised live; the handler shape matches the verified happy path
  (`ElMessage.warning + reload`).
- `/403` is reserved; the backend uses an authorization-aware 404 for
  cross-user reads, so this view is not currently emitted by any flow.

## 12. Reference

- Root deployment + service start-up: `README.md`
- Frontend dev quickstart: `frontend/README.md`
- API gateway routes / auth / CORS / rate limit contract: `docs/api-gateway-contract.md`
- Response envelope + frontend-ready API guide: `docs/frontend-integration.md`
- Per-task implementation log (Tasks 1–10): `docs/dev-log.md`
- Frontend design baseline: `docs/phase1-frontend-design.md`
- Pre-review checklist: `docs/phase1-frontend-prereview.md`
