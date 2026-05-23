# MiniMall Customer Frontend

Phase 1 customer storefront for MiniMall. A lightweight Vue 3 web frontend that
drives the backend microservice order flow: register → login → browse products →
view inventory → create order → view orders → mock pay → order becomes `PAID`.

## Tech Stack

- Vue 3 + TypeScript
- Vite (dev server / build)
- Element Plus (UI components)
- Pinia (state management)
- Vue Router (routing)
- Axios (HTTP client)

## Prerequisites

- Node.js 20.19+ or 22.12+ (developed on Node 24)
- A running MiniMall `api-gateway` (default `http://localhost:8080`)

## Setup

```bash
cd frontend
npm install
```

## Environment Variables

Copy the template and adjust if needed:

```bash
cp .env.example .env.local
```

| Variable | Purpose | Default |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Base URL of the api-gateway. All requests go through it. | `http://localhost:8080` |

Rules:

- The frontend calls **api-gateway only**, using canonical `/api/**` paths.
- Never point `VITE_API_BASE_URL` at a service port (e.g. 8101–8106).
- The frontend never calls `/internal/**` and never sends `X-User-Id` / `X-Username`.

## Commands

```bash
npm run dev         # start dev server (http://localhost:5173)
npm run build       # type-check (vue-tsc) + production build to dist/
npm run preview     # preview the production build
npm run type-check  # type-check only
```

## Pages & Routes

All routes are declared in `src/router/index.ts`. Guards apply globally via
`router.beforeEach`.

| Path | Name | Guard | Purpose |
| --- | --- | --- | --- |
| `/` | — | — | Redirect to `/products` |
| `/login` | `login` | `guestOnly` | Login; honors a safe internal `?redirect=` |
| `/register` | `register` | `guestOnly` | Account creation |
| `/products` | `products` | — | Public catalog, URL-synced `?page` / `?status` |
| `/products/:productId` | `product-detail` | — | Detail + inventory + quantity selector |
| `/checkout` | `checkout` | `requiresAuth` | Idempotent order creation |
| `/orders` | `orders` | `requiresAuth` | My orders, URL-synced `?page` |
| `/orders/:orderNo` | `order-detail` | `requiresAuth` | Order detail + cancel |
| `/payments/:orderNo` | `payment` | `requiresAuth` | Simulated payment (channel `MOCK`) |
| `/403` | `forbidden` | — | Reserved; not currently emitted (see auth contract below) |
| `/:pathMatch(.*)*` | `not-found` | — | Catch-all 404 |

## API & Auth Contract (frontend side)

- All calls go to `VITE_API_BASE_URL` (the api-gateway) using canonical
  `/api/**` paths. No service ports, no `/internal/**`, no upstream-only
  headers like `X-User-Id` / `X-Username`.
- JWT lives in `localStorage` under the key `minimall_token`; it is attached
  as `Authorization: Bearer …` by the axios request interceptor.
- The response interceptor unwraps `ApiResponse`, raises `ApiError`, and
  globally handles: **401** → clear token + redirect to `/login`,
  **429** → friendly rate-limit toast, **500** → generic server toast.
- Backend uses an **authorization-aware 404** for cross-user reads of
  `/api/orders/{orderNo}` (returns NOT_FOUND for both "missing" and
  "exists but belongs to another user"). The frontend does not branch on
  403 for that endpoint by design.

## Idempotency

`CheckoutView` and `PaymentView` each generate one `crypto.randomUUID()`
in `onMounted` and reuse it across every retry of the same attempt. The
key is **not** regenerated on failure — retries must hit the same
idempotency window. It resets only on component remount (i.e. re-entering
the route). `CheckoutView` navigates with `router.replace` (not `push`)
after success so the back button cannot replay a stale submit.

## Mock payment

The `/payments/:orderNo` page sends `channel: 'MOCK'` and renders a
permanent `⚠ 模拟支付 … 不会发生真实扣款` banner in every state. After the
`/pay` request returns success, the order flips to `PAID` asynchronously
through a RabbitMQ event; the frontend polls `GET /api/orders/{orderNo}`
up to 5 × 800 ms to hide that window, and falls back to a `success-pending`
state with a manual refresh CTA if the order has not flipped yet.

## Backend Dependency

This frontend requires the MiniMall backend running locally. See the repository
root `README.md` for how to start the gateway and services, and
`docs/frontend-integration.md` for the API contract. End-of-phase acceptance
notes live in `docs/phase1-frontend-acceptance.md`.
